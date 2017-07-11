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

import static com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest.deallocationRequest;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

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
import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService.ProvisionSecurityGroupTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService.ProvisionSubnetTaskState;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

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
            SUBNET_INSTANCES_REMOVING,
            SUBNET_INSTANCES_REMOVED,
            SECURITY_GROUP_INSTANCES_REMOVING,
            SECURITY_GROUP_INSTANCES_REMOVED,
            REMOVING_RESOURCE_STATES,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(SUBNET_INSTANCES_REMOVING, SECURITY_GROUP_INSTANCES_REMOVING,
                            REMOVING_RESOURCE_STATES));
        }

        /**
         * (Required) The resources on which the given operation will be applied
         */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;
    }

    private static class Context {
        public Context(ComputeNetwork computeNetwork, ServiceTaskCallback<SubStage> serviceTaskCallback) {
            this.computeNetwork = computeNetwork;
            this.serviceTaskCallback = serviceTaskCallback;
        }

        ComputeNetwork computeNetwork;
        ServiceTaskCallback<SubStage> serviceTaskCallback;
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
                removeSubnetInstances(state, computeNetworks);
                break;
            case SUBNET_INSTANCES_REMOVING:
                break;
            case SUBNET_INSTANCES_REMOVED:
                removeSecurityGroupInstances(state, computeNetworks);
                break;
            case SECURITY_GROUP_INSTANCES_REMOVING:
                break;
            case SECURITY_GROUP_INSTANCES_REMOVED:
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

    private void  removeSubnetInstances(ComputeNetworkRemovalTaskState state,
            List<ComputeNetwork> computeNetworks) {
        if (computeNetworks == null) {
            queryComputeNetworkResources(state, networks ->
                    removeSubnetInstances(state, networks));
            return;
        }

        List<String> networkNames = computeNetworks.stream().map(cn -> cn.name)
                .collect(Collectors.toList());
        logInfo("Removing networks with names: ", networkNames);

        // Check if subnets should be deleted
        List<ComputeNetwork> isolatedComputeNetworks = computeNetworks.stream()
                .filter(n -> n.networkType == NetworkType.ISOLATED && n.subnetLink != null)
                .collect(Collectors.toList());
        if (isolatedComputeNetworks.size() == 0) {
            proceedTo(SubStage.SUBNET_INSTANCES_REMOVED);
            return;
        }

        deleteSubnets(isolatedComputeNetworks);
    }

    private void deleteSubnets(List<ComputeNetwork> isolatedComputeNetworks) {
        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback.create(getUri());
        callback.onSuccessTo(SubStage.SUBNET_INSTANCES_REMOVED);
        callback.onErrorTo(SubStage.ERROR);

        List<String> networkNames = computeNetworks.stream().map(cn -> cn.name)
                .collect(Collectors.toList());
        logInfo("Deleting subnets for isolated networks with names: ", networkNames);

        try {
            isolatedComputeNetworks.forEach(isolatedComputeNetwork ->
                    DeferredResult.completed(new Context(isolatedComputeNetwork, callback))
                            .thenCompose(this::populateSubnet)
                            .thenCompose(this::populateCIDRAllocationService)
                            .thenCompose(this::destroySubnet)
                            .thenCompose(this::deallocateSubnet)
                            .exceptionally(e -> {
                                if (e.getCause() != null && e.getCause()
                                        instanceof ServiceHost.ServiceNotFoundException) {
                                    logWarning("Subnet State is not found at link: %s",
                                            isolatedComputeNetwork.subnetLink);
                                    callback.sendResponse(this, (Throwable) null);
                                } else {
                                    callback.sendResponse(this, e);
                                }
                                return null;
                            })
            );
            proceedTo(SubStage.SUBNET_INSTANCES_REMOVING);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting subnets", e);
        }
    }

    private DeferredResult<Context> populateSubnet(Context context) {
        return this.sendWithDeferredResult(
                Operation.createGet(this, context.computeNetwork.subnetLink), SubnetState.class)
                .thenApply(subnetState -> {
                    context.subnet = subnetState;
                    return context;
                });
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
        provisionTaskState.subnetLink = context.subnet.documentSelfLink;

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
                    context.serviceTaskCallback.sendResponse(this, (Throwable) null);
                    return DeferredResult.completed(context);
                });
    }

    private void removeSecurityGroupInstances(ComputeNetworkRemovalTaskState state,
            List<ComputeNetwork> computeNetworks) {
        if (computeNetworks == null) {
            queryComputeNetworkResources(state, networks ->
                    removeSecurityGroupInstances(state, networks));
            return;
        }

        List<ComputeNetwork> isolatedNetworks = computeNetworks.stream()
                .filter(n -> n.networkType == NetworkType.ISOLATED && (
                        n.securityGroupLinks != null && !n.securityGroupLinks.isEmpty()))
                .collect(Collectors.toList());

        if (isolatedNetworks.isEmpty()) {
            proceedTo(SubStage.SECURITY_GROUP_INSTANCES_REMOVED);
            return;
        }

        List<String> networkNames = isolatedNetworks.stream().map(cn -> cn.name)
                .collect(Collectors.toList());
        logInfo("Removing security group instances for networks with names: ", networkNames);
        deleteSecurityGroups(isolatedNetworks, state.tenantLinks);
    }

    private void deleteSecurityGroups(List<ComputeNetwork> isolatedComputeNetworks,
            List<String> tenantLinks) {
        try {
            DeferredResult.allOf(
                    isolatedComputeNetworks.stream()
                            .map(cn -> getSecurityGroupLinks(cn))
                            .collect(Collectors.toList()))
                    .thenApply(allSecurityGroupLinks -> {
                        Set<String> uniqueSecurityGroupLinks = new HashSet<>();
                        return allSecurityGroupLinks.stream()
                                .filter(securityGroupLinks -> !securityGroupLinks.isEmpty())
                                .flatMap(securityGroups ->
                                        securityGroups.stream().filter(
                                                sgLink -> uniqueSecurityGroupLinks.add(sgLink)
                                        )).collect(Collectors.toSet());
                    })
                    .thenCompose(securityGroupLinks ->
                            destroySecurityGroups(securityGroupLinks, tenantLinks));
            proceedTo(SubStage.SECURITY_GROUP_INSTANCES_REMOVING);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting security groups", e);
        }
    }

    private DeferredResult<Set<String>> getSecurityGroupLinks(ComputeNetwork computeNetwork) {
        // Find all SecurityGroups that are associated to and have the same context id as this
        // network
        String contextId = computeNetwork.customProperties != null ?
                computeNetwork.customProperties.get(FIELD_NAME_CONTEXT_ID_KEY) : null;

        if (StringUtils.isBlank(contextId)) {
            return DeferredResult.completed(Collections.emptySet());
        }

        Set<String> securityGroups = new HashSet<>();
        Builder builder = Builder.create()
                .addKindFieldClause(SecurityGroupState.class)
                .addCompositeFieldClause(SecurityGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                        FIELD_NAME_CONTEXT_ID_KEY, contextId)
                .addInClause(SecurityGroupState.FIELD_NAME_SELF_LINK,
                        computeNetwork.securityGroupLinks);
        QueryUtils.QueryByPages<SecurityGroupState> query = new QueryUtils.QueryByPages<>(
                getHost(),
                builder.build(), SecurityGroupState.class, computeNetwork.tenantLinks);

        return query.queryLinks(sg -> securityGroups.add(sg))
                .thenApply(v -> securityGroups);
    }

    private DeferredResult<Operation> destroySecurityGroups(Set<String> securityGroupLinks,
            List<String> tenantLinks) {
        if (securityGroupLinks.isEmpty()) {
            proceedTo(SubStage.SECURITY_GROUP_INSTANCES_REMOVED);
            return DeferredResult.completed(null);
        }

        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback.create(getUri());
        callback.onSuccessTo(SubStage.SECURITY_GROUP_INSTANCES_REMOVED);
        callback.onErrorTo(SubStage.ERROR);

        ProvisionSecurityGroupTaskState provisionTaskState = new ProvisionSecurityGroupTaskState();
        provisionTaskState.isMockRequest = DeploymentProfileConfig.getInstance().isTest();
        provisionTaskState.requestType = InstanceRequestType.DELETE;
        provisionTaskState.serviceTaskCallback = callback;
        provisionTaskState.tenantLinks = tenantLinks;
        provisionTaskState.documentExpirationTimeMicros = ServiceUtils
                .getDefaultTaskExpirationTimeInMicros();
        provisionTaskState.securityGroupDescriptionLinks = securityGroupLinks;

        return this.sendWithDeferredResult(
                Operation.createPost(this, ProvisionSecurityGroupTaskService.FACTORY_LINK)
                        .setBody(provisionTaskState));
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

        List<String> networkNames = computeNetworks.stream().map(cn -> cn.name)
                .collect(Collectors.toList());
        logInfo("Removing compute network states with names: ", networkNames);

        for (ComputeNetwork computeNetwork : computeNetworks) {
            Operation.createDelete(this, computeNetwork.documentSelfLink)
                    .setCompletion(
                            (o, e) -> {
                                if (e != null) {
                                    logWarning("Failed deleting ComputeNetworkState: %s. %s",
                                            computeNetwork.documentSelfLink, Utils.toString(e));
                                    return;
                                }
                                logInfo("Deleted ComputeNetworkState: %s",
                                        computeNetwork.documentSelfLink);

                                removeResourceGroups(computeNetwork.groupLinks,
                                        v -> completeSubTasksCounter(subTaskLink, null));
                            }).sendWith(this);
        }
        proceedTo(SubStage.REMOVING_RESOURCE_STATES);
    }

    private void removeResourceGroups(Set<String> groupLinks, Consumer<Void> callback) {
        if (groupLinks == null || groupLinks.size() == 0) {
            callback.accept(null);
            return;
        }

        DeferredResult.allOf(groupLinks.stream().map(
                groupLink -> this.sendWithDeferredResult(
                        Operation.createDelete(this, groupLink)
                )
        ).collect(Collectors.toList()))
                .whenComplete((all, t) -> {
                    if (t != null) {
                        logWarning("Failed deleting all ResourceGroupStates: %s. %s",
                                groupLinks, Utils.toString(t));
                    }
                    callback.accept(null);
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
                        this.logWarning("Unable to find CIDR allocation service for network: %s",
                                context.subnet.networkLink);
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
}
