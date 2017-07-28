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

import static com.vmware.admiral.request.compute.ComputeNetworkProvisionTaskService.ComputeNetworkProvisionTaskState.SubStage.COMPLETED;
import static com.vmware.admiral.request.compute.ComputeNetworkProvisionTaskService.ComputeNetworkProvisionTaskState.SubStage.PROVISIONING;
import static com.vmware.admiral.request.compute.ComputeNetworkProvisionTaskService.ComputeNetworkProvisionTaskState.SubStage.UPDATED_CONNECTED_RESOURCES;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.NetworkType;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile.IsolationSupportType;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.admiral.request.compute.ComputeNetworkProvisionTaskService.ComputeNetworkProvisionTaskState.SubStage;
import com.vmware.admiral.request.compute.IsolatedSecurityGroupProvisionTaskService.IsolatedSecurityGroupProvisionTaskState;
import com.vmware.admiral.request.compute.IsolatedSubnetProvisionTaskService.IsolatedSubnetProvisionTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

/**
 * Task implementing the provisioning of a compute network.
 */
public class ComputeNetworkProvisionTaskService
        extends
        AbstractTaskStatefulService<ComputeNetworkProvisionTaskService.ComputeNetworkProvisionTaskState, ComputeNetworkProvisionTaskService.ComputeNetworkProvisionTaskState.SubStage> {

    public static final String FACTORY_LINK =
            ManagementUriParts.REQUEST_PROVISION_COMPUTE_NETWORK_TASKS;

    public static final String DISPLAY_NAME = "Compute Network Provision";

    public static class ComputeNetworkProvisionTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeNetworkProvisionTaskState.SubStage> {

        public enum SubStage {
            CREATED, START_PROVISIONING, PROVISIONING, UPDATED_CONNECTED_RESOURCES, COMPLETED, ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Collections.singletonList(PROVISIONING));
        }

        /**
         * (Required) The description that defines the requested resource.
         */
        @Documentation(description = "Type of resource to create.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String resourceDescriptionLink;

        /**
         * (Required) Number of resources to provision.
         */
        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Long resourceCount;

        /**
         * (Required) Links to already allocated resources that are going to be provisioned.
         */
        @Documentation(
                description = "Links to already allocated resources that are going to be provisioned.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public Set<String> resourceLinks;

    }

    public static class ConnectedResource {
        public ResourceState resource;
        public ResourceState description;
        public NetworkInterfaceDescription networkInterfaceDescription;

        public static ConnectedResource create(ResourceState resource, ResourceState description) {
            ConnectedResource result = new ConnectedResource();
            result.resource = resource;
            result.description = description;
            return result;
        }
    }

    private static class Context {
        public Context(String computeNetworkLink, ComputeNetworkProvisionTaskState state,
                ServiceTaskCallback serviceTaskCallback) {
            this.computeNetworkLink = computeNetworkLink;
            this.state = state;
            this.serviceTaskCallback = serviceTaskCallback;
        }

        public String computeNetworkLink;
        public ComputeNetwork computeNetwork;
        public ProfileStateExpanded profile;
        public SubnetState subnet;
        public SecurityGroupState isolationSecurityGroup;
        public ComputeNetworkDescription computeNetworkDescription;
        public List<ConnectedResource> connectedResources;
        public ComputeNetworkProvisionTaskState state;
        public ServiceTaskCallback serviceTaskCallback;
    }

    public ComputeNetworkProvisionTaskService() {
        super(ComputeNetworkProvisionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(ComputeNetworkProvisionTaskState state) {
        state.resourceCount = (long) state.resourceLinks.size();

        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.",
                    "request.resource-count.zero");
        }
    }

    @Override
    protected void handleStartedStagePatch(ComputeNetworkProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            provisionResources(state);
            break;
        case PROVISIONING:
            break;
        case UPDATED_CONNECTED_RESOURCES:
            updateResources(state);
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

    private void provisionResources(ComputeNetworkProvisionTaskState state) {
        try {
            Set<String> resourceLinks = state.resourceLinks;
            if (resourceLinks == null || resourceLinks.isEmpty()) {
                throw new LocalizableValidationException(
                        "No compute network instances to provision",
                        "request.compute.network.provision.empty");
            }
            ServiceTaskCallback callback = ServiceTaskCallback.create(getSelfLink(),
                    TaskState.TaskStage.STARTED, UPDATED_CONNECTED_RESOURCES,
                    TaskState.TaskStage.STARTED, SubStage.ERROR);

            resourceLinks.forEach(computeNetworkLink -> DeferredResult
                    .completed(new Context(computeNetworkLink, state, callback))
                    .thenCompose(this::populateContext)
                    .thenCompose(this::selectComputeNetwork)
                    .thenCompose(this::provisionResource)
                    .exceptionally(t -> {
                        logSevere("Failure provisioning a network: %s", Utils.toString(t));
                        failTask("Failure provisioning a network.", t);
                        return null;
                    }));

            logInfo("Requested provisioning of %s compute network resources.",
                    resourceLinks.size());
        } catch (Throwable e) {
            failTask("System failure creating SubnetStates", e);
        }
    }

    private void updateResources(ComputeNetworkProvisionTaskState state) {
        DeferredResult<List<Context>> request = DeferredResult.allOf(state.resourceLinks.stream()
                .map(computeNetworkLink -> DeferredResult
                        .completed(new Context(computeNetworkLink, state, null))
                        .thenCompose(this::populateContext)
                        .thenCompose(this::populateSubnet)
                        .thenCompose(this::populateSecurityGroup)
                        .thenCompose(this::configureConnectedResources)
                        .thenCompose(this::updateDeploymentResourceGroup))
                .collect(Collectors.toList()));
        request.whenComplete((all, ex) -> {
            if (ex != null) {
                failTask("Failed updating resources connected to compute network", ex);
                return;
            }
            proceedTo(COMPLETED);
        });
    }

    private DeferredResult<Context> populateContext(Context context) {
        return DeferredResult.completed(context)
                .thenCompose(this::populateComputeNetwork)
                .thenCompose(this::populateComputeNetworkDescription)
                .thenCompose(this::populateProfile)
                .thenCompose(this::populateConnectedResources);
    }

    private DeferredResult<Context> provisionResource(Context context) {
        if (context.computeNetwork.networkType == NetworkType.ISOLATED &&
                context.profile.networkProfile.isolationType == IsolationSupportType.SUBNET) {
            // Provision a new subnet
            IsolatedSubnetProvisionTaskState taskState = new IsolatedSubnetProvisionTaskState();
            taskState.resourceLink = context.computeNetworkLink;
            taskState.serviceTaskCallback = context.serviceTaskCallback;
            taskState.customProperties = context.state.customProperties;
            taskState.tenantLinks = context.state.tenantLinks;

            proceedTo(PROVISIONING);
            return this.sendWithDeferredResult(Operation
                    .createPost(this, IsolatedSubnetProvisionTaskService.FACTORY_LINK)
                    .setBody(taskState), IsolatedSubnetProvisionTaskState.class)
                    .thenApply(ctx -> context);
        } else if (context.computeNetwork.networkType == NetworkType.ISOLATED &&
                context.profile.networkProfile.isolationType == IsolationSupportType.SECURITY_GROUP) {
            // Provision a new security group
            IsolatedSecurityGroupProvisionTaskState taskState = new IsolatedSecurityGroupProvisionTaskState();
            taskState.resourceLink = context.computeNetworkLink;
            taskState.resourceDescriptionLink = context.state.resourceDescriptionLink;
            taskState.serviceTaskCallback = context.serviceTaskCallback;
            taskState.customProperties = context.state.customProperties;
            taskState.tenantLinks = context.state.tenantLinks;

            proceedTo(PROVISIONING);
            return this.sendWithDeferredResult(Operation
                    .createPost(this, IsolatedSecurityGroupProvisionTaskService.FACTORY_LINK)
                    .setBody(taskState), IsolatedSecurityGroupProvisionTaskState.class)
                    .thenApply(ctx -> context);
        } else {
            // No new resources need to be provisioned.
            proceedTo(UPDATED_CONNECTED_RESOURCES);
            return DeferredResult.completed(null);
        }
    }

    private DeferredResult<Context> populateComputeNetwork(Context context) {
        return this.sendWithDeferredResult(
                Operation.createGet(this, context.computeNetworkLink), ComputeNetwork.class)
                .thenApply(computeNetwork -> {
                    context.computeNetwork = computeNetwork;
                    return context;
                });
    }

    private DeferredResult<Context> populateComputeNetworkDescription(Context context) {
        return this.sendWithDeferredResult(
                Operation.createGet(this, context.computeNetwork.descriptionLink),
                ComputeNetworkDescription.class)
                .thenApply(cnd -> {
                    context.computeNetworkDescription = cnd;
                    return context;
                });
    }

    private DeferredResult<Context> populateProfile(Context context) {
        AssertUtil.assertNotNull(context.computeNetwork.provisionProfileLink,
                "Context.computeNetwork.provisionProfileLink should not be null");

        URI uri = UriUtils.buildUri(this.getHost(), context.computeNetwork.provisionProfileLink);
        uri = UriUtils.buildExpandLinksQueryUri(uri);

        return this.sendWithDeferredResult(Operation.createGet(uri), ProfileStateExpanded.class)
                .thenApply(profile -> {
                    context.profile = profile;
                    return context;
                });
    }

    private DeferredResult<Context> populateConnectedResources(Context context) {
        return DeferredResult.completed(context)
                .thenApply(ignore -> {
                    context.connectedResources = new ArrayList<>();
                    return context;
                })
                .thenCompose(this::populateComputeStates)
                .thenCompose(this::populateLoadBalancers);
    }

    private DeferredResult<Context> populateComputeStates(Context context) {
        // get all ComputeStates that have the same context id as this compute network
        Builder builder = Builder.create()
                .addKindFieldClause(ComputeState.class);
        builder.addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                FIELD_NAME_CONTEXT_ID_KEY, RequestUtils.getContextId(context.state));
        QueryUtils.QueryByPages<ComputeState> query = new QueryUtils.QueryByPages<>(
                getHost(),
                builder.build(), ComputeState.class, context.state.tenantLinks);

        // collect the ones connected to this network
        return query.collectDocuments(Collectors.toList())
                .thenCompose(computes -> DeferredResult.<ConnectedResource>allOf(
                        computes.stream().map(compute -> populateComputeState(context, compute))
                                .collect(Collectors.toList())))
                .thenApply(resources -> resources.stream()
                        .filter(r -> r.networkInterfaceDescription != null)
                        .collect(Collectors.toList()))
                .thenApply(resources -> {
                    context.connectedResources.addAll(resources);
                    return context;
                });
    }

    private DeferredResult<ConnectedResource> populateComputeState(Context context,
            ComputeState compute) {
        return getDocumentDR(compute.descriptionLink, ComputeDescription.class)
                .thenApply(cd -> ConnectedResource.create(compute, cd))
                .thenCompose(cr -> getDocumentsDR(
                        ((ComputeDescription) cr.description).networkInterfaceDescLinks,
                        NetworkInterfaceDescription.class).thenApply(nids -> nids.stream()
                        .filter(nid -> context.computeNetworkDescription.name != null
                                && context.computeNetworkDescription.name.equals(nid.name))
                        .collect(Collectors.toList())).thenApply(nids -> {
                            if (nids.size() > 1) {
                                throw new LocalizableValidationException(
                                        "Cannot have multiple NICs connected to the same network",
                                        "request.compute.network.provision.multiple-nics");
                            }
                            if (!nids.isEmpty()) {
                                cr.networkInterfaceDescription = nids.get(0);
                            }
                            return cr;
                        }
                ));
    }

    private DeferredResult<Context> populateLoadBalancers(Context context) {
        // get all load balancers that have the same context id as this compute network
        Builder builder = Builder.create()
                .addKindFieldClause(LoadBalancerState.class);
        builder.addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                FIELD_NAME_CONTEXT_ID_KEY, RequestUtils.getContextId(context.state));
        QueryUtils.QueryByPages<LoadBalancerState> query = new QueryUtils.QueryByPages<>(
                getHost(),
                builder.build(), LoadBalancerState.class, context.state.tenantLinks);

        return query.collectDocuments(Collectors.toList())
                .thenCompose(lbs -> DeferredResult.<ConnectedResource>allOf(
                        lbs.stream().map(lb -> populateLoadBalancer(context, lb))
                                .collect(Collectors.toList())))
                .thenApply(resources -> {
                    context.connectedResources.addAll(resources);
                    return context;
                });
    }

    private DeferredResult<ConnectedResource> populateLoadBalancer(Context context,
            LoadBalancerState loadBalancer) {
        return getDocumentDR(loadBalancer.descriptionLink, LoadBalancerDescription.class)
                .thenApply(lbd -> ConnectedResource.create(loadBalancer, lbd));
    }

    private DeferredResult<Context> populateSecurityGroup(Context context) {
        AssertUtil.assertNotNull(context.computeNetwork,
                "Context.computeNetwork should not be null.");

        if (context.computeNetwork.securityGroupLinks == null
                || context.computeNetwork.securityGroupLinks.isEmpty()) {
            return DeferredResult.completed(context);
        }

        AssertUtil.assertTrue(context.computeNetwork.securityGroupLinks.size() == 1,
                "Context.computeNetwork.securityGroupLinks should 1.");

        return this.sendWithDeferredResult(
                Operation.createGet(this,
                        context.computeNetwork.securityGroupLinks.iterator().next()),
                SecurityGroupState.class)
                .thenApply(securityGroup -> {
                    context.isolationSecurityGroup = securityGroup;
                    return context;
                });
    }

    private DeferredResult<Context> populateSubnet(Context context) {
        AssertUtil.assertNotNull(context.computeNetwork,
                "Context.computeNetwork should not be null.");

        // Subnet will not be there if this is external compute network
        // that is not connected to any computes
        if (context.computeNetwork.subnetLink == null) {
            return DeferredResult.completed(context);
        }

        return this.sendWithDeferredResult(
                Operation.createGet(this, context.computeNetwork.subnetLink), SubnetState.class)
                .thenApply(subnetState -> {
                    context.subnet = subnetState;
                    return context;
                });
    }

    private DeferredResult<Context> selectComputeNetwork(Context context) {
        if (context.connectedResources == null || context.connectedResources.isEmpty()) {
            // there are no resources attached to this network
            return DeferredResult.completed(context);
        }
        if (context.computeNetwork.networkType == NetworkType.ISOLATED &&
                context.profile.networkProfile.isolationType == IsolationSupportType.SUBNET) {
            // subnet is not provisioned yet
            return DeferredResult.completed(context);
        }

        return NetworkProfileQueryUtils
                .selectSubnet(getHost(), UriUtils.buildUri(getHost(), getSelfLink()),
                        context.state.tenantLinks, context.profile.endpointLink,
                        context.connectedResources.get(0).description.regionId,
                        context.connectedResources.get(0).networkInterfaceDescription,
                        context.profile,
                        context.computeNetwork, context.computeNetworkDescription,
                        context.subnet, false)
                .thenCompose(subnetState -> {
                    context.computeNetwork.subnetLink = subnetState.documentSelfLink;
                    return this.sendWithDeferredResult(Operation
                            .createPatch(this, context.computeNetwork.documentSelfLink)
                            .setBody(context.computeNetwork))
                            .thenCompose(op -> {
                                context.computeNetwork = op.getBody(ComputeNetwork.class);
                                return DeferredResult.completed(context);
                            });
                });
    }

    private DeferredResult<Context> configureConnectedResources(Context context) {
        if (context.connectedResources == null || context.connectedResources.isEmpty()) {
            // there are no resources attached to this network
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Operation>> patchOps = context.connectedResources.stream()
                .map(cr -> NetworkProfileQueryUtils
                        .selectSubnet(getHost(), UriUtils.buildUri(getHost(), getSelfLink()),
                                context.state.tenantLinks, context.profile.endpointLink,
                                cr.description.regionId,
                                cr.networkInterfaceDescription, context.profile,
                                context.computeNetwork, context.computeNetworkDescription,
                                context.subnet, false)
                        .thenApply(subnetState -> context.subnet = subnetState)
                        .thenCompose(subnetState -> updateConnectedResourceWithSubnet(context, cr,
                                subnetState)))
                .collect(Collectors.toList());

        return DeferredResult.allOf(patchOps)
                .thenApply(ignore -> context)
                .whenComplete((v, e) -> {
                    if (e != null) {
                        failTask("Failure configuring connected resources", e);
                        return;
                    }
                });
    }

    private DeferredResult<Operation> updateConnectedResourceWithSubnet(Context context,
            ConnectedResource cr, SubnetState subnetState) {
        if (cr.resource instanceof ComputeState) {
            return NetworkProfileQueryUtils
                    .createNicState(subnetState, context.state.tenantLinks,
                            context.profile.endpointLink, (ComputeDescription) cr.description,
                            cr.networkInterfaceDescription, context.isolationSecurityGroup,
                            context.profile.networkProfile.securityGroupLinks)
                    .thenCompose(nic -> this.sendWithDeferredResult(Operation
                                    .createPost(this, NetworkInterfaceService.FACTORY_LINK).setBody(nic),
                            NetworkInterfaceState.class))
                    .thenCompose(nis ->
                            patchComputeState((ComputeState) cr.resource, nis.documentSelfLink));
        }

        if (cr.resource instanceof LoadBalancerState) {
            return patchLoadBalancerState(context, cr.resource.documentSelfLink,
                    subnetState.documentSelfLink);
        }

        throw new IllegalStateException(
                "Unexpected resource type: " + cr.resource.getClass().getCanonicalName());
    }

    private DeferredResult<Operation> patchComputeState(
            ComputeState computeState,
            String networkLink) {

        computeState.networkInterfaceLinks = Arrays.asList(networkLink);
        return this.sendWithDeferredResult(
                Operation.createPatch(this, computeState.documentSelfLink)
                        .setBody(computeState));
    }

    private DeferredResult<Operation> patchLoadBalancerState(Context context,
            String loadBalancerLink, String subnetLink) {
        List<String> securityGroupsToAdd = new ArrayList<>();
        if (context.isolationSecurityGroup != null) {
            securityGroupsToAdd.add(context.isolationSecurityGroup.documentSelfLink);
        }
        if (context.profile.networkProfile.securityGroupLinks != null) {
            securityGroupsToAdd.addAll(context.profile.networkProfile.securityGroupLinks);
        }

        LoadBalancerState patchBody = new LoadBalancerState();
        patchBody.subnetLinks = Collections.singleton(subnetLink);
        patchBody.securityGroupLinks = securityGroupsToAdd.isEmpty() ? null : securityGroupsToAdd;
        return this.sendWithDeferredResult(
                Operation.createPatch(this, loadBalancerLink).setBody(patchBody));
    }

    private <T extends ResourceState> DeferredResult<T> getDocumentDR(String link, Class<T> type) {
        return this.sendWithDeferredResult(Operation.createGet(this, link), type);
    }

    private <T extends ResourceState> DeferredResult<List<T>> getDocumentsDR(List<String> links,
            Class<T> type) {
        if (links == null) {
            return DeferredResult.completed(Collections.emptyList());
        }
        return DeferredResult.allOf(
                links.stream().map(link -> getDocumentDR(link, type))
                        .collect(Collectors.toList()));
    }

    /**
     * Add the __endpointLink to the list of custom properties of the deployment resource group.
     * This is done in order to ensure that this resource group gets deleted when the associated
     * endpoint is removed.
     *
     * @param context
     * @return
     */
    private DeferredResult<Context> updateDeploymentResourceGroup(Context context) {
        AssertUtil.assertNotNull(context.profile.endpoint,
                "Context.profile.endpoint should not be null.");

        ResourceGroupState resourceGroupState = new ResourceGroupState();
        resourceGroupState.customProperties = new HashMap<>();
        resourceGroupState.customProperties.put(
                ComputeProperties.ENDPOINT_LINK_PROP_NAME,
                context.profile.endpoint.documentSelfLink);
        return ResourceGroupUtils.updateDeploymentResourceGroup(this.getHost(),
                UriUtils.buildUri(getHost(), getSelfLink()), resourceGroupState,
                context.computeNetwork.groupLinks, context.computeNetwork.tenantLinks)
                .thenApply(ignore -> context);
    }
}
