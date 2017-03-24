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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.NetworkType;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.request.compute.ComputeNetworkRemovalTaskService.ComputeNetworkRemovalTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService.ProvisionSubnetTaskState;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Task implementing removal of Compute Networks.
 */
public class ComputeNetworkRemovalTaskService extends
        AbstractTaskStatefulService<ComputeNetworkRemovalTaskService.ComputeNetworkRemovalTaskState, ComputeNetworkRemovalTaskService.ComputeNetworkRemovalTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_NETWORK_REMOVAL_TASKS;

    public static final String DISPLAY_NAME = "Compute Network Removal";

    public static class ComputeNetworkRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeNetworkRemovalTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            INSTANCES_REMOVING,
            INSTANCES_REMOVED,
            REMOVING_RESOURCE_STATES,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(REMOVING_RESOURCE_STATES));
        }

        /** (Required) The resources on which the given operation will be applied */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;
    }

    public static class Context {
        public Context(ComputeNetwork computeNetwork, String subTaskLink) {
            this.computeNetwork = computeNetwork;
            this.subTaskLink = subTaskLink;
        }

        public ComputeNetwork computeNetwork;
        public String subTaskLink;
        public SubnetState subnet;
    }

    // cached compute networks
    private transient volatile List<ComputeNetwork> computeNetworks;

    public ComputeNetworkRemovalTaskService() {
        super(ComputeNetworkRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ComputeNetworkRemovalTaskState state) {
        try {
            switch (state.taskSubStage) {
            case CREATED:
                deleteResourceInstances(state, computeNetworks);
                break;
            case INSTANCES_REMOVING:
                break;
            case INSTANCES_REMOVED:
                removeComputeNetworkStates(state, computeNetworks, null);
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
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting compute networks", e);
        }
    }

    @Override
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> state) {
        TaskStatusState statusTask = super.fromTask(state);
        if (SubStage.COMPLETED == state.taskSubStage) {
            statusTask.name = NetworkOperationType
                    .extractDisplayName(NetworkOperationType.DELETE.id);
        }
        return statusTask;
    }

    private void deleteResourceInstances(ComputeNetworkRemovalTaskState state,
            List<ComputeNetwork> computeNetworks) {
        if (computeNetworks == null) {
            queryComputeNetworkResources(state, networks ->
                    deleteResourceInstances(state, networks));
            return;
        }

        // Check if subnets should be deleted
        List<ComputeNetwork> isolatedComputeNetworks = computeNetworks.stream()
                .filter(n -> n.networkType == NetworkType.ISOLATED && n.subnetLink != null)
                .collect(Collectors.toList());
        if (isolatedComputeNetworks.size() == 0) {
            proceedTo(SubStage.INSTANCES_REMOVED);
            return;
        }
        createCounterSubTask(state, isolatedComputeNetworks.size(),
                SubStage.INSTANCES_REMOVED,
                (subTaskLink) -> deleteSubnets(isolatedComputeNetworks, subTaskLink));
    }

    private void deleteSubnets(List<ComputeNetwork> isolatedComputeNetworks, String subTaskLink) {
        try {
            isolatedComputeNetworks.forEach(isolatedComputeNetwork -> {
                DeferredResult.completed(new Context(isolatedComputeNetwork, subTaskLink))
                        .thenCompose(this::populateSubnet)
                        .thenCompose(this::destroySubnet)
                        .exceptionally(e -> {
                            if (e.getCause() != null && e.getCause()
                                    instanceof ServiceHost.ServiceNotFoundException) {
                                logWarning("Subnet State is not found at link: %s ",
                                        isolatedComputeNetwork.subnetLink);
                                completeSubTasksCounter(subTaskLink, null);
                            } else {
                                completeSubTasksCounter(subTaskLink, e);
                            }
                            return null;
                        });
            });
            proceedTo(SubStage.INSTANCES_REMOVING);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting subnets", e);
        }
    }

    private DeferredResult<Context> destroySubnet(Context context) {
        // If there is not instanceAdapterReference, subnet was not provisioned by the adapter
        // Ex: deployment provisioning failed after network allocated, but before provisioned
        if (context.subnet.instanceAdapterReference == null) {
            return removeSubnetState(context);
        }

        ProvisionSubnetTaskState provisionTaskState = new ProvisionSubnetTaskState();
        boolean isMockRequest = DeploymentProfileConfig.getInstance().isTest();
        if (isMockRequest) {
            provisionTaskState.options = EnumSet.of(TaskOption.IS_MOCK);
        }
        provisionTaskState.requestType = SubnetInstanceRequest.InstanceRequestType.DELETE;
        provisionTaskState.parentTaskLink = context.subTaskLink;
        provisionTaskState.tenantLinks = context.computeNetwork.tenantLinks;
        provisionTaskState.documentExpirationTimeMicros = ServiceUtils
                .getDefaultTaskExpirationTimeInMicros();
        provisionTaskState.subnetDescriptionLink = context.subnet.documentSelfLink;

        return this.sendWithDeferredResult(
                Operation.createPost(this, ProvisionSubnetTaskService.FACTORY_LINK)
                        .setBody(provisionTaskState))
                .thenApply(op -> context);
    }

    private DeferredResult<Context> removeSubnetState(Context context) {
        return this.sendWithDeferredResult(
                Operation.createDelete(this, context.subnet.documentSelfLink))
                .thenCompose(o -> {
                    completeSubTasksCounter(context.subTaskLink, null);
                    return DeferredResult.completed(context);
                });
    }

    private void removeComputeNetworkStates(ComputeNetworkRemovalTaskState state,
            List<ComputeNetwork> computeNetworks, String subTaskLink) {
        if (subTaskLink == null) {
            createCounterSubTask(state, computeNetworks.size(),
                    (link) -> removeComputeNetworkStates(state, computeNetworks, link));
            return;
        }

        if (computeNetworks == null) {
            queryComputeNetworkResources(state, networks ->
                    removeComputeNetworkStates(state, networks, subTaskLink));
            return;
        }

        for (ComputeNetwork computeNetwork : computeNetworks) {
            Operation.createDelete(this, computeNetwork.documentSelfLink)
                    .setCompletion(
                            (o, e) -> {
                                if (e != null) {
                                    logWarning("Failed deleting ComputeNetworkState: "
                                            + computeNetwork.documentSelfLink, e);
                                    return;
                                }
                                logInfo("Deleted ComputeNetworkState: "
                                        + computeNetwork.documentSelfLink);
                                completeSubTasksCounter(subTaskLink, null);
                            }).sendWith(this);
        }
        proceedTo(SubStage.REMOVING_RESOURCE_STATES);
    }

    private DeferredResult<Context> populateSubnet(Context context) {
        return this.sendWithDeferredResult(
                Operation.createGet(this, context.computeNetwork.subnetLink), SubnetState.class)
                .thenApply(subnetState -> {
                    context.subnet = subnetState;
                    return context;
                });
    }

    private void queryComputeNetworkResources(ComputeNetworkRemovalTaskState state,
            Consumer<List<ComputeNetwork>> callbackFunction) {
        if (this.computeNetworks != null) {
            callbackFunction.accept(this.computeNetworks);
            return;
        }

        List<ComputeNetwork> computeNetworks = new ArrayList<>();
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeNetwork.class)
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, state.resourceLinks);

        QueryByPages<ComputeNetwork> query = new QueryByPages<>(getHost(), builder.build(),
                ComputeNetwork.class, state.tenantLinks);
        query.queryDocuments(computeNetwork -> computeNetworks.add(computeNetwork))
                .whenComplete(((v, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving query results", e);
                        return;
                    }

                    if (computeNetworks.isEmpty()) {
                        logWarning("No available resources found to be removed with links: %s",
                                state.resourceLinks);
                        proceedTo(SubStage.COMPLETED);
                    } else {
                        this.computeNetworks = computeNetworks;
                        callbackFunction.accept(this.computeNetworks);
                    }
                }));
    }
}
