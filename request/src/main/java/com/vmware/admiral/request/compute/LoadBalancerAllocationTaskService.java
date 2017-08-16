/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.compute;

import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.request.ResourceNamePrefixTaskService;
import com.vmware.admiral.request.ResourceNamePrefixTaskService.ResourceNamePrefixTaskState;
import com.vmware.admiral.request.compute.LoadBalancerAllocationTaskService.LoadBalancerAllocationTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
import com.vmware.photon.controller.model.resources.LoadBalancerService;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Task implementing the allocation of a load balancer.
 */
public class LoadBalancerAllocationTaskService extends
        AbstractTaskStatefulService<LoadBalancerAllocationTaskService.LoadBalancerAllocationTaskState, LoadBalancerAllocationTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_LOAD_BALANCER_ALLOCATION_TASKS;

    public static final String DISPLAY_NAME = "Load Balancer Allocation";

    // cached network description
    private volatile LoadBalancerDescription lbDescription;

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    public static class LoadBalancerAllocationTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<LoadBalancerAllocationTaskState.SubStage> {
        public enum SubStage {
            CREATED,
            DETERMINE_LB_NAME,
            FIND_ADAPTER,
            ENHANCE_DESCRIPTION,
            QUERY_COMPUTE_STATES,
            LOAD_COMPUTE_NETWORK,
            SELECT_PROFILE,
            CREATE_LB_STATE,
            COMPLETED,
            ERROR
        }

        /**
         * (Required) The description that defines the requested resource.
         */
        @Documentation(description = "The description that defines the requested resource.")
        @PropertyOptions(usage = { REQUIRED, SINGLE_ASSIGNMENT })
        public String resourceDescriptionLink;

        /**
         * Set by the task with the link to the endpoint owning this load balancer instance.
         */
        @Documentation(description = "Set by the task with the link to the endpoint owning this load balancer instance.")
        @PropertyOptions(indexing = STORE_ONLY, usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL })
        public String endpointLink;

        /**
         * Set by the task with the type of the endpoint owning this load balancer instance.
         */
        @Documentation(description = "Set by the task with the type of the endpoint owning this load balancer instance.")
        @PropertyOptions(indexing = STORE_ONLY, usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL })
        public String endpointType;

        /**
         * Set by the task with the region ID.
         */
        @Documentation(description = "Set by the task with the region ID.")
        @PropertyOptions(indexing = STORE_ONLY, usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL })
        public String regionId;

        /**
         * Set by task after resource name prefixes requested.
         */
        @Documentation(description = "Set by task after resource name prefixes requested.")
        @PropertyOptions(indexing = STORE_ONLY, usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL })
        public Set<String> resourceNames;

        /**
         * Set by the task with the reference to the load balancer adapter for this endpoint.
         */
        @Documentation(description = "Set by the task with the reference to the load balancer adapter for this endpoint.")
        @PropertyOptions(indexing = STORE_ONLY, usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL })
        public URI loadBalancerAdapterReference;

        /**
         * Set by the task with the links of the compute states.
         */
        @Documentation(description = "Set by the task with the links of the compute states.")
        @PropertyOptions(indexing = STORE_ONLY, usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL })
        public Set<String> computeLinks;

        /**
         * Set by the task with the compute network where the load balancer is attached to.
         */
        @Documentation(description = "Set by the task with the compute network where the load balancer is attached to.")
        @PropertyOptions(indexing = STORE_ONLY, usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL })
        public ComputeNetwork computeNetwork;

        /**
         * Set by the task with the links of the allocated resources.
         */
        @Documentation(description = "Set by the task with the links of the allocated resources.")
        @PropertyOptions(indexing = STORE_ONLY, usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL })
        public Set<String> resourceLinks;
    }

    public LoadBalancerAllocationTaskService() {
        super(LoadBalancerAllocationTaskState.class, LoadBalancerAllocationTaskState.SubStage.class,
                DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(LoadBalancerAllocationTaskState state) {
        if (this.lbDescription == null) {
            getState(state.resourceDescriptionLink, LoadBalancerDescription.class)
                    .thenAccept(lbd -> {
                        this.lbDescription = lbd;
                        handleStartedStagePatch(state);
                    });
            return;
        }

        // merge request/allocation properties and load balancer description properties
        // over the request's custom properties
        Map<String, String> customProperties = mergeCustomProperties(
                this.lbDescription.customProperties,
                state.customProperties);

        switch (state.taskSubStage) {
        case CREATED:
            retrieveEndpointDetails(state).thenAccept(endpointDetails -> {
                proceedTo(LoadBalancerAllocationTaskState.SubStage.DETERMINE_LB_NAME, s -> {
                    s.customProperties = customProperties;
                    s.endpointLink = endpointDetails.get(0);
                    s.endpointType = endpointDetails.get(1);
                    s.regionId = endpointDetails.get(2);
                });
            });
            break;

        case DETERMINE_LB_NAME:
            startResourceNameTask(state);
            break;

        case FIND_ADAPTER:
            findLoadBalancerAdapter(state).thenAccept(ref -> {
                proceedTo(LoadBalancerAllocationTaskState.SubStage.ENHANCE_DESCRIPTION, s -> {
                    s.loadBalancerAdapterReference = URI.create(ref);
                });
            });
            break;

        case ENHANCE_DESCRIPTION:
            enhanceDescription(state).thenAccept(lbd -> {
                this.lbDescription = lbd;
                proceedTo(LoadBalancerAllocationTaskState.SubStage.QUERY_COMPUTE_STATES);
            });
            break;

        case QUERY_COMPUTE_STATES:
            findComputeStates(state).thenAccept(links -> {
                proceedTo(LoadBalancerAllocationTaskState.SubStage.LOAD_COMPUTE_NETWORK, s -> {
                    s.computeLinks = links;
                });
            });
            break;

        case LOAD_COMPUTE_NETWORK:
            retrieveLbNetwork(state).thenAccept(cn -> {
                proceedTo(LoadBalancerAllocationTaskState.SubStage.SELECT_PROFILE, s -> {
                    s.computeNetwork = cn;
                });
            });
            break;

        case SELECT_PROFILE:
            selectProfile(state).thenAccept(
                    ignore -> proceedTo(LoadBalancerAllocationTaskState.SubStage.CREATE_LB_STATE));
            break;

        case CREATE_LB_STATE:
            createLoadBalancerState(state).thenAccept(lbs -> {
                proceedTo(LoadBalancerAllocationTaskState.SubStage.COMPLETED, s -> {
                    s.resourceLinks = Collections.singleton(lbs.documentSelfLink);
                });
            });
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

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            LoadBalancerAllocationTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated resources.");
        }
        return finishedResponse;
    }

    @Override
    protected ServiceTaskCallbackResponse getFailedCallbackResponse(
            LoadBalancerAllocationTaskState state) {
        CallbackCompleteResponse failedResponse = new CallbackCompleteResponse();
        failedResponse.copy(state.serviceTaskCallback.getFailedResponse(state.taskInfo.failure));
        failedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated resources.");
        }
        return failedResponse;
    }

    /**
     * Retrieves endpoint details.
     */
    private DeferredResult<List<String>> retrieveEndpointDetails(
            LoadBalancerAllocationTaskState state) {
        return getState(this.lbDescription.computeDescriptionLink, ComputeDescription.class)
                .thenApply(cd -> {
                    String endpointLink = cd.customProperties != null
                            ? cd.customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME)
                            : null;
                    if (endpointLink == null) {
                        throw new IllegalArgumentException(
                                "No endpointLink found in compute description "
                                        + this.lbDescription.computeDescriptionLink);
                    }

                    String endpointType = cd.customProperties != null
                            ? cd.customProperties.get(ComputeConstants.CUSTOM_PROP_ENDPOINT_TYPE_NAME)
                            : null;
                    if (endpointType == null) {
                        throw new IllegalArgumentException(
                                "No endpointType found in compute description "
                                        + this.lbDescription.computeDescriptionLink);
                    }

                    return Arrays.asList(endpointLink, endpointType, cd.regionId);
                }).whenComplete(exceptionHandler(
                        "Error retrieving endpoint details from compute description "
                                + this.lbDescription.computeDescriptionLink));
    }

    /**
     * Starts the resource name task.
     */
    private void startResourceNameTask(LoadBalancerAllocationTaskState state) {
        ResourceNamePrefixTaskState namePrefixTask = new ResourceNamePrefixTaskState();
        namePrefixTask.documentSelfLink = getSelfId();
        namePrefixTask.resourceCount = 1;
        namePrefixTask.baseResourceNameFormat = ResourceNamePrefixService
                .getDefaultResourceNameFormat(this.lbDescription.name);
        namePrefixTask.tenantLinks = state.tenantLinks;

        namePrefixTask.customProperties = state.customProperties;
        namePrefixTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, LoadBalancerAllocationTaskState.SubStage.FIND_ADAPTER,
                TaskStage.STARTED, LoadBalancerAllocationTaskState.SubStage.ERROR);
        namePrefixTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ResourceNamePrefixTaskService.FACTORY_LINK)
                .setBody(namePrefixTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating resource name prefix task", e);
                        return;
                    }
                }));
    }

    /**
     * Retrieves the load balancer adapter reference for the selected endpoint type.
     */
    private DeferredResult<String> findLoadBalancerAdapter(LoadBalancerAllocationTaskState state) {
        String configPath = UriUtils.buildUriPath(
                PhotonModelAdaptersRegistryService.FACTORY_LINK, state.endpointType);
        return getState(configPath, PhotonModelAdapterConfig.class)
                .thenApply(config -> {
                    String lbAdapterReference = config.adapterEndpoints
                            .get(UriPaths.AdapterTypePath.LOAD_BALANCER_ADAPTER.key);
                    if (lbAdapterReference == null) {
                        throw new IllegalArgumentException(
                                "No load balancer adapter reference found for endpoint type "
                                        + state.endpointType);
                    }
                    return lbAdapterReference;
                }).whenComplete(exceptionHandler("Error retrieving load balancer adapter"));
    }

    /**
     * Enhance load balancer description with endpoint-specific information.
     */
    private DeferredResult<LoadBalancerDescription> enhanceDescription(
            LoadBalancerAllocationTaskState state) {
        LoadBalancerDescription patchBody = new LoadBalancerDescription();
        patchBody.endpointLink = state.endpointLink;
        patchBody.regionId = state.regionId;
        patchBody.instanceAdapterReference = state.loadBalancerAdapterReference;
        Operation patchOp = Operation.createPatch(this, state.resourceDescriptionLink)
                .setBody(patchBody);

        return sendWithDeferredResult(patchOp)
                .whenComplete(exceptionHandler("Error enhancing load balancer description "
                        + state.resourceDescriptionLink))
                .thenCompose(ignore ->
                        getState(state.resourceDescriptionLink, LoadBalancerDescription.class));
    }

    /**
     * Selects a profile for the load balancer compute network.
     */
    private DeferredResult<Void> selectProfile(LoadBalancerAllocationTaskState state) {
        if (this.lbDescription.subnetLinks != null && !this.lbDescription.subnetLinks.isEmpty()) {
            return DeferredResult.completed(null);
        }

        if (state.computeNetwork.provisionProfileLink != null) {
            return DeferredResult.completed(null);
        }

        return NetworkProfileQueryUtils
                .selectComputeNetworkProfileByEndpoint(getHost(), getUri(),
                        state.tenantLinks, state.endpointLink, state.computeNetwork)
                .thenCompose(profile -> patchComputeNetwork(
                        state.computeNetwork.documentSelfLink, profile.documentSelfLink))
                .thenApply(network -> (Void) null)
                .whenComplete(exceptionHandler(String.format(
                        "Error selecting provision profile for network %s for load balancer %s",
                        state.computeNetwork.name, state.resourceDescriptionLink)));
    }

    /**
     * Retrieves the ComputeNetwork corresponding to the LoadBalancerDescription.networkName.
     */
    private DeferredResult<ComputeNetwork> retrieveLbNetwork(LoadBalancerAllocationTaskState state) {
        if (this.lbDescription.networkName == null) {
            return DeferredResult.completed(null);
        }

        DeferredResult<ComputeNetwork> dr = new DeferredResult<>();
        NetworkProfileQueryUtils.getContextComputeNetworksByName(getHost(),
                UriUtils.buildUri(getHost(), getSelfLink()), state.tenantLinks,
                RequestUtils.getContextId(state),
                Collections.singleton(this.lbDescription.networkName), (computeNetworks, ex) -> {
                    if (computeNetworks == null || computeNetworks.isEmpty()) {
                        dr.fail(new IllegalArgumentException(
                                String.format("Cannot find a network with name '%s' in the context",
                                        this.lbDescription.networkName)));
                    } else {
                        dr.complete(computeNetworks.get(0));
                    }
                });
        dr.whenComplete(exceptionHandler("Error retrieving network from context for load balancer "
                + state.resourceDescriptionLink));
        return dr;
    }

    private DeferredResult<ComputeNetwork> patchComputeNetwork(String networkLink, String profileLink) {
        ComputeNetwork patchBody = new ComputeNetwork();
        patchBody.provisionProfileLink = profileLink;
        return sendWithDeferredResult(Operation.createPatch(this, networkLink).setBody(patchBody),
                ComputeNetwork.class);
    }

    /**
     * Finds compute instances that this load balancer will balance.
     */
    private DeferredResult<Set<String>> findComputeStates(LoadBalancerAllocationTaskState state) {
        Query computeQuery = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_DESCRIPTION_LINK,
                        this.lbDescription.computeDescriptionLink)
                .build();
        QueryByPages<ComputeState> q = new QueryByPages<ComputeState>(getHost(), computeQuery,
                ComputeState.class, state.tenantLinks);

        return q.collectLinks(Collectors.toSet())
                .whenComplete(exceptionHandler("Error querying compute states for description "
                        + this.lbDescription.computeDescriptionLink));
    }

    /**
     * Creates the load balancer state based on the description.
     */
    private DeferredResult<LoadBalancerState> createLoadBalancerState(
            LoadBalancerAllocationTaskState state) {
        LoadBalancerState lbState = new LoadBalancerState();
        lbState.descriptionLink = this.lbDescription.documentSelfLink;
        lbState.name = state.resourceNames.iterator().next();
        lbState.routes = this.lbDescription.routes;
        lbState.internetFacing = this.lbDescription.internetFacing;
        lbState.computeLinks = state.computeLinks;
        lbState.endpointLink = this.lbDescription.endpointLink;
        lbState.regionId = this.lbDescription.regionId;
        lbState.securityGroupLinks = this.lbDescription.securityGroupLinks;
        lbState.tenantLinks = state.tenantLinks;
        lbState.customProperties = mergeCustomProperties(this.lbDescription.customProperties,
                state.customProperties);
        lbState.tagLinks = this.lbDescription.tagLinks;
        lbState.instanceAdapterReference = this.lbDescription.instanceAdapterReference;

        lbState.customProperties.put(
                ComputeConstants.FIELD_NAME_COMPOSITE_COMPONENT_LINK_KEY,
                UriUtils.buildUriPath(
                        CompositeComponentFactoryService.SELF_LINK, RequestUtils.getContextId(state)));

        Operation postOp = Operation.createPost(this, LoadBalancerService.FACTORY_LINK)
                .setBody(lbState);
        return sendWithDeferredResult(postOp, LoadBalancerState.class)
                .whenComplete(exceptionHandler("Failure creating load balancer state"));
    }

    /**
     * Helper method for retrieving document state for the given link.
     */
    private <T> DeferredResult<T> getState(String link, Class<T> type) {
        return sendWithDeferredResult(Operation.createGet(this, link), type)
                .whenComplete(exceptionHandler("Error retrieving document " + link));
    }

    /**
     * A convenient exception handler to make sure the task is failed when there are exceptions
     * in DeferredResult's.
     */
    private <T> BiConsumer<T, ? super Throwable> exceptionHandler(String msg) {
        return (t, e) -> {
            if (e != null) {
                failTask(msg, e);
            }
        };
    }
}
