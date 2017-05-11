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

import static com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest.deallocationRequest;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationState;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.NetworkType;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.request.compute.ComputeNetworkRemovalTaskService.ComputeNetworkRemovalTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService.ProvisionSubnetTaskState;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Task implementing removal of Compute Networks.
 */
public class ComputeNetworkRemovalTaskService extends
        AbstractTaskStatefulService<ComputeNetworkRemovalTaskService.ComputeNetworkRemovalTaskState, ComputeNetworkRemovalTaskService.ComputeNetworkRemovalTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_NETWORK_REMOVAL_TASKS;

    public static final String DISPLAY_NAME = "Compute Network Removal";

    public static class ComputeNetworkRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeNetworkRemovalTaskState.SubStage> {

        public enum SubStage {
            CREATED,
            INSTANCES_REMOVING,
            INSTANCES_REMOVED,
            REMOVING_RESOURCE_STATES,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES =
                    Collections.singleton(REMOVING_RESOURCE_STATES);
        }

        /**
         * (Required) The resources on which the given operation will be applied
         */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;
    }

    private static class Context {
        public Context(ComputeNetwork computeNetwork, ServiceTaskCallback<?> serviceTaskCallback) {
            this.computeNetwork = computeNetwork;
            this.serviceTaskCallback = serviceTaskCallback;
        }

        ComputeNetwork computeNetwork;
        ServiceTaskCallback<?> serviceTaskCallback;
        SubnetState subnet;
        String cidrAllocationServiceLink;
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

        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback.create(getUri());
        callback.onSuccessTo(SubStage.INSTANCES_REMOVED);
        callback.onErrorTo(SubStage.ERROR);

        deleteSubnets(isolatedComputeNetworks, callback);
    }

    private void deleteSubnets(List<ComputeNetwork> isolatedComputeNetworks,
            ServiceTaskCallback<?> serviceTaskCallback) {
        try {
            isolatedComputeNetworks.forEach(isolatedComputeNetwork ->
                    DeferredResult.completed(new Context(isolatedComputeNetwork, serviceTaskCallback))
                            .thenCompose(this::populateSubnet)
                            .thenCompose(this::populateCIDRAllocationService)
                            .thenCompose(this::destroySubnet)
                            .thenCompose(this::deallocateSubnet)
                            .exceptionally(e -> {
                                if (e.getCause() != null && e.getCause()
                                        instanceof ServiceHost.ServiceNotFoundException) {
                                    logWarning("Subnet State is not found at link: %s ",
                                            isolatedComputeNetwork.subnetLink);
                                    completeSubTask(serviceTaskCallback, null);
                                } else {
                                    completeSubTask(serviceTaskCallback, e);
                                }
                                return null;
                            })
            );
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
        provisionTaskState.serviceTaskCallback = context.serviceTaskCallback;
        provisionTaskState.tenantLinks = context.computeNetwork.tenantLinks;
        provisionTaskState.documentExpirationTimeMicros = ServiceUtils
                .getDefaultTaskExpirationTimeInMicros();
        provisionTaskState.subnetDescriptionLink = context.subnet.documentSelfLink;

        return this.sendWithDeferredResult(
                Operation.createPost(this, ProvisionSubnetTaskService.FACTORY_LINK)
                        .setBody(provisionTaskState))
                .thenApply(op -> context);
    }

    private DeferredResult<Context> deallocateSubnet(Context context) {
        if (context.cidrAllocationServiceLink == null) {
            // No CIDR allocation service found.
            return DeferredResult.completed(context);
        }

        ComputeNetworkCIDRAllocationRequest request =
                deallocationRequest(context.subnet.id);

        return this.sendWithDeferredResult(
                Operation.createPatch(this, context.cidrAllocationServiceLink)
                        .setBody(request))
                .thenApply(op -> context);
    }

    private DeferredResult<Context> removeSubnetState(Context context) {
        return this.sendWithDeferredResult(
                Operation.createDelete(this, context.subnet.documentSelfLink))
                .thenCompose(o -> {
                    completeSubTask(context.serviceTaskCallback, null);
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

    private DeferredResult<Context> populateCIDRAllocationService(Context context) {
        AssertUtil.assertNotNull(context.subnet, "context.subnet");

        // Check if ComputeNetworkCIDRAllocationService exists for the isolated network.
        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeNetworkCIDRAllocationState.class)
                .addFieldClause(
                        ComputeNetworkCIDRAllocationState.FIELD_NAME_NETWORK_LINK,
                        context.subnet.networkLink)
                .build();

        QueryTop<ComputeNetworkCIDRAllocationState> queryCIDRAllocation =
                new QueryTop<>(this.getHost(),
                        query,
                        ComputeNetworkCIDRAllocationState.class,
                        QueryUtil.getTenantLinks(context.subnet.tenantLinks))
                        .setMaxResultsLimit(1);

        return queryCIDRAllocation.collectLinks(Collectors.toList())
                .thenApply(cidrAllocationLinks -> {
                    if (cidrAllocationLinks != null && cidrAllocationLinks.size() == 1) {
                        // Found existing CIDRAllocationService
                        context.cidrAllocationServiceLink = cidrAllocationLinks.get(0);
                    } else {
                        this.logWarning(() -> "Unable to find CIDR allocation service for "
                                + "network: " + context.subnet.networkLink);
                    }
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
        query.queryDocuments(computeNetworks::add)
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

    private void completeSubTask(ServiceTaskCallback taskCallback, Throwable ex) {
        ServiceTaskCallbackResponse response;
        if (ex == null) {
            response = taskCallback.getFinishedResponse();
        } else {
            response = taskCallback.getFailedResponse(ex);
        }

        sendRequest(Operation.createPatch(taskCallback.serviceURI)
                .setBody(response)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Notifying calling task failed: %s", e);
                    }
                }));
    }
}
