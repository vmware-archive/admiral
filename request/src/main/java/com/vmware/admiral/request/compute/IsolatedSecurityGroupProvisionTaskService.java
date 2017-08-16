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

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;
import static com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService.NETWORK_STATE_ID_PROP_NAME;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.NetworkType;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile.IsolationSupportType;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.admiral.request.compute.IsolatedSecurityGroupProvisionTaskService.IsolatedSecurityGroupProvisionTaskState;
import com.vmware.admiral.request.compute.IsolatedSecurityGroupProvisionTaskService.IsolatedSecurityGroupProvisionTaskState.SubStage;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule.Access;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService.ProvisionSecurityGroupTaskState;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Task implementing the provisioning of an isolated compute network with security group.
 */
public class IsolatedSecurityGroupProvisionTaskService
        extends
        AbstractTaskStatefulService<IsolatedSecurityGroupProvisionTaskState, IsolatedSecurityGroupProvisionTaskService.IsolatedSecurityGroupProvisionTaskState.SubStage> {

    public static final String FACTORY_LINK =
            ManagementUriParts.REQUEST_PROVISION_ISOLATED_SECURITY_GROUP_TASKS;

    public static final String DISPLAY_NAME = "Compute Network Isolated Security Group Provision";

    public static class IsolatedSecurityGroupProvisionTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<IsolatedSecurityGroupProvisionTaskState.SubStage> {

        public enum SubStage {
            CREATED, ALLOCATING_EXTERNAL_IP_ADDRESS, START_PROVISIONING, PROVISIONING, COMPLETED, ERROR;

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
         * (Required) Links to already allocated resources that are going to be provisioned.
         */
        @Documentation(
                description = "Links to already allocated resources that are going to be provisioned.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String resourceLink;

        // Service use fields:

        /**
         * (Internal) Reference to the adapter that will fulfill the provision request.
         */
        @Documentation(
                description = "Reference to the adapter that will fulfill the provision request.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public URI instanceAdapterReference;

    }

    private static class Context {
        public Context(String computeNetworkLink, IsolatedSecurityGroupProvisionTaskState state,
                ServiceTaskCallback<SubStage> serviceTaskCallback) {
            this.computeNetworkLink = computeNetworkLink;
            this.state = state;
            this.serviceTaskCallback = serviceTaskCallback;
        }

        public String computeNetworkLink;
        public ComputeNetwork computeNetwork;
        public ProfileStateExpanded profile;
        public SubnetState subnet;
        public EndpointState isolatedNetworkEndpoint;
        public String securityGroupInstanceAdapterReference;
        public String subnetCIDR;
        public ComputeNetworkDescription computeNetworkDescription;
        public IsolatedSecurityGroupProvisionTaskState state;
        public SecurityGroupState isolationSecurityGroup;
        public NetworkState subnetNetworkState;
        public ComputeStateWithDescription endpointComputeState;
        public ServiceTaskCallback<SubStage> serviceTaskCallback;
    }

    public IsolatedSecurityGroupProvisionTaskService() {
        super(IsolatedSecurityGroupProvisionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(IsolatedSecurityGroupProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            provisionResources(state);
            break;
        case PROVISIONING:
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

    private void provisionResources(IsolatedSecurityGroupProvisionTaskState state) {
        try {
            ServiceTaskCallback<SubStage> callback = ServiceTaskCallback.create(getUri());
            callback.onSuccessTo(SubStage.COMPLETED);
            callback.onErrorTo(SubStage.ERROR);

            DeferredResult
                    .completed(new Context(state.resourceLink, state, callback))
                    .thenCompose(this::populateContext)
                    .thenCompose(this::provisionResource)
                    .exceptionally(t -> {
                        logSevere("Failure provisioning a network: %s", Utils.toString(t));
                        callback.sendResponse(this, t);
                        return null;
                    });
            proceedTo(SubStage.PROVISIONING);
        } catch (Throwable e) {
            failTask("System failure creating SubnetStates", e);
        }
    }

    private DeferredResult<Context> populateContext(Context context) {
        return DeferredResult.completed(context)
                .thenCompose(this::populateComputeNetwork)
                .thenCompose(this::populateComputeNetworkDescription)
                .thenCompose(this::populateSubnet)
                .thenCompose(this::populateProfile)
                .thenCompose(ctx -> {
                    if (context.computeNetwork.networkType != NetworkType.ISOLATED) {
                        return DeferredResult.completed(context);
                    } else {
                        // Get isolated network context
                        return DeferredResult.completed(context)
                                .thenCompose(this::populateEndpoint)
                                .thenCompose(this::populateInstanceAdapterReference);
                    }
                });
    }

    private DeferredResult<Context> provisionResource(Context context) {
        return DeferredResult.completed(context)
                .thenCompose(this::populateEndpointComputeState)
                .thenCompose(this::createSecurityGroup)
                .thenCompose(this::populateSubnetNetworkState)
                .thenCompose(this::provisionSecurityGroup);
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

    private DeferredResult<Context> populateEndpoint(Context context) {
        if (context.profile == null
                || context.profile.networkProfile.isolatedNetworkState == null) {
            return DeferredResult.completed(context);
        } else {
            // Use network profile isolated network endpoint link to provision subnet
            // In case of NSX-T isolated networks, profile endpoint will be vSphere
            // and network profile Isolated network endpoint will be NSX-T
            return this.sendWithDeferredResult(Operation.createGet(this.getHost(),
                    context.profile.networkProfile.isolatedNetworkState.endpointLink),
                    EndpointState.class)
                    .thenApply(endpoint -> {
                        context.isolatedNetworkEndpoint = endpoint;
                        return context;
                    });
        }
    }

    private DeferredResult<Context> populateInstanceAdapterReference(Context context) {
        AssertUtil.assertNotNull(context.profile.endpoint.endpointType,
                "Context.profile.endpoint.endpointType should not be null.");
        return sendWithDeferredResult(
                Operation.createGet(getHost(),
                        UriUtils.buildUriPath(
                                PhotonModelAdaptersRegistryService.FACTORY_LINK,
                                context.profile.endpoint.endpointType)),
                PhotonModelAdapterConfig.class)
                .thenApply(config -> {
                    context.securityGroupInstanceAdapterReference = config.adapterEndpoints
                            .get(AdapterTypePath.SECURITY_GROUP_ADAPTER.key);
                    return context;
                });
    }

    private DeferredResult<Context> populateSubnet(Context context) {
        AssertUtil.assertNotNull(context.computeNetwork,
                "Context.computeNetwork should not be null.");
        AssertUtil.assertNotNull(context.computeNetwork.subnetLink,
                "Context.computeNetwork.subnetLink should not be null.");

        return this.sendWithDeferredResult(
                Operation.createGet(this, context.computeNetwork.subnetLink), SubnetState.class)
                .thenApply(subnetState -> {
                    context.subnet = subnetState;
                    return context;
                });
    }

    private DeferredResult<Context> createSecurityGroup(Context context) {
        AssertUtil.assertNotNull(context.profile, "Context.profile should not be null.");
        AssertUtil.assertNotNull(context.computeNetwork,
                "Context.computeNetwork should not be null.");
        AssertUtil.assertNotNull(context.computeNetwork.groupLinks,
                "Context.computeNetwork.groupLinks should not be null.");
        AssertUtil.assertNotNull(context.profile.networkProfile,
                "Context.profile.networkProfile should not be null.");
        AssertUtil.assertTrue(context.computeNetwork.networkType.equals(NetworkType.ISOLATED),
                "Context.computeNetwork.networkType should be ISOLATED");
        AssertUtil.assertTrue(context.profile.networkProfile.isolationType.equals(
                IsolationSupportType.SECURITY_GROUP),
                "Context.profile.networkProfile.isolationType should be SECURITY_GROUP");
        AssertUtil.assertNotNull(context.endpointComputeState,
                "Context.endpointComputeState should not be null");

        // Create a new security group for this network
        SecurityGroupState securityGroup = new SecurityGroupState();
        securityGroup.id = UUID.randomUUID().toString();
        securityGroup.documentSelfLink = securityGroup.id;
        securityGroup.name =
                String.format("isolation-network-%s-deployment-%s",
                        context.computeNetworkDescription.name,
                        RequestUtils.getContextId(context.state));
        securityGroup.desc = securityGroup.name;
        securityGroup.regionId = context.endpointComputeState.description.regionId;
        securityGroup.endpointLink = context.profile.endpointLink;
        securityGroup.tenantLinks = context.state.tenantLinks;
        securityGroup.instanceAdapterReference =
                URI.create(context.securityGroupInstanceAdapterReference);
        securityGroup.resourcePoolLink = context.profile.endpoint.resourcePoolLink;
        securityGroup.authCredentialsLink = context.profile.endpoint.authCredentialsLink;
        securityGroup.groupLinks = context.computeNetwork.groupLinks;
        String contextId = RequestUtils.getContextId(context.state);
        if (contextId != null) {
            securityGroup.customProperties = new HashMap<>();
            securityGroup.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        }

        securityGroup.ingress = buildSecurityGroupRule("inbound-deny-all", Access.Deny);
        if (context.computeNetworkDescription.outboundAccess == null ||
                context.computeNetworkDescription.outboundAccess.equals(Boolean.FALSE)) {
            securityGroup.egress = buildSecurityGroupRule("outbound-deny-all", Access.Deny);
        } else {
            securityGroup.egress = buildSecurityGroupRule("outbound-allow-all", Access.Allow);
        }

        return this.sendWithDeferredResult(
                Operation.createPost(this, SecurityGroupService.FACTORY_LINK)
                        .setBody(securityGroup), SecurityGroupState.class)
                .thenApply(sg -> {
                    context.isolationSecurityGroup = sg;
                    return context;
                })
                .thenCompose(this::patchComputeNetwork);
    }

    private DeferredResult<Context> provisionSecurityGroup(Context context) {

        AssertUtil.assertNotNull(context.isolationSecurityGroup,
                "Context.isolationSecurityGroup should not be null.");
        AssertUtil.assertNotNull(context.subnetNetworkState,
                "Context.subnetNetworkState should not be null.");

        ProvisionSecurityGroupTaskState provisionTaskState = new ProvisionSecurityGroupTaskState();
        provisionTaskState.isMockRequest = DeploymentProfileConfig.getInstance().isTest();
        provisionTaskState.requestType = SecurityGroupInstanceRequest.InstanceRequestType.CREATE;
        provisionTaskState.tenantLinks = context.computeNetwork.tenantLinks;
        provisionTaskState.documentExpirationTimeMicros = ServiceUtils
                .getDefaultTaskExpirationTimeInMicros();
        provisionTaskState.securityGroupDescriptionLinks = Stream.of(context.isolationSecurityGroup
                .documentSelfLink).collect(Collectors.toSet());
        provisionTaskState.serviceTaskCallback = context.serviceTaskCallback;
        provisionTaskState.customProperties = new HashMap<>();
        // the network state id is the vpc id on AWS; it is needed by the photon-model to create
        // this security group
        provisionTaskState.customProperties.put(NETWORK_STATE_ID_PROP_NAME, context
                .subnetNetworkState.id);

        return this.sendWithDeferredResult(
                Operation.createPost(this, ProvisionSecurityGroupTaskService.FACTORY_LINK)
                        .setBody(provisionTaskState))
                .thenApply(op -> context);
    }

    private DeferredResult<Context> patchComputeNetwork(Context context) {
        if (context.isolationSecurityGroup != null) {
            if (context.computeNetwork.securityGroupLinks == null) {
                context.computeNetwork.securityGroupLinks = new HashSet<>();
            }
            context.computeNetwork.securityGroupLinks.add(context
                    .isolationSecurityGroup.documentSelfLink);
        }
        return this.sendWithDeferredResult(
                Operation.createPatch(this, context.computeNetwork.documentSelfLink)
                        .setBody(context.computeNetwork))
                .thenApply(op -> context);
    }

    private List<Rule> buildSecurityGroupRule(String name, Access access) {
        Rule isolationRule = new Rule();
        isolationRule.name = name;
        isolationRule.protocol = SecurityGroupService.ANY;
        isolationRule.ipRangeCidr = "0.0.0.0/0";
        isolationRule.access = access;
        isolationRule.ports = "1-65535";

        return Arrays.asList(isolationRule);
    }

    private DeferredResult<Context> populateSubnetNetworkState(Context context) {
        AssertUtil.assertNotNull(context.subnet,
                "Context.subnet should not be null.");

        return this.sendWithDeferredResult(
                Operation.createGet(this, context.subnet.networkLink), NetworkState.class)
                .thenApply(networkState -> {
                    context.subnetNetworkState = networkState;
                    return context;
                });
    }

    private DeferredResult<Context> populateEndpointComputeState(Context context) {
        AssertUtil.assertNotNull(context.profile.endpoint,
                "Context.profile.endpoint should not be null.");

        return this.sendWithDeferredResult(
                Operation.createGet(ComputeStateWithDescription
                        .buildUri(UriUtils.buildUri(getHost(), context.profile.endpoint
                                .computeLink))),
                ComputeStateWithDescription.class)
                .thenApply(computeState -> {
                    context.endpointComputeState = computeState;
                    return context;
                });
    }
}
