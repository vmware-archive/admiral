/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.compute;

import static com.vmware.xenon.common.Service.Action.PATCH;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.common.util.SubscriptionUtils;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.request.ContainerHostRemovalTaskFactoryService;
import com.vmware.admiral.request.ContainerHostRemovalTaskService.ContainerHostRemovalTaskState;
import com.vmware.admiral.request.ReservationRemovalTaskFactoryService;
import com.vmware.admiral.request.ReservationRemovalTaskService.ReservationRemovalTaskState;
import com.vmware.admiral.request.compute.ComputeRemovalTaskService.ComputeRemovalTaskState.SubStage;
import com.vmware.admiral.request.utils.EventTopicUtils;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.EventTopicDeclarator;
import com.vmware.admiral.service.common.EventTopicService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.data.SchemaBuilder;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
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
        AbstractTaskStatefulService<ComputeRemovalTaskService.ComputeRemovalTaskState, ComputeRemovalTaskService.ComputeRemovalTaskState.SubStage>
        implements EventTopicDeclarator {

    public static final String DISPLAY_NAME = "Compute Removal";

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_REMOVAL_OPEARTIONS;

    public static class ComputeRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeRemovalTaskState.SubStage> {

        @Documentation(description = "The compute resources to be removed")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

        @Documentation(description = "Optional resource removal options")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public EnumSet<TaskOption> resourceRemovalOptions;

        @Documentation(description = "Whether to skip the associated reservation or not")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public boolean skipReleaseResourceQuota;

        /**
         * (Set by a Task) Links to auto-generated placement zones that needs to be deleted after the
         * removal operation succeeds.
         */
        @Documentation(description = "Links to auto-generated placement zones that needs to "
                + "be deleted after the removal operation succeeds.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, SERVICE_USE, AUTO_MERGE_IF_NOT_NULL },
                indexing = STORE_ONLY)
        public List<String> deletePlacementZonesLinks;

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

            static final Set<SubStage> SUBSCRIPTION_SUB_STAGES = new HashSet<>(
                    Arrays.asList(REMOVED_CONTAINER_HOSTS));
        }
    }

    public ComputeRemovalTaskService() {
        super(ComputeRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
        super.subscriptionSubStages = EnumSet.copyOf(SubStage.SUBSCRIPTION_SUB_STAGES);
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
            complete();
            break;
        case ERROR:
            completeWithError();
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
                    logInfo("No available container enabled computes found to be removed with"
                                    + " links: %s", state.resourceLinks);
                    proceedTo(SubStage.REMOVED_CONTAINER_HOSTS);
                } else {
                    queryContainerHostsAutoGeneratedZonesLinks(state, (deleteZonesLinks) -> {
                        removeContainerHosts(state, containerHosts, deleteZonesLinks);
                    });
                }
            }
        });
    }

    private void removeContainerHosts(ComputeRemovalTaskState state, List<String> containerHosts,
            List<String> deleteZonesLinks) {
        ContainerHostRemovalTaskState hostRemovalState = new ContainerHostRemovalTaskState();
        hostRemovalState.resourceLinks = new HashSet<>(containerHosts);
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
                    proceedTo(SubStage.REMOVING_CONTAINER_HOSTS, (patchState) -> {
                        patchState.deletePlacementZonesLinks = deleteZonesLinks;
                    });
                }));
    }

    private void disableComputeHosts(ComputeRemovalTaskState state) {
        ComputeState computeState = new ComputeState();
        computeState.powerState = PowerState.SUSPEND;
        computeState.lifecycleState = LifecycleState.SUSPEND;

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

                            if (state.customProperties != null && Boolean.TRUE.toString().equals(
                                    state.customProperties
                                            .get(RequestUtils.FIELD_NAME_DEALLOCATION_REQUEST))) {
                                proceedTo(SubStage.COMPLETED);
                                return;
                            }

                            failTask("Unexpected exception while suspending container host",
                                    new Throwable(Utils.toString(exs)));
                            return;
                        }
                        proceedTo(SubStage.SUSPENDED_COMPUTES);
                    }).sendWith(this);
            proceedTo(SubStage.SUSPENDING_COMPUTES);
        } catch (Throwable e) {
            failTask("Unexpected exception while suspending container host", e);
        }
    }

    private void deallocateResources(ComputeRemovalTaskState state,
            ServiceTaskCallback taskCallback) {
        if (state.skipReleaseResourceQuota) {
            logFine("Skipping releasing quota");
            proceedTo(SubStage.DEALLOCATED_RESOURCES);
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
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                logWarning("Failed retrieving Compute State: %s. Error: %s",
                                        resourceLink, Utils.toString(e));
                                completeSubTasksCounter(taskCallback, e);
                                return;
                            }
                            ComputeState cs = o.getBody(ComputeState.class);

                            releaseResourceQuota(state, cs, taskCallback);
                        }));
            }
            proceedTo(SubStage.DEALLOCATING_RESOURCES);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting resources", e);
        }
    }

    private void releaseResourceQuota(ComputeRemovalTaskState state, ComputeState cs,
            ServiceTaskCallback taskCallback) {

        String groupResourcePlacementLink = cs.customProperties != null
                ? cs.customProperties.get(ComputeConstants.GROUP_RESOURCE_PLACEMENT_LINK_NAME)
                : null;
        if (groupResourcePlacementLink == null) {
            logInfo("Placement was not used for %s", cs.documentSelfLink);
            completeSubTasksCounter(taskCallback, null);
            return;
        }

        ReservationRemovalTaskState rsrvTask = new ReservationRemovalTaskState();
        rsrvTask.serviceTaskCallback = taskCallback;
        rsrvTask.resourceCount = 1;
        rsrvTask.resourceDescriptionLink = cs.descriptionLink;
        rsrvTask.groupResourcePlacementLink = groupResourcePlacementLink;
        rsrvTask.requestTrackerLink = state.requestTrackerLink;

        Operation.createPost(this, ReservationRemovalTaskFactoryService.SELF_LINK)
                .setBody(rsrvTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Quotas can't be cleaned up for: %s. Error: %s",
                                groupResourcePlacementLink, Utils.toString(e));
                        completeSubTasksCounter(taskCallback, e);
                        return;
                    }
                    logInfo("Requested Quota removal for: %s", groupResourcePlacementLink);
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
            removalServiceState.options = state.resourceRemovalOptions;
            removalServiceState.documentExpirationTimeMicros = ServiceUtils
                    .getDefaultTaskExpirationTimeInMicros();
            removalServiceState.isMockRequest = DeploymentProfileConfig.getInstance().isTest();
            removalServiceState.tenantLinks = state.tenantLinks;

            Operation
                    .createPost(this, ResourceRemovalTaskService.FACTORY_LINK)
                    .setBody(removalServiceState)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Unable request provider resource removal for: "
                                    + state.resourceLinks, e);
                            return;
                        }

                        proceedTo(SubStage.REMOVING_COMPUTES);
                    })
                    .sendWith(this);

        } catch (Throwable e) {
            failTask("Unexpected exception while deleting compute instances", e);
        }
    }

    /**
     * Builds a list of the document links of all auto-generated placement zones for the container
     * hosts that are being removed. On success, the <code>completionHandler</code> is called with
     * the built list as an argument. On failure the completionHandler is called with a
     * <code>null</code> argument (the removal task is never failed).
     */
    private void queryContainerHostsAutoGeneratedZonesLinks(ComputeRemovalTaskState state,
            Consumer<List<String>> completionHandler) {
        QueryTask queryTask = QueryUtil.buildPropertyQuery(ComputeState.class,
                QuerySpecification.buildCompositeFieldName(
                        ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeConstants.AUTOGENERATED_PLACEMENT_ZONE_PROP_NAME),
                Boolean.toString(true),
                QuerySpecification.buildCompositeFieldName(
                        ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME),
                Boolean.toString(true));
        QueryUtil.addListValueClause(queryTask, ComputeState.FIELD_NAME_SELF_LINK,
                state.resourceLinks);
        QueryUtil.addExpandOption(queryTask);

        HashSet<String> generatedPzLinks = new HashSet<>();
        new ServiceDocumentQuery<ComputeState>(getHost(), ComputeState.class).query(queryTask,
                (r) -> {
                    if (r.hasException()) {
                        // Do not fail the removal because of a failed query
                        logWarning("Failed to query for auto-generated placement zones: %s",
                                Utils.toString(r.getException()));
                        completionHandler.accept(null);
                    } else if (r.hasResult()) {
                        ComputeState hostState = r.getResult();
                        if (hostState.resourcePoolLink != null) {
                            generatedPzLinks.add(hostState.resourcePoolLink);
                        }
                    } else {
                        completionHandler.accept(new ArrayList<>(generatedPzLinks));
                    }
                });
    }

    private void subscribeToResourceRemovalTask(ComputeRemovalTaskState state) {
        String taskLink = UriUtils
                .buildUriPath(ResourceRemovalTaskService.FACTORY_LINK, getSelfId());
        Consumer<Operation> notificationTarget = (op) -> {
            // We only care about listening to PATCH updates...
            if (!op.hasBody() || !PATCH.equals(op.getAction())) {
                op.complete();
                return;
            }

            ResourceRemovalTaskState deletionState = op.getBody(ResourceRemovalTaskState.class);
            op.complete();
            if (TaskState.isFinished(deletionState.taskInfo)) {
                logInfo("ResourceRemoval Task completed successfully: %s",
                        deletionState.documentSelfLink);
                SubscriptionUtils.unsubscribeNotifications(getHost(), taskLink, op.getUri());
                removeAutoGeneratedPlacementZones(state);
            } else if (TaskState.isCancelled(deletionState.taskInfo) ||
                    TaskState.isFailed(deletionState.taskInfo)) {
                SubscriptionUtils.unsubscribeNotifications(getHost(), taskLink, op.getUri());
                failTask("Fail to delete resources",
                        new IllegalStateException(deletionState.failureMessage));
            }
        };

        logInfo("Subscribing to ResourceRemovalTask, waiting for removal of %d compute hosts",
                state.resourceLinks.size());

        SubscriptionUtils.subscribeToNotifications(getHost(), notificationTarget,
                e -> failTask("Error subscribing for resource removal", e), taskLink);

    }

    private void removeAutoGeneratedPlacementZones(ComputeRemovalTaskState state) {
        List<String> toDeleteZonesLinks = state.deletePlacementZonesLinks;
        if (toDeleteZonesLinks == null || toDeleteZonesLinks.isEmpty()) {
            proceedTo(SubStage.COMPLETED);
            return;
        }

        List<Operation> deleteOps = toDeleteZonesLinks.stream().map((link) -> {
            return createDeleteOperationForAutogeneratedResource(UriUtils.buildUriPath(
                    ElasticPlacementZoneConfigurationService.SELF_LINK, link));
        }).collect(Collectors.toList());

        ArrayList<String> deletedPlacementZonesLinks = new ArrayList<>();
        OperationJoin.create(deleteOps)
                .setCompletion((ops, ers) -> {
                    ops.forEach((key, op) -> {
                        // build a list of the successfully deleted placement zones
                        if (ers == null || !ers.containsKey(key)) {
                            String deletedZoneLink = op.getUri().getPath().substring(
                                    ElasticPlacementZoneConfigurationService.SELF_LINK.length());
                            deletedPlacementZonesLinks.add(deletedZoneLink);
                        }
                    });

                    removeAutoGeneratedPlacements(deletedPlacementZonesLinks);

                }).sendWith(getHost());
    }

    private void removeAutoGeneratedPlacements(List<String> deletedPlacementZonesLinks) {
        if (deletedPlacementZonesLinks == null || deletedPlacementZonesLinks.isEmpty()) {
            proceedTo(SubStage.COMPLETED);
            return;
        }

        // Select all placement zones for container hosts that were linked to a now deleted
        // placement zone and are marked for automatic removal
        QueryTask queryTask = QueryUtil.buildPropertyQuery(GroupResourcePlacementState.class,
                GroupResourcePlacementState.FIELD_NAME_RESOURCE_TYPE,
                ResourceType.CONTAINER_TYPE.getName(),
                QuerySpecification.buildCompositeFieldName(
                        GroupResourcePlacementState.FIELD_NAME_CUSTOM_PROPERTIES,
                        GroupResourcePlacementState.AUTOGENERATED_PLACEMENT_PROP_NAME),
                Boolean.toString(true));
        QueryUtil.addListValueClause(queryTask,
                GroupResourcePlacementState.FIELD_NAME_RESOURCE_POOL_LINK,
                deletedPlacementZonesLinks);

        ArrayList<Operation> deleteOps = new ArrayList<>();
        new ServiceDocumentQuery<>(getHost(), GroupResourcePlacementState.class).query(queryTask,
                (r) -> {
                    if (r.hasException()) {

                        logWarning("Could not query placements that were marked for automatic"
                                        + " removal: %s", Utils.toString(r.getException()));
                        // do not fail the compute removal task

                    } else if (r.hasResult()) {

                        Operation delete = createDeleteOperationForAutogeneratedResource(
                                r.getDocumentSelfLink());
                        deleteOps.add(delete);

                    } else {

                        if (deleteOps.isEmpty()) {
                            // nothing to delete, complete the task
                            proceedTo(SubStage.COMPLETED);
                            return;
                        }

                        // delete all selected placements
                        OperationJoin.create(deleteOps)
                                .setCompletion((ops, ers) -> {
                                    // ignore results and failures and complete the task
                                    proceedTo(SubStage.COMPLETED);
                                }).sendWith(getHost());

                    }
                });
    }

    private Operation createDeleteOperationForAutogeneratedResource(String documentLink) {
        return Operation.createDelete(this, documentLink)
                .setReferer(getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Could not delete auto-generated resource %s: %s",
                                documentLink, Utils.toString(e));
                        // do not fail the compute removal task
                    } else {
                        logFine("Successfully deleted auto-generated resource %s", documentLink);
                    }
                });
    }

    @Override
    protected Collection<String> getRelatedResourcesLinks(ComputeRemovalTaskState state) {
        return state.resourceLinks;
    }

    @Override
    protected Class<? extends ResourceState> getRelatedResourceStateType(ComputeRemovalTaskState state) {
        return ComputeState.class;
    }

    protected static class ExtensibilityCallbackResponse extends BaseExtensibilityCallbackResponse {
    }

    @Override
    protected BaseExtensibilityCallbackResponse notificationPayload(ComputeRemovalTaskState state) {
        return new ExtensibilityCallbackResponse();
    }

    public static final String RESOURCE_REMOVAL_TOPIC_TASK_SELF_LINK =
            "compute-removal";
    public static final String RESOURCE_REMOVAL_TOPIC_ID = "com.vmware.compute.removal.pre";
    public static final String RESOURCE_REMOVAL_TOPIC_NAME = "Compute removal";
    public static final String RESOURCE_REMOVAL_TOPIC_TASK_DESCRIPTION = "Fired before a compute "
            + "resource is being destroyed";

    private void resourceRemovalEventTopic(ServiceHost host) {
        EventTopicService.TopicTaskInfo taskInfo = new EventTopicService.TopicTaskInfo();
        taskInfo.task = ComputeRemovalTaskState.class.getSimpleName();
        taskInfo.stage = TaskStage.STARTED.name();
        taskInfo.substage = SubStage.REMOVED_CONTAINER_HOSTS.name();

        EventTopicUtils.registerEventTopic(RESOURCE_REMOVAL_TOPIC_ID,
                RESOURCE_REMOVAL_TOPIC_NAME, RESOURCE_REMOVAL_TOPIC_TASK_DESCRIPTION,
                RESOURCE_REMOVAL_TOPIC_TASK_SELF_LINK, Boolean.TRUE,
                resourceRemovalTopicSchema(), taskInfo, host);
    }

    private SchemaBuilder resourceRemovalTopicSchema() {
        return new SchemaBuilder();//no special fields needed
    }

    @Override
    public void registerEventTopics(ServiceHost host) {
        resourceRemovalEventTopic(host);
    }
}