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

import static com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest.allocationRequest;

import java.net.URI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationState;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.NetworkType;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.admiral.request.compute.ComputeNetworkProvisionTaskService.ComputeNetworkProvisionTaskState.SubStage;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService.ComputeProvisionTaskState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService.ProvisionSubnetTaskState;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback;
import com.vmware.photon.controller.model.tasks.SubTaskService;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

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
            CREATED, START_PROVISIONING, PROVISIONING, COMPLETED, ERROR;

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
        @Documentation(description = "Links to already allocated resources that are going to be provisioned.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public Set<String> resourceLinks;

        // Service use fields:

        /**
         * (Internal) Reference to the adapter that will fulfill the provision request.
         */
        @Documentation(description = "Reference to the adapter that will fulfill the provision request.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public URI instanceAdapterReference;

    }

    private static class Context {
        public Context(String computeNetworkLink, String subTaskLink) {
            this.computeNetworkLink = computeNetworkLink;
            this.subTaskLink = subTaskLink;
        }

        public String computeNetworkLink;
        public String subTaskLink;
        public ComputeNetwork computeNetwork;
        public ProfileStateExpanded profile;
        public SubnetState subnet;
        public EndpointState isolatedNetworkEndpoint;
        public String instanceAdapterReference;
        public String subnetCIDR;
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
            provisionResources(state, null);
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

    private void provisionResources(ComputeNetworkProvisionTaskState state, String subTaskLink) {
        try {
            Set<String> resourceLinks = state.resourceLinks;
            if (resourceLinks == null || resourceLinks.isEmpty()) {
                throw new LocalizableValidationException(
                        "No compute network instances to provision",
                        "request.compute.network.provision.empty");
            }
            if (subTaskLink == null) {
                // recurse after creating a sub task
                createSubTaskForProvisionCallbacks(state);
                return;
            }

            resourceLinks.forEach(computeNetworkLink -> DeferredResult
                    .completed(new Context(computeNetworkLink, subTaskLink))
                    .thenCompose(this::populateContext)
                    .thenCompose(this::provisionResource)
                    .exceptionally(t -> {
                        logSevere("Failure provisioning a subnet: %s", t);
                        ResourceOperationResponse r = ResourceOperationResponse
                                .fail(null, t);
                        completeSubTask(subTaskLink, r);
                        return null;
                    }));

            logInfo("Requested provisioning of %s compute network resources.",
                    resourceLinks.size());
            proceedTo(SubStage.PROVISIONING);
        } catch (Throwable e) {
            failTask("System failure creating SubnetStates", e);
        }
    }

    private DeferredResult<Context> populateContext(Context context) {
        return DeferredResult.completed(context)
                .thenCompose(this::populateComputeNetwork)
                .thenCompose(ctx -> {
                    if (context.computeNetwork.networkType != NetworkType.ISOLATED) {
                        return DeferredResult.completed(context);
                    } else {
                        // Get isolated network context
                        return DeferredResult.completed(context)
                                .thenCompose(this::populateSubnet)
                                .thenCompose(this::populateProfile)
                                .thenCompose(this::populateEndpoint)
                                .thenCompose(this::populateInstanceAdapterReference);

                    }
                });
    }

    private DeferredResult<Context> provisionResource(Context context) {
        // Should we provision a new Subnet?
        if (context.computeNetwork.networkType == NetworkType.ISOLATED) {
            // Yes!
            if (context.subnet == null) {
                throw new IllegalArgumentException(
                        String.format("Subnet is required to provision an ISOLATED network '%s'.",
                                context.computeNetwork.name));
            }
            return DeferredResult.completed(context)
                    .thenCompose(this::allocateSubnetCIDR)
                    .thenCompose(this::customizeTemplateSubnet)
                    .thenCompose(this::provisionSubnet);

        } else {
            // No!
            ResourceOperationResponse r = ResourceOperationResponse.finish(null /* is this ok? */);
            completeSubTask(context.subTaskLink, r);
            return DeferredResult.completed(context);
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

    private DeferredResult<Context> populateProfile(Context context) {
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
        if (context.isolatedNetworkEndpoint == null) {
            return DeferredResult.completed(context);
        } else {
            String uri = UriUtils.buildUriPath(PhotonModelAdaptersRegistryService.FACTORY_LINK,
                    context.isolatedNetworkEndpoint.endpointType);

            return sendWithDeferredResult(
                    Operation.createGet(getHost(), uri), PhotonModelAdapterConfig.class)
                            .thenApply(config -> {
                                context.instanceAdapterReference = config.adapterEndpoints
                                        .get(UriPaths.AdapterTypePath.SUBNET_ADAPTER.key);
                                return context;
                            });
        }
    }

    private DeferredResult<Context> populateSubnet(Context context) {
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

    private DeferredResult<Context> allocateSubnetCIDR(Context context) {
        AssertUtil.assertNotNull(context.profile, "Context.profile should not be null.");
        AssertUtil.assertNotNull(context.subnet, "Context.subnet should not be null.");

        // Get new CIDR.
        ComputeNetworkCIDRAllocationRequest request =
                allocationRequest(context.subnet.documentSelfLink);
        return this.sendWithDeferredResult(
                Operation.createPatch(this,
                        context.profile.networkProfile.isolationNetworkCIDRAllocationLink)
                        .setBody(request),
                ComputeNetworkCIDRAllocationState.class)
                .thenApply(cidrAllocation -> {
                    // Store the allocated CIDR in the context.
                    context.subnetCIDR = cidrAllocation.allocatedCIDRs.get(request.subnetLink);
                    return context;
                });
    }

    private DeferredResult<Context> customizeTemplateSubnet(Context context) {
        ProfileStateExpanded profile = context.profile;
        SubnetState subnet = context.subnet;

        subnet.networkLink = profile.networkProfile.isolationNetworkLink;
        subnet.endpointLink = context.isolatedNetworkEndpoint.documentSelfLink;
        subnet.instanceAdapterReference = URI.create(context.instanceAdapterReference);
        subnet.subnetCIDR = context.subnetCIDR;

        return this.sendWithDeferredResult(Operation.createPatch(this, subnet.documentSelfLink)
                .setBody(subnet))
                .thenApply(op -> context);
    }

    private DeferredResult<Context> provisionSubnet(Context context) {

        ProvisionSubnetTaskState provisionTaskState = new ProvisionSubnetTaskState();
        boolean isMockRequest = DeploymentProfileConfig.getInstance().isTest();
        if (isMockRequest) {
            provisionTaskState.options = EnumSet.of(TaskOption.IS_MOCK);
        }
        provisionTaskState.requestType = InstanceRequestType.CREATE;
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

    private void completeSubTask(String subTaskLink, Object body) {
        Operation.createPatch(this, subTaskLink)
                .setBody(body)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logWarning("Unable to complete subtask: %s, reason: %s",
                                        subTaskLink, Utils.toString(ex));
                            }
                        })
                .sendWith(this);
    }

    private void createSubTaskForProvisionCallbacks(ComputeNetworkProvisionTaskState currentState) {
        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback.create(getSelfLink());
        callback.onSuccessTo(SubStage.COMPLETED);
        SubTaskService.SubTaskState<ComputeNetworkProvisionTaskState.SubStage> subTaskInitState =
                new SubTaskService.SubTaskState<>();
        // tell the sub task with what to patch us, on completion
        subTaskInitState.serviceTaskCallback = callback;
        subTaskInitState.errorThreshold = 0;
        subTaskInitState.completionsRemaining = currentState.resourceLinks.size();
        subTaskInitState.tenantLinks = currentState.tenantLinks;
        Operation startPost = Operation
                .createPost(this, UUID.randomUUID().toString())
                .setBody(subTaskInitState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning("Failure creating sub task: %s",
                                        Utils.toString(e));
                                failTask("Failure creating sub task", e);
                                return;
                            }
                            SubTaskService.SubTaskState<?> body = o
                                    .getBody(SubTaskService.SubTaskState.class);
                            // continue, passing the sub task link
                            provisionResources(currentState, body.documentSelfLink);
                        });
        getHost().startService(startPost, new SubTaskService<ComputeProvisionTaskState.SubStage>());
    }
}
