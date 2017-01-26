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
import com.vmware.admiral.adapter.common.VolumeOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.request.ContainerVolumeRemovalTaskService.ContainerVolumeRemovalTaskState.SubStage;
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
 * Task implementing removal of Container Volumes.
 */
public class ContainerVolumeRemovalTaskService extends
        AbstractTaskStatefulService<ContainerVolumeRemovalTaskService.ContainerVolumeRemovalTaskState, ContainerVolumeRemovalTaskService.ContainerVolumeRemovalTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_CONTAINER_VOLUME_REMOVAL_TASKS;

    public static final String DISPLAY_NAME = "Container Volume Removal";

    public static final String EXTERNAL_INSPECT_ONLY_CUSTOM_PROPERTY = "__externalInspectOnly";

    public static class ContainerVolumeRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerVolumeRemovalTaskState.SubStage> {

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
         * whether to actually go and destroy the container volume using the adapter or just remove
         * the ContainerVolumeState
         */
        public boolean removeOnly;

        /**
         * whether to actually remove the container volume or just inspect (and refresh) the
         * ContainerVolumeState (i.e. external volumes can't be removed when removing an
         * application that uses them)
         */
        public boolean externalInspectOnly;

        /**
         * If this is a cleanup removal task, it will try to delete the volume state even if it
         * fails to delete an actual volume on a host
         */
        public boolean cleanupRemoval;
    }

    public ContainerVolumeRemovalTaskService() {
        super(ContainerVolumeRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ContainerVolumeRemovalTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            queryContainerVolumeResources(state);
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
            statusTask.name = VolumeOperationType
                    .extractDisplayName(VolumeOperationType.DELETE.id);
        }
        return statusTask;
    }

    private void queryContainerVolumeResources(ContainerVolumeRemovalTaskState state) {
        QueryTask volumeQuery = createResourcesQuery(ContainerVolumeState.class,
                state.resourceLinks);
        ServiceDocumentQuery<ContainerVolumeState> query = new ServiceDocumentQuery<>(getHost(),
                ContainerVolumeState.class);
        List<String> volumeLinks = new ArrayList<>();
        QueryUtil.addBroadcastOption(volumeQuery);
        QueryUtil.addExpandOption(volumeQuery);
        query.query(volumeQuery, (r) -> {
            if (r.hasException()) {
                failTask("Failure retrieving query results", r.getException());
            } else if (r.hasResult()) {
                volumeLinks.add(r.getDocumentSelfLink());
            } else {
                if (volumeLinks.isEmpty()) {
                    logWarning("No available resources found to be removed with links: %s",
                            state.resourceLinks);
                    proceedTo(SubStage.COMPLETED);
                } else {
                    deleteResourceInstances(state, volumeLinks, null);
                }
            }
        });
    }

    private void deleteResourceInstances(ContainerVolumeRemovalTaskState state,
            Collection<String> resourceLinks, String subTaskLink) {

        if (state.removeOnly) {
            logFine("Skipping actual container volume removal by the adapter since the removeOnly "
                    + "flag was set: %s", state.documentSelfLink);

            // skip the actual removal of container volumes through the adapter
            proceedTo(SubStage.INSTANCES_REMOVED);
            return;
        }

        if (subTaskLink == null) {
            createDeleteResourceCounterSubTask(state, resourceLinks);
            return;
        }

        try {
            logInfo("Starting delete of %d container volume resources", resourceLinks.size());
            for (String resourceLink : resourceLinks) {
                sendRequest(Operation
                        .createGet(this, resourceLink)
                        .setCompletion(
                                (o, e) -> {
                                    if (e != null) {
                                        logWarning("Failed retrieving ContainerVolumeState: "
                                                + resourceLink);
                                        completeSubTasksCounter(subTaskLink, e);
                                        return;
                                    }
                                    ContainerVolumeState volumeState = o
                                            .getBody(ContainerVolumeState.class);
                                    if (ContainerVolumeState.PowerState.RETIRED == volumeState.powerState) {
                                        logWarning(
                                                "Volume with name '%s' is retired. Deleting the state only.",
                                                volumeState.name);
                                        completeSubTasksCounter(subTaskLink, null);
                                    } else if (volumeState.originatingHostLink == null
                                            || volumeState.originatingHostLink.isEmpty()) {
                                        logWarning(
                                                "No originatingHostLink set for volume state [%s].",
                                                volumeState.documentSelfLink);
                                        completeSubTasksCounter(subTaskLink, null);
                                    } else {
                                        sendContainerVolumeDeleteRequest(state, volumeState, subTaskLink);
                                    }
                                }));
            }
            proceedTo(SubStage.INSTANCES_REMOVING);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting container volume instances", e);
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

    private void createDeleteResourceCounterSubTask(ContainerVolumeRemovalTaskState state,
            Collection<String> resourceLinks) {
        CounterSubTaskState subTaskInitState = new CounterSubTaskState();
        subTaskInitState.completionsRemaining = resourceLinks.size();
        subTaskInitState.documentExpirationTimeMicros = ServiceUtils
                .getDefaultTaskExpirationTimeInMicros();
        subTaskInitState.serviceTaskCallback = ServiceTaskCallback.create(
                state.documentSelfLink,
                TaskStage.STARTED, SubStage.INSTANCES_REMOVED,
                TaskStage.STARTED,
                state.cleanupRemoval ? SubStage.INSTANCES_REMOVED : SubStage.ERROR);

        CounterSubTaskService.createSubTask(this, subTaskInitState,
                (subTaskLink) -> deleteResourceInstances(state, resourceLinks, subTaskLink));
    }

    private void sendContainerVolumeDeleteRequest(ContainerVolumeRemovalTaskState state,
            ContainerVolumeState volumeState, String subTaskLink) {

        AdapterRequest adapterRequest = new AdapterRequest();
        String selfLink = volumeState.documentSelfLink;
        adapterRequest.resourceReference = UriUtils.buildUri(getHost(), selfLink);
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.create(UriUtils.buildUri(
                getHost(), subTaskLink).toString());
        adapterRequest.operationTypeId = (Boolean.TRUE.equals(volumeState.external)
                && state.externalInspectOnly) ? VolumeOperationType.INSPECT.id
                        : VolumeOperationType.DELETE.id;
        sendRequest(Operation.createPatch(getHost(), volumeState.adapterManagementReference.toString())
                .setBody(adapterRequest)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("AdapterRequest failed for container volume: " + selfLink, e);
                        return;
                    }
                }));
    }

    private void removeResources(ContainerVolumeRemovalTaskState state, String subTaskLink) {
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
                                failTask("Failed retrieving Container Volume State: "
                                        + resourceLink, e);
                                return;
                            }

                            ContainerVolumeState cvs = o.getBody(ContainerVolumeState.class);

                            if (Boolean.TRUE.equals(cvs.external) && state.externalInspectOnly) {
                                logFine("Skipping actual Container Volume State removal since the "
                                        + "inspectOnly flag was set: %s", state.documentSelfLink);
                                completeSubTasksCounter(subTaskLink, null);
                                return;
                            }

                            doDeleteResource(state, subTaskLink, cvs);
                        }));
            }
            proceedTo(SubStage.REMOVING_RESOURCE_STATES);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting resources", e);
        }
    }

    private void doDeleteResource(ContainerVolumeRemovalTaskState state, String subTaskLink,
            ContainerVolumeState cns) {

        Operation.createDelete(this, cns.documentSelfLink)
                .setBody(new ServiceDocument())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failed deleting container volume resources: "
                                + Utils.toString(e), null);
                        return;
                    }

                    logInfo("Deleted ContainerVolumeState: " + cns.documentSelfLink);
                    completeSubTasksCounter(subTaskLink, null);
                }).sendWith(this);
    }

    private QueryTask createResourcesQuery(Class<? extends ServiceDocument> type,
            Collection<String> resourceLinks) {
        QueryTask query = QueryUtil.buildQuery(type, false);
        QueryUtil.addListValueClause(query, ServiceDocument.FIELD_NAME_SELF_LINK, resourceLinks);

        return query;
    }
}
