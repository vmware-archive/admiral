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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.ContainerHostRemovalTaskService.ContainerHostRemovalTaskState.SubStage;
import com.vmware.admiral.request.ContainerNetworkRemovalTaskService.ContainerNetworkRemovalTaskState;
import com.vmware.admiral.request.ContainerRemovalTaskService.ContainerRemovalTaskState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.CounterSubTaskService;
import com.vmware.admiral.service.common.CounterSubTaskService.CounterSubTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Task implementing removal of container hosts (including removing all the ContainerStates that
 * depend on the hosts)
 */
public class ContainerHostRemovalTaskService extends
        AbstractTaskStatefulService<ContainerHostRemovalTaskService.ContainerHostRemovalTaskState,
                ContainerHostRemovalTaskService.ContainerHostRemovalTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Host Removal";

    public static class ContainerHostRemovalTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerHostRemovalTaskState.SubStage> {

        private static final String FIELD_NAME_RESOURCE_LINKS = "resourceLinks";
        private static final String FIELD_NAME_CONTAINER_QUERY_TASK_LINK = "containerQueryTaskLink";

        /** (Required) The resources on which the given operation will be applied */
        public List<String> resourceLinks;

        /** (Internal) Set by Task for the query to retrieve all the containers for the given hosts. */
        public String containerQueryTaskLink;

        public boolean skipComputeHostRemoval;

        public static enum SubStage {
            CREATED,
            SUSPENDING_HOSTS,
            SUSPENDED_HOSTS,
            REMOVING_CONTAINERS,
            REMOVED_CONTAINERS,
            REMOVING_NETWORKS,
            REMOVED_NETWORKS,
            REMOVING_HOSTS,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(REMOVING_HOSTS, SUSPENDING_HOSTS, REMOVING_CONTAINERS,
                            REMOVING_NETWORKS));
        }
    }

    public ContainerHostRemovalTaskService() {
        super(ContainerHostRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(ContainerHostRemovalTaskState state)
            throws IllegalArgumentException {

        assertNotEmpty(state.resourceLinks, "resourceLinks");
    }

    @Override
    protected void handleStartedStagePatch(ContainerHostRemovalTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            disableContainerHosts(state, null);
            break;
        case SUSPENDING_HOSTS:
            break;
        case SUSPENDED_HOSTS:
            queryContainers(state);
            break;
        case REMOVING_CONTAINERS:
            break;
        case REMOVED_CONTAINERS:
            queryNetworks(state);
            break;
        case REMOVING_NETWORKS:
            break;
        case REMOVED_NETWORKS:
            removeHosts(state, null);
            break;
        case REMOVING_HOSTS:
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

    private void disableContainerHosts(ContainerHostRemovalTaskState state, String subTaskLink) {
        if (subTaskLink == null) {
            CounterSubTaskState subTaskInitState = new CounterSubTaskState();
            subTaskInitState.completionsRemaining = state.resourceLinks.size();
            subTaskInitState.documentExpirationTimeMicros = ServiceUtils
                    .getDefaultTaskExpirationTimeInMicros();
            subTaskInitState.serviceTaskCallback = ServiceTaskCallback.create(
                    getSelfLink(),
                    TaskStage.STARTED, SubStage.SUSPENDED_HOSTS,
                    TaskStage.STARTED, SubStage.ERROR);

            CounterSubTaskService.createSubTask(this, subTaskInitState,
                    (link) -> disableContainerHosts(state, link));
            return;
        }
        ComputeState computeState = new ComputeState();
        computeState.powerState = PowerState.SUSPEND;

        try {
            AtomicBoolean error = new AtomicBoolean();
            for (String resourceLink : state.resourceLinks) {
                sendRequest(Operation
                        .createPatch(this, resourceLink)
                        .setBody(computeState)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                if (error.compareAndSet(false, true)) {
                                    logWarning("Failed suspending ComputeState: " + resourceLink);
                                    completeSubTasksCounter(subTaskLink, e);
                                }
                                return;
                            }
                            completeSubTasksCounter(subTaskLink, null);
                        }));
            }
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.SUSPENDING_HOSTS));
        } catch (Throwable e) {
            failTask("Unexpected exception while suspending container host", e);
        }
    }

    private void queryContainers(ContainerHostRemovalTaskState state) {
        QueryTask containerQuery = QueryUtil.buildQuery(ContainerState.class, false);

        QueryUtil.addListValueClause(containerQuery,
                ContainerState.FIELD_NAME_PARENT_LINK, state.resourceLinks);

        List<String> containerLinks = new ArrayList<>();
        new ServiceDocumentQuery<ContainerState>(getHost(), ContainerState.class).query(
                containerQuery, (r) -> {
                    if (r.hasException()) {
                        failTask("Failure retrieving query results", r.getException());
                        return;
                    } else if (r.hasResult()) {
                        containerLinks.add(r.getDocumentSelfLink());
                    } else {
                        if (containerLinks.isEmpty()) {
                            queryNetworks(state);
                            return;
                        }

                        removeContainers(state, containerLinks);
                    }
                });
    }

    private void removeContainers(ContainerHostRemovalTaskState state,
            List<String> containerSelfLinks) {

        // run a sub task for removing the containers
        ContainerRemovalTaskState containerRemovalTask = new ContainerRemovalTaskState();
        containerRemovalTask.resourceLinks = containerSelfLinks;
        containerRemovalTask.removeOnly = true;
        containerRemovalTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, SubStage.REMOVED_CONTAINERS,
                TaskStage.STARTED, SubStage.ERROR);
        containerRemovalTask.requestTrackerLink = state.requestTrackerLink;

        Operation startPost = Operation
                .createPost(this, ContainerRemovalTaskFactoryService.SELF_LINK)
                .setBody(containerRemovalTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating container removal task", e);
                        return;
                    }

                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.REMOVING_CONTAINERS));
                });
        sendRequest(startPost);
    }

    private void queryNetworks(ContainerHostRemovalTaskState state) {
        QueryTask networkQuery = QueryUtil.buildQuery(ContainerNetworkState.class, false);

        String parentLinksItemField = QueryTask.QuerySpecification
                .buildCollectionItemName(ContainerNetworkState.FIELD_NAME_PARENT_LINKS);
        QueryUtil.addListValueClause(networkQuery, parentLinksItemField, state.resourceLinks);
        QueryUtil.addExpandOption(networkQuery);

        List<String> networkLinks = new ArrayList<>();
        new ServiceDocumentQuery<ContainerNetworkState>(getHost(), ContainerNetworkState.class)
                .query(networkQuery, (r) -> {
                    if (r.hasException()) {
                        failTask("Failure retrieving query results", r.getException());
                        return;
                    } else if (r.hasResult()) {
                        ContainerNetworkState networkState = r.getResult();

                        List<String> parentLinks = new ArrayList<>(networkState.parentLinks);
                        parentLinks.removeAll(state.resourceLinks);

                        if (parentLinks.isEmpty()) {
                            networkLinks.add(networkState.documentSelfLink);
                        } else {
                            networkState.parentLinks = parentLinks;
                            updateNetworkParentLinks(networkState);
                        }
                    } else {
                        if (networkLinks.isEmpty()) {
                            removeHosts(state, null);
                            return;
                        }

                        removeNetworks(state, networkLinks);
                    }
                });
    }

    private void updateNetworkParentLinks(ContainerNetworkState networkState) {
        ContainerNetworkState patchNetworkState = new ContainerNetworkState();
        patchNetworkState.parentLinks = networkState.parentLinks;

        if ((networkState.originatingHostLink != null) && !networkState.parentLinks.isEmpty()
                && (!networkState.parentLinks.contains(networkState.originatingHostLink))) {
            // set another parent like the "owner" of the network
            patchNetworkState.originatingHostLink = networkState.parentLinks.get(0);
        }

        sendRequest(Operation
                .createPatch(this, networkState.documentSelfLink)
                .setBody(patchNetworkState)
                .setCompletion((o, ex) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        logFine("Network %s not found to be updated its parent links.",
                                networkState.documentSelfLink);
                    } else if (ex != null) {
                        logWarning("Failed to update network %s parent links: %s",
                                networkState.documentSelfLink, Utils.toString(ex));
                    } else {
                        logInfo("Updated network parent links: %s", networkState.documentSelfLink);
                    }
                }));
    }

    private void removeNetworks(ContainerHostRemovalTaskState state,
            List<String> networkSelfLinks) {

        // run a sub task for removing the networks
        ContainerNetworkRemovalTaskState networkRemovalTask = new ContainerNetworkRemovalTaskState();
        networkRemovalTask.resourceLinks = networkSelfLinks;
        networkRemovalTask.removeOnly = true;
        networkRemovalTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, SubStage.REMOVED_NETWORKS,
                TaskStage.STARTED, SubStage.ERROR);
        networkRemovalTask.requestTrackerLink = state.requestTrackerLink;

        Operation startPost = Operation
                .createPost(this, ContainerNetworkRemovalTaskService.FACTORY_LINK)
                .setBody(networkRemovalTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating container network removal task", e);
                        return;
                    }

                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.REMOVING_NETWORKS));
                });
        sendRequest(startPost);
    }

    private void removeHosts(ContainerHostRemovalTaskState state, String subTaskLink) {
        if (subTaskLink == null && !state.skipComputeHostRemoval) {
            createCounterSubTask(state, state.resourceLinks.size(),
                    (link) -> removeHosts(state, link));
            return;
        }

        try {
            logInfo("Starting delete of %d container hosts", state.resourceLinks.size());

            // Notify the data collection service first
            URI uri = UriUtilsExtended.buildUri(getHost(),
                    ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK);
            ContainerHostDataCollectionService.ContainerHostDataCollectionState dataCollectionState = new ContainerHostDataCollectionService.ContainerHostDataCollectionState();
            dataCollectionState.computeContainerHostLinks = state.resourceLinks;
            dataCollectionState.remove = true;
            sendRequest(Operation.createPatch(uri)
                    .setBody(dataCollectionState)
                    .setCompletion((o1, ex1) -> {
                        if (ex1 != null) {
                            logWarning("Failed to update host data collection: %s",
                                    ex1.getMessage());
                        }

                        if (state.skipComputeHostRemoval) {
                            sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
                            return;
                        }

                        for (String resourceLink : state.resourceLinks) {
                            sendRequest(Operation
                                    .createDelete(this, resourceLink)
                                    .setBody(new ServiceDocument())
                                    .setCompletion((o, e) -> {
                                        if (e != null) {
                                            logWarning("Failed deleting ComputeState: "
                                                    + resourceLink);
                                            completeSubTasksCounter(subTaskLink, e);
                                            return;
                                        }
                                        completeSubTasksCounter(subTaskLink, null);
                                    }));
                        }
                    }));

            sendSelfPatch(createUpdateSubStageTask(state, SubStage.REMOVING_HOSTS));
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting container host instances", e);
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

    @Override
    protected boolean validateStageTransition(Operation patch,
            ContainerHostRemovalTaskState patchBody, ContainerHostRemovalTaskState currentState) {

        currentState.containerQueryTaskLink = mergeProperty(currentState.containerQueryTaskLink,
                patchBody.containerQueryTaskLink);

        // return false when there are no validation issues
        return false;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();

        setDocumentTemplateIndexingOptions(template, EnumSet.of(PropertyIndexingOption.STORE_ONLY),
                ContainerHostRemovalTaskState.FIELD_NAME_RESOURCE_LINKS,
                ContainerHostRemovalTaskState.FIELD_NAME_CONTAINER_QUERY_TASK_LINK);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.SINGLE_ASSIGNMENT),
                ContainerHostRemovalTaskState.FIELD_NAME_RESOURCE_LINKS,
                ContainerHostRemovalTaskState.FIELD_NAME_CONTAINER_QUERY_TASK_LINK);

        setDocumentTemplateUsageOptions(template, EnumSet.of(PropertyUsageOption.SERVICE_USE),
                ContainerHostRemovalTaskState.FIELD_NAME_CONTAINER_QUERY_TASK_LINK);

        template.documentDescription.serializedStateSizeLimit = 128 * 1024; // 128 Kb

        return template;
    }
}
