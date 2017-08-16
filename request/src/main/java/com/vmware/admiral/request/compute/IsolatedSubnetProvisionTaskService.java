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

import static com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest.allocationRequest;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationState;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.request.compute.IsolatedSubnetProvisionTaskService.IsolatedSubnetProvisionTaskState;
import com.vmware.admiral.request.compute.IsolatedSubnetProvisionTaskService.IsolatedSubnetProvisionTaskState.SubStage;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.support.LifecycleState;
import com.vmware.photon.controller.model.tasks.IPAddressAllocationTaskService;
import com.vmware.photon.controller.model.tasks.IPAddressAllocationTaskService.IPAddressAllocationTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService.ProvisionSubnetTaskState;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class IsolatedSubnetProvisionTaskService
        extends
        AbstractTaskStatefulService<IsolatedSubnetProvisionTaskState, IsolatedSubnetProvisionTaskService.IsolatedSubnetProvisionTaskState.SubStage> {

    public static final String FACTORY_LINK =
            ManagementUriParts.REQUEST_PROVISION_ISOLATED_SUBNET_TASKS;

    public static final String DISPLAY_NAME = "Compute Network Isolated Subnet Provision";

    public static class IsolatedSubnetProvisionTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<IsolatedSubnetProvisionTaskState.SubStage> {

        public enum SubStage {
            CREATED, ALLOCATING_EXTERNAL_IP_ADDRESS, ALLOCATED_EXTERNAL_IP_ADDRESS, START_PROVISIONING, PROVISIONING, COMPLETED, ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(PROVISIONING, ALLOCATING_EXTERNAL_IP_ADDRESS));
        }

        /**
         * (Required) Links to already allocated resources that are going to be provisioned.
         */
        @Documentation(
                description = "Link to already allocated resources that are going to be provisioned.")
        @PropertyOptions(indexing = ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY, usage = {
                ServiceDocumentDescription.PropertyUsageOption.REQUIRED,
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String resourceLink;

        // Service use fields:

        /**
         * (Internal) Reference to the adapter that will fulfill the provision request.
         */
        @Documentation(
                description = "Reference to the adapter that will fulfill the provision request.")
        @PropertyOptions(indexing = ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY, usage = {
                ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE,
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public URI instanceAdapterReference;

        /**
         * (Internal) External IP address links.
         */
        @Documentation(
                description = "External IP address links.")
        @PropertyOptions(indexing = ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY, usage = {
                ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE,
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public List<String> ipAddressLinks;

    }

    private static class Context {
        public Context(IsolatedSubnetProvisionTaskState state,
                ServiceTaskCallback<SubStage> serviceTaskCallback) {
            this.state = state;
            this.serviceTaskCallback = serviceTaskCallback;
        }

        public ComputeNetworkService.ComputeNetwork computeNetwork;
        public ComputeNetworkDescription computeNetworkDescription;
        public ProfileService.ProfileStateExpanded profile;
        public SubnetState subnet;
        public EndpointService.EndpointState isolatedNetworkEndpoint;
        public String subnetInstanceAdapterReference;
        public IsolatedSubnetProvisionTaskState state;
        public ServiceTaskCallback<SubStage> serviceTaskCallback;
    }

    public IsolatedSubnetProvisionTaskService() {
        super(IsolatedSubnetProvisionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(IsolatedSubnetProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            allocateExternalIPAddress(state);
            break;
        case ALLOCATING_EXTERNAL_IP_ADDRESS:
            break;
        case ALLOCATED_EXTERNAL_IP_ADDRESS:
            saveExternalIPAddress(state);
            break;
        case START_PROVISIONING:
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

    private void allocateExternalIPAddress(IsolatedSubnetProvisionTaskState state) {
        ServiceTaskCallback<SubStage> callback = createCallback(
                SubStage.ALLOCATED_EXTERNAL_IP_ADDRESS,
                // ignore IP address allocation errors for now;
                // this needs to go to SubStage.ERROR once the IP allocation task service is able
                // to handle the various providers properly (e.g., elastic IP allocation on AWS)
                SubStage.START_PROVISIONING);

        DeferredResult.completed(new Context(state, callback))
                .thenCompose(this::populateContext)
                .thenCompose(this::populateEndpoint)
                .thenCompose(this::populateInstanceAdapterReference)
                .thenCompose(this::createSubnet)
                .thenCompose(context -> {
                    if (context.subnet.externalSubnetLink == null) {
                        proceedTo(SubStage.START_PROVISIONING);
                        return DeferredResult.completed(context);
                    }
                    return sendIPAllocationRequest(context)
                            .thenApply(ignore -> {
                                proceedTo(SubStage.ALLOCATING_EXTERNAL_IP_ADDRESS);
                                return DeferredResult.completed(context);
                            });
                })
                .exceptionally(e -> {
                    failTask("Failed allocating External IP address", e);
                    return null;
                });

    }

    private DeferredResult<Context> sendIPAllocationRequest(Context context) {

        IPAddressAllocationTaskState ipAddressAllocationTask = new IPAddressAllocationTaskState();
        ipAddressAllocationTask.allocationCount = 1;
        ipAddressAllocationTask.connectedResourceLink = context.subnet.documentSelfLink;
        ipAddressAllocationTask.requestType = IPAddressAllocationTaskState.RequestType.ALLOCATE;
        ipAddressAllocationTask.subnetLink = context.subnet.externalSubnetLink;
        ipAddressAllocationTask.serviceTaskCallback = context.serviceTaskCallback;

        return this.sendWithDeferredResult(
                Operation.createPost(this, IPAddressAllocationTaskService.FACTORY_LINK)
                        .setBody(ipAddressAllocationTask))
                .thenApply(op -> context);

    }

    private void saveExternalIPAddress(IsolatedSubnetProvisionTaskState state) {
        DeferredResult.completed(new Context(state, null))
                .thenCompose(this::populateContext)
                .thenCompose(context -> {
                    AssertUtil.assertNotNull(context.subnet,
                            "context.subnet should not be null");

                    if (state.ipAddressLinks != null && !state.ipAddressLinks.isEmpty()) {
                        context.subnet.customProperties.put(
                                ComputeConstants.CUSTOM_PROP_ISOLATION_EXTERNAL_IP_ADDRESS_LINK,
                                state.ipAddressLinks.get(0));
                    }

                    return patchSubnet(context);

                })
                .whenComplete((context, throwable) -> {
                    if (throwable != null) {
                        failTask("Failed updating Subnet with allocated IP address", throwable);
                        return;
                    }
                    proceedTo(SubStage.START_PROVISIONING);
                });
    }

    private void provisionResources(IsolatedSubnetProvisionTaskState state) {
        ServiceTaskCallback<SubStage> callback = createCallback(SubStage.COMPLETED, SubStage.ERROR);
        DeferredResult
                .completed(new Context(state, callback))
                .thenCompose(this::populateContext)
                .thenCompose(this::provisionResource)
                .exceptionally(t -> {
                    logSevere("Failure provisioning a network: %s", Utils.toString(t));
                    callback.sendResponse(this, t);
                    return null;
                });
        proceedTo(SubStage.PROVISIONING);
    }

    private DeferredResult<Context> populateContext(Context context) {
        return DeferredResult.completed(context)
                .thenCompose(this::populateComputeNetwork)
                .thenCompose(this::populateComputeNetworkDescription)
                .thenCompose(this::populateSubnet)
                .thenCompose(this::populateProfile)
                .thenCompose(ctx -> {
                    AssertUtil.assertTrue(context.computeNetwork.networkType
                                    == ComputeNetworkDescriptionService.NetworkType.ISOLATED &&
                                    context.profile.networkProfile.isolationType
                                            == NetworkProfileService.NetworkProfile.IsolationSupportType.SUBNET,
                            "Compute network is not isolated");
                    return DeferredResult.completed(context);
                });
    }

    private DeferredResult<Context> provisionResource(Context context) {
        if (context.subnet == null) {
            throw new IllegalArgumentException(
                    String.format("Subnet is required to provision an ISOLATED network '%s'.",
                            context.computeNetwork.name));
        }
        return DeferredResult.completed(context)
                .thenCompose(this::allocateSubnetCIDR)
                .thenCompose(this::provisionSubnet);
    }

    private DeferredResult<Context> populateComputeNetwork(Context context) {
        return this.sendWithDeferredResult(
                Operation.createGet(this, context.state.resourceLink),
                ComputeNetworkService.ComputeNetwork.class)
                .thenApply(computeNetwork -> {
                    context.computeNetwork = computeNetwork;
                    return context;
                });
    }

    private DeferredResult<Context> populateComputeNetworkDescription(Context context) {
        AssertUtil.assertNotNull(context.computeNetwork, "context.computeNetwork should not be "
                + "null");
        return this.sendWithDeferredResult(
                Operation.createGet(this, context.computeNetwork.descriptionLink),
                ComputeNetworkDescription.class)
                .thenApply(computeNetworkDescription -> {
                    context.computeNetworkDescription = computeNetworkDescription;
                    return context;
                });
    }

    private DeferredResult<Context> populateProfile(Context context) {
        AssertUtil.assertNotNull(context.computeNetwork.provisionProfileLink,
                "Context.computeNetwork.provisionProfileLink should not be null");

        URI uri = UriUtils.buildUri(this.getHost(), context.computeNetwork.provisionProfileLink);
        uri = UriUtils.buildExpandLinksQueryUri(uri);

        return this.sendWithDeferredResult(Operation.createGet(uri),
                ProfileService.ProfileStateExpanded.class)
                .thenApply(profile -> {
                    context.profile = profile;
                    return context;
                });
    }

    private DeferredResult<Context> populateSubnet(Context context) {
        AssertUtil.assertNotNull(context.computeNetwork,
                "Context.computeNetwork should not be null.");
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
                    EndpointService.EndpointState.class)
                    .thenApply(endpoint -> {
                        context.isolatedNetworkEndpoint = endpoint;
                        return context;
                    });
        }
    }

    private DeferredResult<Context> populateInstanceAdapterReference(Context context) {
        AssertUtil.assertNotNull(context.isolatedNetworkEndpoint.endpointType,
                "Context.isolatedNetworkEndpoint.endpointType should not be null.");
        return sendWithDeferredResult(
                Operation.createGet(getHost(),
                        UriUtils.buildUriPath(
                                PhotonModelAdaptersRegistryService.FACTORY_LINK,
                                context.isolatedNetworkEndpoint.endpointType)),
                PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig.class)
                .thenApply(config -> {
                    context.subnetInstanceAdapterReference = config.adapterEndpoints
                            .get(UriPaths.AdapterTypePath.SUBNET_ADAPTER.key);
                    return context;
                });
    }

    private DeferredResult<Context> allocateSubnetCIDR(Context context) {
        AssertUtil.assertNotNull(context.profile, "Context.profile should not be null.");
        AssertUtil.assertNotNull(context.profile.networkProfile.isolatedSubnetCIDRPrefix,
                "Context.profile.networkProfile.isolatedSubnetCIDRPrefix should "
                        + "not be null.");
        AssertUtil.assertNotNull(context.subnet, "Context.subnet should not be null.");

        String optionalNetworkCIDR = context.profile.networkProfile.isolationNetworkCIDR;
        ComputeNetworkCIDRAllocationRequest request =
                allocationRequest(context.subnet.id,
                        context.profile.networkProfile.isolatedSubnetCIDRPrefix,
                        optionalNetworkCIDR);
        return this.sendWithDeferredResult(
                Operation.createPatch(this,
                        context.profile.networkProfile.isolationNetworkCIDRAllocationLink)
                        .setBody(request),
                ComputeNetworkCIDRAllocationState.class)
                .thenCompose(cidrAllocation -> {
                    // Store the allocated CIDR in the context.
                    context.subnet.subnetCIDR = cidrAllocation.allocatedCIDRs.get(
                            request.subnetLink);
                    return patchSubnet(context);
                });
    }

    private DeferredResult<Context> createSubnet(Context context) {
        AssertUtil.assertNotNull(context.profile, "Context.profile should not be null.");
        AssertUtil.assertNotNull(context.computeNetwork,
                "Context.computeNetwork should not be null.");
        AssertUtil.assertNotNull(context.computeNetwork.groupLinks,
                "Context.computeNetwork.groupLinks should not be null.");
        AssertUtil.assertNotNull(context.profile.networkProfile,
                "Context.profile.networkProfile should not be null.");

        // Create a new subnet template to attach to the VM NICs
        SubnetState subnet = new SubnetState();
        subnet.id = UUID.randomUUID().toString();
        subnet.name = context.computeNetwork.name;
        subnet.tenantLinks = context.computeNetwork.tenantLinks;
        subnet.networkLink = context.profile.networkProfile.isolationNetworkLink;
        subnet.endpointLink = context.isolatedNetworkEndpoint.documentSelfLink;
        subnet.instanceAdapterReference = URI.create(context.subnetInstanceAdapterReference);
        subnet.groupLinks = context.computeNetwork.groupLinks;
        subnet.externalSubnetLink = (context.computeNetworkDescription.outboundAccess != null &&
                context.computeNetworkDescription.outboundAccess == true) ?
                context.profile.networkProfile.isolationExternalSubnetLink : null;

        subnet.lifecycleState = LifecycleState.PROVISIONING;

        subnet.customProperties = context.computeNetwork.customProperties;
        subnet.customProperties = subnet.customProperties == null ?
                new HashMap<>() :
                subnet.customProperties;

        if (context.profile.networkProfile.extensionData != null) {
            subnet.customProperties.put(
                    ComputeConstants.CUSTOM_PROP_NETWORK_PROFILE_EXTENSION_DATA,
                    Utils.toJson(context.profile.networkProfile.extensionData));
        }

        String contextId = RequestUtils.getContextId(context.state);
        if (contextId != null) {
            subnet.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        }

        return this.sendWithDeferredResult(
                Operation.createPost(this, SubnetService.FACTORY_LINK)
                        .setBody(subnet), SubnetState.class)
                .thenApply(subnetState -> context.subnet = subnetState)
                .thenCompose(subnetState -> patchComputeNetwork(context));
    }

    private DeferredResult<Context> provisionSubnet(Context context) {
        ProvisionSubnetTaskState provisionTaskState = new ProvisionSubnetTaskState();
        boolean isMockRequest = DeploymentProfileConfig.getInstance().isTest();
        if (isMockRequest) {
            provisionTaskState.options = EnumSet.of(TaskOption.IS_MOCK);
        }
        provisionTaskState.requestType = SubnetInstanceRequest.InstanceRequestType.CREATE;
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

    private DeferredResult<Context> patchComputeNetwork(Context context) {
        context.computeNetwork.subnetLink = context.subnet.documentSelfLink;
        return this.sendWithDeferredResult(
                Operation.createPatch(this, context.computeNetwork.documentSelfLink)
                        .setBody(context.computeNetwork))
                .thenApply(op -> context);
    }

    private DeferredResult<Context> patchSubnet(Context context) {
        return this.sendWithDeferredResult(
        Operation.createPatch(this, context.subnet.documentSelfLink)
                .setBody(context.subnet), SubnetState.class)
                            .thenApply(subnetState -> context.subnet = subnetState)
                .thenCompose(ignore -> DeferredResult.completed(context));
    }

    private ServiceTaskCallback<SubStage> createCallback(SubStage completeStage,
            SubStage errorStage) {
        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback.create(getUri());
        callback.onSuccessTo(completeStage);
        callback.onErrorTo(errorStage);
        return callback;
    }
}
