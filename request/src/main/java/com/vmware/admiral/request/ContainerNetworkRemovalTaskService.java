/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.ContainerNetworkRemovalTaskService.ContainerNetworkRemovalTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.CounterSubTaskService;
import com.vmware.admiral.service.common.CounterSubTaskService.CounterSubTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Task implementing removal of Container Networks.
 */
public class ContainerNetworkRemovalTaskService extends
        AbstractTaskStatefulService<ContainerNetworkRemovalTaskService.ContainerNetworkRemovalTaskState, ContainerNetworkRemovalTaskService.ContainerNetworkRemovalTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_CONTAINER_NETWORK_REMOVAL_TASKS;

    public static final String DISPLAY_NAME = "Container Network Removal";

    public static final String EXTERNAL_INSPECT_ONLY_CUSTOM_PROPERTY = "__externalInspectOnly";

    public static class ContainerNetworkRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerNetworkRemovalTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            INSTANCES_REMOVING,
            INSTANCES_REMOVED,
            REMOVING_RESOURCE_STATES,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(INSTANCES_REMOVING, REMOVING_RESOURCE_STATES));
        }

        /** (Required) The resources on which the given operation will be applied */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

        /**
         * whether to actually go and destroy the container network using the adapter or just remove
         * the ContainerNetworkState
         */
        public boolean removeOnly;

        /**
         * whether to actually remove the container network or just inspect (and refresh) the
         * ContainerNetworkState (i.e. external networks can't be removed when removing an
         * application that uses them)
         */
        public boolean externalInspectOnly;

        /**
         * If this is a cleanup removal task, it will try to delete the network state even if it
         * fails to delete an actual network on a host
         */
        public boolean cleanupRemoval;
    }

    public ContainerNetworkRemovalTaskService() {
        super(ContainerNetworkRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ContainerNetworkRemovalTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            queryContainerNetworkResources(state);
            break;
        case INSTANCES_REMOVING:
            break;// just patch with the links
        case INSTANCES_REMOVED:
            removeResources(state, null);
            break;
        case REMOVING_RESOURCE_STATES:
            break;
        case COMPLETED:
            complete();
            break;
        case ERROR:
            completeWithError();
            break;
        default:
            break;
        }
    }

    @Override
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> state) {
        TaskStatusState statusTask = super.fromTask(state);
        if (SubStage.INSTANCES_REMOVED == state.taskSubStage
                || SubStage.COMPLETED == state.taskSubStage) {
            statusTask.name = NetworkOperationType
                    .extractDisplayName(NetworkOperationType.DELETE.id);
        }
        return statusTask;
    }

    private void queryContainerNetworkResources(ContainerNetworkRemovalTaskState state) {
        QueryTask networkQuery = createResourcesQuery(ContainerNetworkState.class,
                state.resourceLinks);
        ServiceDocumentQuery<ContainerNetworkState> query = new ServiceDocumentQuery<>(getHost(),
                ContainerNetworkState.class);
        List<String> networkLinks = new ArrayList<>();
        QueryUtil.addBroadcastOption(networkQuery);
        QueryUtil.addExpandOption(networkQuery);
        query.query(networkQuery, (r) -> {
            if (r.hasException()) {
                failTask("Failure retrieving query results", r.getException());
            } else if (r.hasResult()) {
                networkLinks.add(r.getDocumentSelfLink());
            } else {
                if (networkLinks.isEmpty()) {
                    logWarning("No available resources found to be removed with links: %s",
                            state.resourceLinks);
                    proceedTo(SubStage.COMPLETED);
                } else {
                    deleteResourceInstances(state, networkLinks, null);
                }
            }
        });
    }

    private QueryTask createResourcesQuery(Class<? extends ServiceDocument> type,
            Collection<String> resourceLinks) {
        QueryTask query = QueryUtil.buildQuery(type, false);
        QueryUtil.addListValueClause(query, ServiceDocument.FIELD_NAME_SELF_LINK, resourceLinks);

        return query;
    }

    private void deleteResourceInstances(ContainerNetworkRemovalTaskState state,
            Collection<String> resourceLinks, String subTaskLink) {

        if (state.removeOnly) {
            logFine("Skipping actual container network removal by the adapter since the removeOnly "
                    + "flag was set: %s", state.documentSelfLink);

            // skip the actual removal of container networks through the adapter
            proceedTo(SubStage.INSTANCES_REMOVED);
            return;
        }

        if (subTaskLink == null) {
            createDeleteResourceCounterSubTask(state, resourceLinks);
            return;
        }

        try {
            logInfo("Starting delete of %d container network resources", resourceLinks.size());
            for (String resourceLink : resourceLinks) {
                sendRequest(Operation
                        .createGet(this, resourceLink)
                        .setCompletion(
                                (o, e) -> {
                                    if (e != null) {
                                        logWarning("Failed retrieving ContainerNetworkState: "
                                                + resourceLink);
                                        completeSubTasksCounter(subTaskLink, e);
                                        return;
                                    }
                                    ContainerNetworkState networkState = o
                                            .getBody(ContainerNetworkState.class);
                                    if (networkState.id == null || networkState.id.isEmpty()) {
                                        logWarning("No ID set for container network state: [%s]  ",
                                                networkState.documentSelfLink);
                                        completeSubTasksCounter(subTaskLink, null);
                                    } else if (networkState.originatingHostLink == null
                                            || networkState.originatingHostLink.isEmpty()) {
                                        logWarning(
                                                "No originatingHostLink set for network state [%s].",
                                                networkState.documentSelfLink);
                                        completeSubTasksCounter(subTaskLink, null);
                                    } else if (ContainerNetworkState.PowerState.RETIRED == networkState.powerState) {
                                        logWarning(
                                                "Network with id '%s' is retired. Deleting the state only.",
                                                networkState.id);
                                        completeSubTasksCounter(subTaskLink, null);
                                    } else {
                                        sendContainerNetworkDeleteRequest(state, networkState,
                                                subTaskLink);
                                    }
                                }));
            }
            proceedTo(SubStage.INSTANCES_REMOVING);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting container network instances", e);
        }
    }

    private void completeSubTasksCounter(String subTaskLink, Throwable ex) {
        CounterSubTaskState body = new CounterSubTaskState();
        body.taskInfo = new TaskState();
        if (ex == null) {
            body.taskInfo.stage = TaskStage.FINISHED;
        } else {
            body.taskInfo.stage = TaskStage.FAILED;
            body.taskInfo.failure = Utils.toServiceErrorResponse(ex);
        }

        sendRequest(Operation.createPatch(this, subTaskLink)
                .setBody(body)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Notifying counting task failed: %s", e);
                    }
                }));
    }

    private void createDeleteResourceCounterSubTask(ContainerNetworkRemovalTaskState state,
            Collection<String> resourceLinks) {
        CounterSubTaskState subTaskInitState = new CounterSubTaskState();
        subTaskInitState.completionsRemaining = resourceLinks.size();
        subTaskInitState.documentExpirationTimeMicros = ServiceUtils
                .getDefaultTaskExpirationTimeInMicros();
        subTaskInitState.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, SubStage.INSTANCES_REMOVED,
                TaskStage.STARTED,
                state.cleanupRemoval ? SubStage.INSTANCES_REMOVED : SubStage.ERROR);

        CounterSubTaskService.createSubTask(this, subTaskInitState,
                (subTaskLink) -> deleteResourceInstances(state, resourceLinks, subTaskLink));
    }

    private void sendContainerNetworkDeleteRequest(ContainerNetworkRemovalTaskState state,
            ContainerNetworkState networkState, String subTaskLink) {

        AdapterRequest adapterRequest = new AdapterRequest();
        String selfLink = networkState.documentSelfLink;
        adapterRequest.resourceReference = UriUtils.buildUri(getHost(), selfLink);
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.create(UriUtils.buildUri(
                getHost(), subTaskLink).toString());
        adapterRequest.operationTypeId = (Boolean.TRUE.equals(networkState.external)
                && state.externalInspectOnly) ? NetworkOperationType.INSPECT.id
                        : NetworkOperationType.DELETE.id;
        sendRequest(Operation.createPatch(getHost(), networkState.adapterManagementReference.toString())
                .setBody(adapterRequest)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("AdapterRequest failed for container network: " + selfLink, e);
                        return;
                    }
                }));
    }

    private void removeResources(ContainerNetworkRemovalTaskState state, String subTaskLink) {
        if (subTaskLink == null) {
            // count 2 * resourceLinks (to keep track of each removal operation starting and ending)
            createCounterSubTask(state, state.resourceLinks.size(),
                    (link) -> removeResources(state, link));
            return;
        }

        try {
            for (String resourceLink : state.resourceLinks) {
                sendRequest(Operation
                        .createGet(this, resourceLink)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                failTask("Failed retrieving Container Network State: "
                                        + resourceLink, e);
                                return;
                            }

                            ContainerNetworkState cns = o.getBody(ContainerNetworkState.class);

                            if (Boolean.TRUE.equals(cns.external) && state.externalInspectOnly) {
                                logFine("Skipping actual Container Network State removal since the "
                                        + "inspectOnly flag was set: %s", state.documentSelfLink);
                                completeSubTasksCounter(subTaskLink, null);
                                return;
                            }

                            deleteContainerNetwork(cns).setCompletion((o2, e2) -> {
                                if (e2 != null) {
                                    failTask("Failed removing Container Network State: "
                                            + resourceLink, e2);
                                    return;
                                }
                                completeSubTasksCounter(subTaskLink, null);
                            }).sendWith(this);
                        }));
            }
            proceedTo(SubStage.REMOVING_RESOURCE_STATES);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting resources", e);
        }
    }

    private Operation deleteContainerNetwork(ContainerNetworkState cns) {
        return Operation
                .createDelete(this, cns.documentSelfLink)
                .setBody(new ServiceDocument())
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning("Failed deleting ContainerNetworkState: "
                                        + cns.documentSelfLink, e);
                                return;
                            }
                            logInfo("Deleted ContainerNetworkState: " + cns.documentSelfLink);
                        });
    }
}
