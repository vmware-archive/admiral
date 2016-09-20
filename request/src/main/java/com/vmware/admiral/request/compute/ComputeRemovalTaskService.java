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

package com.vmware.admiral.request.compute;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.SubscriptionUtils;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.request.ContainerHostRemovalTaskFactoryService;
import com.vmware.admiral.request.ContainerHostRemovalTaskService.ContainerHostRemovalTaskState;
import com.vmware.admiral.request.ReservationRemovalTaskFactoryService;
import com.vmware.admiral.request.ReservationRemovalTaskService.ReservationRemovalTaskState;
import com.vmware.admiral.request.compute.ComputeRemovalTaskService.ComputeRemovalTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Task implementing removal of compute hosts.
 */
public class ComputeRemovalTaskService extends
        AbstractTaskStatefulService<ComputeRemovalTaskService.ComputeRemovalTaskState, ComputeRemovalTaskService.ComputeRemovalTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Compute Removal";

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_REMOVAL_OPEARTIONS;

    public static class ComputeRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeRemovalTaskState.SubStage> {

        @Documentation(description = "The compute resources to be removed")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public List<String> resourceLinks;

        @Documentation(description = "whether to skip the associated reservation or not")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public boolean skipReleaseResourceQuota;

        public static enum SubStage {
            CREATED,
            SUSPENDING_COMPUTES,
            SUSPENDED_COMPUTES,
            REMOVING_CONTAINER_HOSTS,
            REMOVED_CONTAINER_HOSTS,
            DEALLOCATING_RESOURCES,
            DEALLOCATED_RESOURCES,
            REMOVING_COMPUTES,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(SUSPENDING_COMPUTES, DEALLOCATING_RESOURCES,
                            REMOVING_CONTAINER_HOSTS));
        }
    }

    public ComputeRemovalTaskService() {
        super(ComputeRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(ComputeRemovalTaskState state)
            throws IllegalArgumentException {

        assertNotEmpty(state.resourceLinks, "resourceLinks");
    }

    @Override
    protected void handleStartedStagePatch(ComputeRemovalTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            disableComputeHosts(state);
            break;
        case SUSPENDING_COMPUTES:
            break;
        case SUSPENDED_COMPUTES:
            queryContainerHosts(state);
            break;
        case REMOVING_CONTAINER_HOSTS:
            break;
        case REMOVED_CONTAINER_HOSTS:
            deallocateResources(state, null);
            break;
        case DEALLOCATING_RESOURCES:
            break;
        case DEALLOCATED_RESOURCES:
            deleteResources(state);
            break;
        case REMOVING_COMPUTES:
            subscribeToResourceRemovalTask(state);
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

    private void queryContainerHosts(ComputeRemovalTaskState state) {

        Query computeHost = QueryUtil.addListValueClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                state.resourceLinks, MatchType.TERM);
        computeHost.occurance = Occurance.MUST_OCCUR;
        Query containerHost = new Query().setTermPropertyName(QuerySpecification
                .buildCompositeFieldName(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME))
                .setTermMatchValue("true");
        containerHost.occurance = Occurance.MUST_OCCUR;

        QueryTask computeQuery = QueryUtil.buildQuery(ComputeState.class, false, containerHost,
                computeHost);
        QueryUtil.addBroadcastOption(computeQuery);
        ServiceDocumentQuery<ComputeState> query = new ServiceDocumentQuery<>(getHost(),
                ComputeState.class);
        List<String> containerHosts = new ArrayList<>();
        query.query(computeQuery, (r) -> {
            if (r.hasException()) {
                failTask("Failure retrieving query results", r.getException());
            } else if (r.hasResult()) {
                containerHosts.add(r.getDocumentSelfLink());
            } else {
                if (containerHosts.isEmpty()) {
                    logInfo(
                            "No available container enabled computes found to be removed with links: %s",
                            state.resourceLinks);
                    sendSelfPatch(
                            createUpdateSubStageTask(state, SubStage.REMOVED_CONTAINER_HOSTS));
                } else {
                    removeContainerHosts(state, containerHosts);
                }
            }
        });
    }

    private void removeContainerHosts(ComputeRemovalTaskState state, List<String> containerHosts) {
        ContainerHostRemovalTaskState hostRemovalState = new ContainerHostRemovalTaskState();
        hostRemovalState.resourceLinks = state.resourceLinks;
        hostRemovalState.skipComputeHostRemoval = true;
        boolean errorState = state.taskSubStage == SubStage.ERROR;
        hostRemovalState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.REMOVED_CONTAINER_HOSTS,
                TaskStage.FAILED, SubStage.ERROR);
        hostRemovalState.documentSelfLink = getSelfId();
        hostRemovalState.customProperties = state.customProperties;
        hostRemovalState.requestTrackerLink = state.requestTrackerLink;
        hostRemovalState.taskSubStage = ContainerHostRemovalTaskState.SubStage.SUSPENDED_HOSTS;

        sendRequest(Operation.createPost(this, ContainerHostRemovalTaskFactoryService.SELF_LINK)
                .setBody(hostRemovalState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failed to create container host removal operation task", ex);
                        return;
                    }
                    sendSelfPatch(
                            createUpdateSubStageTask(state, SubStage.REMOVING_CONTAINER_HOSTS));
                }));
    }

    private void disableComputeHosts(ComputeRemovalTaskState state) {
        ComputeState computeState = new ComputeState();
        computeState.powerState = PowerState.SUSPEND;

        try {
            List<Operation> operations = new ArrayList<>(state.resourceLinks.size());
            for (String resourceLink : state.resourceLinks) {
                operations.add(Operation
                        .createPatch(this, resourceLink)
                        .setBody(computeState));
            }
            OperationJoin.create(operations.toArray(new Operation[operations.size()]))
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            logInfo("Failed suspending ComputeStates");
                            logFine("Failed suspending ComputeStates, reason %s",
                                    Utils.toString(exs));
                            sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
                            return;
                        }
                        sendSelfPatch(createUpdateSubStageTask(state, SubStage.SUSPENDED_COMPUTES));
                    }).sendWith(this);
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.SUSPENDING_COMPUTES));
        } catch (Throwable e) {
            failTask("Unexpected exception while suspending container host", e);
        }
    }

    private void deallocateResources(ComputeRemovalTaskState state,
            ServiceTaskCallback taskCallback) {
        if (state.skipReleaseResourceQuota) {
            logFine("Skipping releasing quota");
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.DEALLOCATED_RESOURCES));
            return;
        }

        if (taskCallback == null) {
            createCounterSubTaskCallback(state, state.resourceLinks.size(), false,
                    SubStage.DEALLOCATED_RESOURCES,
                    (serviceTask) -> deallocateResources(state, serviceTask));
            return;
        }

        try {
            for (String resourceLink : state.resourceLinks) {
                sendRequest(Operation
                        .createGet(this, resourceLink)
                        .setCompletion(
                                (o, e) -> {
                                    if (e != null) {
                                        logWarning("Failed retrieving Compute State: "
                                                + resourceLink, e);
                                        completeSubTasksCounter(taskCallback, e);
                                        return;
                                    }
                                    ComputeState cs = o.getBody(ComputeState.class);

                                    releaseResourceQuota(state, cs, taskCallback);

                                }));
            }
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.DEALLOCATING_RESOURCES));
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting resources", e);
        }
    }

    private void releaseResourceQuota(ComputeRemovalTaskState state, ComputeState cs,
            ServiceTaskCallback taskCallback) {

        String groupResourcePolicyLink = cs.customProperties != null
                ? cs.customProperties.get(ComputeConstants.GROUP_RESOURCE_POLICY_LINK_NAME)
                : null;
        if (groupResourcePolicyLink == null) {
            logInfo("No policy was used for %s", cs.documentSelfLink);
            completeSubTasksCounter(taskCallback, null);
            return;
        }

        ReservationRemovalTaskState rsrvTask = new ReservationRemovalTaskState();
        rsrvTask.serviceTaskCallback = taskCallback;
        rsrvTask.resourceCount = 1;
        rsrvTask.resourceDescriptionLink = cs.descriptionLink;
        rsrvTask.groupResourcePolicyLink = groupResourcePolicyLink;
        rsrvTask.requestTrackerLink = state.requestTrackerLink;

        Operation.createPost(this, ReservationRemovalTaskFactoryService.SELF_LINK)
                .setBody(rsrvTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Quotas can't be cleaned up for: " + groupResourcePolicyLink, e);
                        completeSubTasksCounter(taskCallback, e);
                        return;
                    }
                    logInfo("Requested Quota removal for: %s", groupResourcePolicyLink);
                }).sendWith(this);
    }

    private void deleteResources(ComputeRemovalTaskState state) {
        try {
            logInfo("Starting delete of %d compute hosts", state.resourceLinks.size());
            Query resourceQuery = QueryUtil.addListValueClause(ComputeState.FIELD_NAME_SELF_LINK,
                    state.resourceLinks, MatchType.TERM);
            QuerySpecification qSpec = new QuerySpecification();
            qSpec.query = resourceQuery;

            ResourceRemovalTaskState removalServiceState = new ResourceRemovalTaskState();
            removalServiceState.documentSelfLink = getSelfId();
            removalServiceState.resourceQuerySpec = qSpec;
            // removalServiceState.options = EnumSet.of(TaskOption.DOCUMENT_CHANGES_ONLY);
            removalServiceState.isMockRequest = DeploymentProfileConfig.getInstance().isTest();
            removalServiceState.tenantLinks = state.tenantLinks;

            Operation
                    .createPost(
                            UriUtils.buildUri(getHost(), ResourceRemovalTaskService.FACTORY_LINK))
                    .setBody(removalServiceState)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Unable request provider resource removal for: "
                                    + state.resourceLinks, e);
                            return;
                        }
                        sendSelfPatch(createUpdateSubStageTask(state, SubStage.REMOVING_COMPUTES));
                    })
                    .sendWith(this);

        } catch (Throwable e) {
            failTask("Unexpected exception while deleting compute instances", e);
        }
    }

    private void subscribeToResourceRemovalTask(ComputeRemovalTaskState state) {
        Consumer<Operation> onSuccess = (op) -> {
            if (!op.hasBody()) {
                return;
            }
            ResourceRemovalTaskState deletionState = op.getBody(ResourceRemovalTaskState.class);
            if (TaskState.isFinished(deletionState.taskInfo)) {
                ComputeRemovalTaskState body = createUpdateSubStageTask(state,
                        SubStage.COMPLETED);
                sendSelfPatch(body);
            } else if (TaskState.isCancelled(deletionState.taskInfo) ||
                    TaskState.isFailed(deletionState.taskInfo)) {
                failTask("Fail to delete resources",
                        new IllegalStateException(deletionState.failureMessage));
            }
        };

        logInfo("Subscribing to ResourceRemovalTask, waiting for removal of %d compute hosts",
                state.resourceLinks.size());
        SubscriptionUtils.subscribeToNotifications(this, onSuccess,
                UriUtils.buildUriPath(ResourceRemovalTaskService.FACTORY_LINK, getSelfId()));
    }

    @Override
    protected boolean validateStageTransition(Operation patch, ComputeRemovalTaskState patchBody,
            ComputeRemovalTaskState currentState) {
        return false;
    }
}
