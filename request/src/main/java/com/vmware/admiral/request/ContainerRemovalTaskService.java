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
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;
import static com.vmware.admiral.compute.container.SystemContainerDescriptions.isDiscoveredContainer;
import static com.vmware.admiral.compute.container.SystemContainerDescriptions.isSystemContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService.ContainerHostDataCollectionState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.request.ContainerRemovalTaskService.ContainerRemovalTaskState.SubStage;
import com.vmware.admiral.request.ReservationRemovalTaskService.ReservationRemovalTaskState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.CounterSubTaskService;
import com.vmware.admiral.service.common.CounterSubTaskService.CounterSubTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Task implementing removal of Containers.
 */
public class ContainerRemovalTaskService
        extends
        AbstractTaskStatefulService<ContainerRemovalTaskService.ContainerRemovalTaskState, ContainerRemovalTaskService.ContainerRemovalTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Container Removal";

    public static class ContainerRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerRemovalTaskState.SubStage> {
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

        /** (Internal) Set by Task for the query to retrieve all Containers based on the links. */
        public String resourceQueryTaskLink;

        /** (Internal) Set by task to run data collection for the affected hosts */
        public Set<String> containersParentLinks;

        /**
         * whether to actually go and destroy the container using the adapter or just remove the
         * ContainerState
         */
        public boolean removeOnly;

        /**
         * whether to skip the associated reservation or not
         */
        public boolean skipReleaseResourcePlacement;
    }

    public ContainerRemovalTaskService() {
        super(ContainerRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ContainerRemovalTaskState state) {
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
            updateContainerHosts(state);
            complete(state, SubStage.COMPLETED);
            break;
        case ERROR:
            completeWithError(state, SubStage.ERROR);
            break;
        default:
            break;
        }
    }

    private void updateContainerHosts(ContainerRemovalTaskState state) {

        // Don't trigger data collection as the containers will be discovered and the whole removal
        // will be undone
        if (state.removeOnly) {
            return;
        }

        ContainerHostDataCollectionState body = new ContainerHostDataCollectionState();
        body.computeContainerHostLinks = state.containersParentLinks;

        sendRequest(Operation.createPatch(this,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK)
                .setBody(body)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failure during host data collection. Error: [%s]",
                                Utils.toString(e));
                    }
                }));
    }

    @Override
    protected boolean validateStageTransition(Operation patch, ContainerRemovalTaskState patchBody,
            ContainerRemovalTaskState currentState) {
        currentState.resourceQueryTaskLink = mergeProperty(currentState.resourceQueryTaskLink,
                patchBody.resourceQueryTaskLink);
        currentState.containersParentLinks = mergeProperty(currentState.containersParentLinks,
                patchBody.containersParentLinks);
        return false;
    }

    @Override
    protected void validateStateOnStart(ContainerRemovalTaskState state)
            throws IllegalArgumentException {
        assertNotEmpty(state.resourceLinks, "resourceLinks");
    }

    @Override
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> state) {
        TaskStatusState statusTask = super.fromTask(state);
        if (SubStage.INSTANCES_REMOVED == state.taskSubStage
                || SubStage.COMPLETED == state.taskSubStage) {
            statusTask.name = ContainerOperationType
                    .extractDisplayName(ContainerOperationType.DELETE.id);
        }
        return statusTask;
    }

    private void queryContainerResources(ContainerRemovalTaskState state) {
        QueryTask computeQuery = createResourcesQuery(ContainerState.class, state.resourceLinks);
        ServiceDocumentQuery<ContainerState> query = new ServiceDocumentQuery<>(getHost(),
                ContainerState.class);
        List<String> containerLinks = new ArrayList<>();
        state.containersParentLinks = new HashSet<>();
        QueryUtil.addBroadcastOption(computeQuery);
        QueryUtil.addExpandOption(computeQuery);
        query.query(computeQuery, (r) -> {
            if (r.hasException()) {
                failTask("Failure retrieving query results", r.getException());
            } else if (r.hasResult()) {
                containerLinks.add(r.getDocumentSelfLink());
                state.containersParentLinks.add(r.getResult().parentLink);
            } else {
                if (containerLinks.isEmpty()) {
                    logWarning(
                            "No available resources found to be removed with links: %s",
                            state.resourceLinks);
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
                } else {
                    ContainerRemovalTaskState patchBody = createUpdateSubStageTask(state,
                            SubStage.INSTANCES_REMOVING);
                    patchBody.containersParentLinks = state.containersParentLinks;
                    sendSelfPatch(patchBody);

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

    private void deleteResourceInstances(ContainerRemovalTaskState state,
            Collection<String> resourceLinks, String subTaskLink) {

        if (state.removeOnly) {
            logFine("Skipping actual container removal by the adapter since the removeOnly flag "
                    + "was set: %s", state.documentSelfLink);

            // skip the actual removal of containers through the adapter
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
                                        logWarning("Failed retrieving ContainerState: "
                                                + resourceLink);
                                        completeSubTasksCounter(subTaskLink, e);
                                        return;
                                    }
                                    ContainerState containerState = o.getBody(ContainerState.class);
                                    if (isAllocatedOnlyContainer(containerState)) {
                                        completeSubTasksCounter(subTaskLink, null);
                                    } else if (containerState.id == null
                                            || containerState.id.isEmpty()) {
                                        logWarning("No ID set for container state: [%s]  ",
                                                containerState.documentSelfLink);
                                        completeSubTasksCounter(subTaskLink, null);
                                    } else if (isSystemContainer(o.getBody(ContainerState.class))) {
                                        logWarning(
                                                "Resource [%s] will not be removed because it is a system container",
                                                o.getBody(ContainerState.class).documentSelfLink);
                                        completeSubTasksCounter(subTaskLink, null);
                                    } else {
                                        sendContainerDeleteRequest(containerState, subTaskLink);
                                    }
                                }));
            }
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting container instances", e);
        }
    }

    private boolean isAllocatedOnlyContainer(ContainerState containerState) {
        return PowerState.PROVISIONING == containerState.powerState
                && ContainerState.CONTAINER_ALLOCATION_STATUS
                        .equals(containerState.status);
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

    private void createDeleteResourceCounterSubTask(ContainerRemovalTaskState state,
            Collection<String> resourceLinks) {
        CounterSubTaskState subTaskInitState = new CounterSubTaskState();
        subTaskInitState.completionsRemaining = resourceLinks.size();
        subTaskInitState.documentExpirationTimeMicros = ServiceUtils
                .getDefaultTaskExpirationTimeInMicros();
        subTaskInitState.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, SubStage.INSTANCES_REMOVED,
                TaskStage.STARTED, SubStage.ERROR);

        CounterSubTaskService.createSubTask(this, subTaskInitState,
                (subTaskLink) -> deleteResourceInstances(state, resourceLinks, subTaskLink));
    }

    private void sendContainerDeleteRequest(ContainerState containerState, String subTaskLink) {
        AdapterRequest adapterRequest = new AdapterRequest();
        String selfLink = containerState.documentSelfLink;
        adapterRequest.resourceReference = UriUtils.buildUri(getHost(), selfLink);
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.create(UriUtils.buildUri(
                getHost(), subTaskLink).toString());
        adapterRequest.operationTypeId = ContainerOperationType.DELETE.id;
        sendRequest(Operation.createPatch(containerState.adapterManagementReference)
                .setBody(adapterRequest)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("AdapterRequest failed for container: " + selfLink, e);
                        return;
                    }
                }));
    }

    private void removeResources(ContainerRemovalTaskState state, String subTaskLink) {
        if (subTaskLink == null) {
            // count 2 * resourceLinks (to keep track of each removal operation starting and ending)
            createCounterSubTask(state, 2 * state.resourceLinks.size(),
                    (link) -> removeResources(state, link));
            return;
        }
        AtomicBoolean isRemoveHost = new AtomicBoolean(state.serviceTaskCallback.serviceSelfLink
                .startsWith(ManagementUriParts.REQUEST_HOST_REMOVAL_OPERATIONS));

        try {
            for (String resourceLink : state.resourceLinks) {
                sendRequest(Operation
                        .createGet(this, resourceLink)
                        .setCompletion(
                                (o, e) -> {
                                    if (e != null) {
                                        failTask("Failed retrieving Container State: "
                                                + resourceLink, e);
                                        return;
                                    }

                                    if (isSystemContainer(o.getBody(ContainerState.class))
                                            && !(isRemoveHost.get())) {
                                        logWarning(
                                                "Resource [%s] will not be removed because it is a system container",
                                                o.getBody(ContainerState.class).documentSelfLink);
                                        // need to complete the counter twice, because the removal
                                        // task is not created in this case
                                        completeSubTasksCounter(subTaskLink, null);
                                        completeSubTasksCounter(subTaskLink, null);
                                        return;
                                    }

                                    ContainerState cs = o.getBody(ContainerState.class);
                                    doDeleteResource(state, subTaskLink, cs);
                                }));
            }
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.REMOVING_RESOURCE_STATES));
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting resources", e);
        }
    }

    private void doDeleteResource(ContainerRemovalTaskState state, String subTaskLink,
            ContainerState cs) {

        QueryTask compositeQueryTask = QueryUtil.buildQuery(ContainerState.class, true);

        String containerDescriptionLink = UriUtils.buildUriPath(
                ManagementUriParts.CONTAINER_DESC,
                Service.getId(cs.descriptionLink));
        QueryUtil.addListValueClause(compositeQueryTask,
                ContainerState.FIELD_NAME_DESCRIPTION_LINK,
                Arrays.asList(containerDescriptionLink));

        final List<String> resourcesSharingDesc = new ArrayList<String>();
        new ServiceDocumentQuery<ContainerState>(getHost(), ContainerState.class)
                .query(compositeQueryTask, (r) -> {
                    if (r.hasException()) {
                        logSevere(
                                "Failed to retrieve containers, sharing the same containerdescription: %s -%s",
                                r.getDocumentSelfLink(), r.getException());
                    } else if (r.hasResult()) {
                        resourcesSharingDesc.add(r.getDocumentSelfLink());
                    } else {
                        AtomicLong skipOperationException = new AtomicLong();

                        Operation delContainerOpr = deleteContainer(cs);
                        Operation placementOpr = releaseResourcePlacement(state, cs, subTaskLink);

                        // list of operations to execute to release container resources
                        List<Operation> operations = new ArrayList<>();
                        operations.add(delContainerOpr);
                        // add placementOpr only when needed
                        if (placementOpr != null) {
                            operations.add(placementOpr);
                        } else {
                            // if ReservationRemovalTask is not started, count it as finished
                            completeSubTasksCounter(subTaskLink, null);
                        }
                        // delete container description when deleting all its containers
                        if (state.resourceLinks.containsAll(resourcesSharingDesc)) {
                            // there could be a race condition when containers are in cluster and
                            // the same description tries to be deleted multiple times, that's why
                            // we need to skipOperationException is the description is NOT FOUND
                            Operation deleteContainerDescription = deleteContainerDescription(cs,
                                    skipOperationException);
                            operations.add(deleteContainerDescription);
                        }

                        OperationJoin.create(operations).setCompletion((ops, exs) -> {
                            // remove skipped exceptions
                            if (exs != null && skipOperationException.get() != 0) {
                                exs.remove(skipOperationException.get());
                            }
                            // fail the task is there are exceptions in the children operations
                            if (exs != null && !exs.isEmpty()) {
                                failTask("Failed deleting container resources: "
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

    private Operation deleteContainer(ContainerState cs) {
        return Operation
                .createDelete(this, cs.documentSelfLink)
                .setBody(new ServiceDocument())
                .setCompletion(
                        (op, ex) -> {
                            if (ex != null) {
                                logWarning("Failed deleting ContainerState: " + cs.documentSelfLink,
                                        ex);
                                return;
                            }
                            logInfo("Deleted ContainerState: " + cs.documentSelfLink);
                        });
    }

    private Operation deleteContainerDescription(ContainerState cs,
            AtomicLong skipOperationException) {

        Operation deleteContanerDesc = Operation
                .createGet(this, cs.descriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                                logFine("Resource [%s] not found, it will not be removed!",
                                        cs.descriptionLink);
                                skipOperationException.set(o.getId());
                                return;
                            }

                            if (e != null) {
                                logWarning("Failed retrieving ContainerDescription: "
                                        + cs.descriptionLink, e);
                                return;
                            }

                            ContainerDescription cd = o.getBody(ContainerDescription.class);

                            if (cd.parentDescriptionLink == null) {
                                logFine("Resource [%s] will not be removed because it doesn't contain parentDescriptionLink!",
                                        o.getBody(ContainerDescription.class).documentSelfLink);
                                return;
                            }

                            sendRequest(Operation
                                    .createDelete(this, cd.documentSelfLink)
                                    .setBody(new ServiceDocument())
                                    .setCompletion(
                                            (op, ex) -> {
                                                if (ex != null) {
                                                    logWarning("Failed deleting ContainerDescription: " + cd.documentSelfLink, ex);
                                                    return;
                                                }
                                                logInfo("Deleted ContainerDescription: " + cd.documentSelfLink);
                                            }));
                        });

        return deleteContanerDesc;
    }

    private Operation releaseResourcePlacement(ContainerRemovalTaskState state, ContainerState cs,
            String subTaskLink) {

        if (isDiscoveredContainer(cs) || state.skipReleaseResourcePlacement) {
            logFine("Skipping releasing placement because container is a discovered one: %s",
                    cs.documentSelfLink);
            return null;
        }

        if (isSystemContainer(cs)) {
            logFine("Skipping releasing placement because container is a system one: %s",
                    cs.documentSelfLink);
            return null;
        }

        ReservationRemovalTaskState rsrvTask = new ReservationRemovalTaskState();
        rsrvTask.resourceCount = 1;
        rsrvTask.resourceDescriptionLink = cs.descriptionLink;
        rsrvTask.groupResourcePlacementLink = cs.groupResourcePlacementLink;
        rsrvTask.requestTrackerLink = state.requestTrackerLink;
        // Completion of the reservation removal also notifies the counter task
        rsrvTask.serviceTaskCallback = ServiceTaskCallback.create(subTaskLink);

        return Operation.createPost(this, ReservationRemovalTaskFactoryService.SELF_LINK)
                .setBody(rsrvTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failed creating task to delete placement "
                                + cs.groupResourcePlacementLink, e);
                        return;
                    }
                });
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();

        setDocumentTemplateIndexingOptions(template, EnumSet.of(PropertyIndexingOption.STORE_ONLY),
                ContainerRemovalTaskState.FIELD_NAME_RESOURCE_LINKS,
                ContainerRemovalTaskState.FIELD_NAME_RESOURCE_QUERY_TASK_LINK,
                ContainerRemovalTaskState.FIELD_NAME_REMOVE_ONLY);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.SINGLE_ASSIGNMENT),
                ContainerRemovalTaskState.FIELD_NAME_RESOURCE_LINKS,
                ContainerRemovalTaskState.FIELD_NAME_RESOURCE_QUERY_TASK_LINK,
                ContainerRemovalTaskState.FIELD_NAME_REMOVE_ONLY);

        setDocumentTemplateUsageOptions(template, EnumSet.of(PropertyUsageOption.SERVICE_USE),
                ContainerRemovalTaskState.FIELD_NAME_RESOURCE_QUERY_TASK_LINK);

        template.documentDescription.serializedStateSizeLimit = 128 * 1024; // 128 Kb

        return template;
    }
}
