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

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.ContainerNetworkRemovalTaskService.ContainerNetworkRemovalTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.CounterSubTaskService;
import com.vmware.admiral.service.common.CounterSubTaskService.CounterSubTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
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

    public static class ContainerNetworkRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerNetworkRemovalTaskState.SubStage> {
        private static final String FIELD_NAME_RESOURCE_LINKS = "resourceLinks";
        private static final String FIELD_NAME_RESOURCE_QUERY_TASK_LINK = "resourceQueryTaskLink";
        private static final String FIELD_NAME_REMOVE_ONLY = "removeOnly";

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
        public List<String> resourceLinks;

        /**
         * whether to actually go and destroy the container network using the adapter or just remove
         * the
         * ContainerNetworkState
         */
        public boolean removeOnly;
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
            queryContainerResources(state);
            break;
        case INSTANCES_REMOVING:
            break;// just patch with the links
        case INSTANCES_REMOVED:
            removeResources(state, null);
            break;
        case REMOVING_RESOURCE_STATES:
            break;
        case COMPLETED:
            complete(state, SubStage.COMPLETED);
            break;
        case ERROR:
            completeWithError(state, SubStage.ERROR);
            break;
        default:
            break;
        }
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            ContainerNetworkRemovalTaskState patchBody,
            ContainerNetworkRemovalTaskState currentState) {
        return false;
    }

    @Override
    protected void validateStateOnStart(ContainerNetworkRemovalTaskState state)
            throws IllegalArgumentException {
        assertNotEmpty(state.resourceLinks, "resourceLinks");
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

    private void queryContainerResources(ContainerNetworkRemovalTaskState state) {
        QueryTask networkQuery = createResourcesQuery(ContainerNetworkState.class,
                state.resourceLinks);
        ServiceDocumentQuery<ContainerNetworkState> query = new ServiceDocumentQuery<>(getHost(),
                ContainerNetworkState.class);
        List<String> containerLinks = new ArrayList<>();
        QueryUtil.addBroadcastOption(networkQuery);
        QueryUtil.addExpandOption(networkQuery);
        query.query(networkQuery, (r) -> {
            if (r.hasException()) {
                failTask("Failure retrieving query results", r.getException());
            } else if (r.hasResult()) {
                containerLinks.add(r.getDocumentSelfLink());
            } else {
                if (containerLinks.isEmpty()) {
                    logWarning("No available resources found to be removed with links: %s",
                            state.resourceLinks);
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
                } else {
                    deleteResourceInstances(state, containerLinks, null);
                }
            }
        });
    }

    private QueryTask createResourcesQuery(Class<? extends ServiceDocument> type,
            List<String> resourceLinks) {
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
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.INSTANCES_REMOVED));
            return;
        }

        if (subTaskLink == null) {
            createDeleteResourceCounterSubTask(state, resourceLinks);
            return;
        }

        try {
            logInfo("Starting delete of %d container resources", resourceLinks.size());
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
                                    } else {
                                        sendContainerNetworkDeleteRequest(networkState,
                                                subTaskLink);
                                    }
                                }));
            }
            ContainerNetworkRemovalTaskState patchBody = createUpdateSubStageTask(state,
                    SubStage.INSTANCES_REMOVING);
            sendSelfPatch(patchBody);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting container instances", e);
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
                state.documentSelfLink,
                TaskStage.STARTED, SubStage.INSTANCES_REMOVED,
                TaskStage.STARTED, SubStage.ERROR);

        CounterSubTaskService.createSubTask(this, subTaskInitState,
                (subTaskLink) -> deleteResourceInstances(state, resourceLinks, subTaskLink));
    }

    private void sendContainerNetworkDeleteRequest(ContainerNetworkState state,
            String subTaskLink) {
        AdapterRequest adapterRequest = new AdapterRequest();
        String selfLink = state.documentSelfLink;
        adapterRequest.resourceReference = UriUtils.buildUri(getHost(), selfLink);
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.create(UriUtils.buildUri(
                getHost(), subTaskLink).toString());
        adapterRequest.operationTypeId = NetworkOperationType.DELETE.id;
        sendRequest(Operation.createPatch(state.adapterManagementReference)
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
                        .setCompletion(
                                (o, e) -> {
                                    if (e != null) {
                                        failTask("Failed retrieving Container Network State: "
                                                + resourceLink, e);
                                        return;
                                    }

                                    ContainerNetworkState cns = o
                                            .getBody(ContainerNetworkState.class);
                                    doDeleteResource(state, subTaskLink, cns);
                                }));
            }
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.REMOVING_RESOURCE_STATES));
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting resources", e);
        }
    }

    private void doDeleteResource(ContainerNetworkRemovalTaskState state, String subTaskLink,
            ContainerNetworkState cns) {

        QueryTask compositeQueryTask = QueryUtil.buildQuery(ContainerNetworkState.class, true);

        QueryUtil.addListValueClause(compositeQueryTask,
                ContainerNetworkState.FIELD_NAME_DESCRIPTION_LINK,
                Arrays.asList(cns.descriptionLink));

        final List<String> resourcesSharingDesc = new ArrayList<String>();
        new ServiceDocumentQuery<ContainerNetworkState>(getHost(), ContainerNetworkState.class)
                .query(compositeQueryTask, (r) -> {
                    if (r.hasException()) {
                        logSevere(
                                "Failed to retrieve container networks, sharing the same container "
                                        + "network description: %s -%s",
                                r.getDocumentSelfLink(), r.getException());
                    } else if (r.hasResult()) {
                        resourcesSharingDesc.add(r.getDocumentSelfLink());
                    } else {
                        AtomicLong skipOperationException = new AtomicLong();

                        Operation delOp = deleteContainerNetwork(cns);

                        // list of operations to execute to release container network resources
                        List<Operation> operations = new ArrayList<>();
                        operations.add(delOp);

                        // delete container network description when deleting all its container
                        // networks
                        if (state.resourceLinks.containsAll(resourcesSharingDesc)) {
                            Operation delDescOp = deleteContainerNetworkDescription(cns);
                            operations.add(delDescOp);
                        }

                        OperationJoin.create(operations).setCompletion((ops, exs) -> {
                            // remove skipped exceptions
                            if (exs != null && skipOperationException.get() != 0) {
                                exs.remove(skipOperationException.get());
                            }
                            // fail the task is there are exceptions in the children operations
                            if (exs != null && !exs.isEmpty()) {
                                failTask("Failed deleting container network resources: "
                                        + Utils.toString(exs), null);
                                return;
                            }

                            // complete the counter task after all remove operations finished
                            // successfully
                            completeSubTasksCounter(subTaskLink, null);
                        }).sendWith(this);
                    }
                });
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

    private Operation deleteContainerNetworkDescription(ContainerNetworkState cns) {
        return Operation
                .createGet(this, cns.descriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning("Failed retrieving ContainerNetworkDescription: "
                                        + cns.descriptionLink, e);
                                return;
                            }

                            ContainerNetworkDescription cnd = o
                                    .getBody(ContainerNetworkDescription.class);

                            sendRequest(Operation
                                    .createDelete(this, cnd.documentSelfLink)
                                    .setBody(new ServiceDocument())
                                    .setCompletion(
                                            (op, ex) -> {
                                                if (ex != null) {
                                                    logWarning(
                                                            "Failed deleting ContainerNetworkDescription: "
                                                                    + cnd.documentSelfLink,
                                                            ex);
                                                    return;
                                                }
                                                logInfo("Deleted ContainerNetworkDescription: "
                                                        + cnd.documentSelfLink);
                                            }));
                        });
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();

        setDocumentTemplateIndexingOptions(template, EnumSet.of(PropertyIndexingOption.STORE_ONLY),
                ContainerNetworkRemovalTaskState.FIELD_NAME_RESOURCE_LINKS,
                ContainerNetworkRemovalTaskState.FIELD_NAME_REMOVE_ONLY);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.SINGLE_ASSIGNMENT),
                ContainerNetworkRemovalTaskState.FIELD_NAME_RESOURCE_LINKS,
                ContainerNetworkRemovalTaskState.FIELD_NAME_REMOVE_ONLY);

        setDocumentTemplateUsageOptions(template, EnumSet.of(PropertyUsageOption.SERVICE_USE));

        template.documentDescription.serializedStateSizeLimit = 128 * 1024; // 128 Kb

        return template;
    }
}
