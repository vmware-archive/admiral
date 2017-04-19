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

import static com.vmware.admiral.common.ManagementUriParts.CLOSURES_CONTAINER_DESC;
import static com.vmware.admiral.common.ManagementUriParts.CONTAINER_DESC;
import static com.vmware.admiral.compute.container.SystemContainerDescriptions.isDiscoveredContainer;
import static com.vmware.admiral.compute.container.SystemContainerDescriptions.isSystemContainer;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
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
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.request.ContainerRemovalTaskService.ContainerRemovalTaskState.SubStage;
import com.vmware.admiral.request.ReservationRemovalTaskService.ReservationRemovalTaskState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.CounterSubTaskService;
import com.vmware.admiral.service.common.CounterSubTaskService.CounterSubTaskState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
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
        @PropertyOptions(usage = { REQUIRED, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

        /** (Internal) Set by Task for the query to retrieve all Containers based on the links. */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String resourceQueryTaskLink;

        /** (Internal) Set by task to run data collection for the affected hosts */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
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
            complete();
            break;
        case ERROR:
            completeWithError();
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
                    proceedTo(SubStage.COMPLETED);
                } else {
                    proceedTo(SubStage.INSTANCES_REMOVING, s -> {
                        s.containersParentLinks = state.containersParentLinks;
                    });

                    deleteResourceInstances(state, containerLinks, null);
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

    private void deleteResourceInstances(ContainerRemovalTaskState state,
            Collection<String> resourceLinks, String subTaskLink) {

        if (state.removeOnly) {
            logFine("Skipping actual container removal by the adapter since the removeOnly flag "
                    + "was set: %s", state.documentSelfLink);

            // skip the actual removal of containers through the adapter
            proceedTo(SubStage.INSTANCES_REMOVED);
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
        ContainerState ps = new ContainerState();
        ps.isDeleted = true;
        sendRequest(Operation.createPatch(getHost(), containerState.documentSelfLink)
                .setBody(ps)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(
                                "Failed to modify container state isDeleted before container delete: "
                                        + containerState.documentSelfLink, e);
                        return;
                    } else {
                        AdapterRequest adapterRequest = new AdapterRequest();
                        String selfLink = containerState.documentSelfLink;
                        adapterRequest.resourceReference = UriUtils.buildUri(getHost(), selfLink);
                        adapterRequest.serviceTaskCallback = ServiceTaskCallback
                                .create(subTaskLink);
                        adapterRequest.operationTypeId = ContainerOperationType.DELETE.id;
                        sendRequest(Operation.createPatch(getHost(),
                                containerState.adapterManagementReference.toString())
                                .setBody(adapterRequest)
                                .setContextId(getSelfId())
                                .setCompletion((o1, e1) -> {
                                    if (e1 != null) {
                                        failTask("AdapterRequest failed for container: " + selfLink,
                                                e1);
                                        ContainerState ps1 = new ContainerState();
                                        ps1.isDeleted = false;
                                        sendRequest(Operation.createPatch(getHost(),
                                                containerState.documentSelfLink)
                                                .setBody(ps1)
                                                .setCompletion((o2, e2) -> {
                                                    if (e2 != null) {
                                                        logWarning(
                                                                "Failed to modify container state  isDeleted after container delete: "
                                                                        + containerState.documentSelfLink,
                                                                e);
                                                        return;
                                                    }
                                                }));
                                        return;
                                    }
                                }));
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
                                    // Don't fail if container is still collected but has already
                                    // been removed.
                                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                                        logFine("Resource [%s] not found, it should have "
                                                        + "already been removed!",
                                                resourceLink);
                                        completeSubTasksCounter(subTaskLink, null);
                                        completeSubTasksCounter(subTaskLink, null);
                                        return;
                                    }
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
            proceedTo(SubStage.REMOVING_RESOURCE_STATES);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting resources", e);
        }
    }

    private void doDeleteResource(ContainerRemovalTaskState state, String subTaskLink, ContainerState cs) {
        QueryTask compositeQueryTask = QueryUtil.buildQuery(ContainerState.class, true);

        String containerDescriptionLink = UriUtils.buildUriPath(
                CONTAINER_DESC,
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
                        AtomicLong skipDeleteDescriptionOperationException = new AtomicLong();
                        AtomicLong skipHostPortProfileNotFoundException = new AtomicLong();
                        List<AtomicLong> skipOperationExceptions = Arrays.asList(
                                skipDeleteDescriptionOperationException, skipHostPortProfileNotFoundException);

                        Operation delContainerOpr = deleteContainer(cs);
                        Operation placementOpr = releaseResourcePlacement(state, cs, subTaskLink);
                        Operation releasePortsOpr = releasePorts(cs, skipHostPortProfileNotFoundException);

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
                        if (releasePortsOpr != null) {
                            operations.add(releasePortsOpr);
                        }
                        // delete container description when deleting all its containers
                        if (state.resourceLinks.containsAll(resourcesSharingDesc)) {
                            // there could be a race condition when containers are in cluster and
                            // the same description tries to be deleted multiple times, that's why
                            // we need to skipOperationException is the description is NOT FOUND
                            Operation deleteContainerDescription = deleteContainerDescription(cs,
                                    skipDeleteDescriptionOperationException);
                            operations.add(deleteContainerDescription);
                        }

                        OperationJoin.create(operations).setCompletion((ops, exs) -> {
                            // remove skipped exceptions
                            if (exs != null) {
                                skipOperationExceptions
                                        .stream()
                                        .filter(p -> p.get() != 0)
                                        .forEach(p -> exs.remove(p.get()));
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
                            /*When removing container state, remove also if there are any logs
                              created. This is workaround for:
                              https://www.pivotaltracker.com/n/projects/1471320/stories/143794415 */
                            sendRequest(Operation.createDelete(this, UriUtils.buildUriPath(
                                    LogService.FACTORY_LINK, Service.getId(cs.documentSelfLink))));
                        });
    }

    private Operation deleteContainerDescription(ContainerState cs,
            AtomicLong skipOperationException) {

        Operation deleteContainerDesc = Operation
                .createGet(this, cs.descriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND ||
                                    e instanceof CancellationException) {
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
                                                    logWarning(
                                                            "Failed deleting ContainerDescription: "
                                                                    + cd.documentSelfLink, ex);
                                                    return;
                                                }
                                                logInfo("Deleted ContainerDescription: "
                                                        + cd.documentSelfLink);
                                            }));
                        });

        return deleteContainerDesc;
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

        if (isClosureContainer(cs)) {
            logFine("Skipping releasing placement because container is related to closures: %s",
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

    private boolean isClosureContainer(ContainerState cs) {
        return cs.descriptionLink != null && cs.descriptionLink
                .startsWith(CLOSURES_CONTAINER_DESC);
    }

    private Operation releasePorts(ContainerState cs, AtomicLong skipOperationException) {
        String hostPortProfileLink = HostPortProfileService.getHostPortProfileLink(cs.parentLink);
        Operation operation = Operation
                .createGet(this, hostPortProfileLink)
                .setCompletion(
                        (o, e) -> {
                            if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND ||
                                    e instanceof CancellationException) {
                                logWarning("Cannot find host port profile [%s]", hostPortProfileLink);
                                skipOperationException.set(o.getId());
                            }

                            if (e != null) {
                                logWarning("Failed retrieving HostPortProfileState: "
                                        + hostPortProfileLink, e);
                                return;
                            }
                            HostPortProfileService.HostPortProfileState profile =
                                    o.getBody(HostPortProfileService.HostPortProfileState.class);

                            Set<Long> allocatedPorts = HostPortProfileService.getAllocatedPorts(
                                    profile, cs.documentSelfLink);

                            if (allocatedPorts.isEmpty()) {
                                return;
                            }
                            // release all ports of the container
                            HostPortProfileService.HostPortProfileReservationRequest request =
                                    new HostPortProfileService.HostPortProfileReservationRequest();
                            request.containerLink = cs.documentSelfLink;
                            request.mode = HostPortProfileService.HostPortProfileReservationRequestMode.RELEASE;

                            sendRequest(Operation
                                    .createPatch(getHost(), profile.documentSelfLink)
                                    .setBody(request)
                                    .setCompletion(
                                            (op, ex) -> {
                                                if (ex != null) {
                                                    logWarning("Failed releasing container ports: " + cs.documentSelfLink, ex);
                                                    return;
                                                }
                                            }));
                        });
        return operation;
    }
}
