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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.request.compute.LoadBalancerAllocationTaskService.LoadBalancerAllocationTaskState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
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
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
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
            FIND_ADAPTER,
            ENHANCE_DESCRIPTION,
            QUERY_COMPUTE_STATES,
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

        switch (state.taskSubStage) {
        case CREATED:
            retrieveEndpointDetails(state).thenAccept(endpointDetails -> {
                proceedTo(LoadBalancerAllocationTaskState.SubStage.FIND_ADAPTER, s -> {
                    s.endpointLink = endpointDetails.get(0);
                    s.endpointType = endpointDetails.get(1);
                    s.regionId = endpointDetails.get(2);
                });
            });
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
                proceedTo(LoadBalancerAllocationTaskState.SubStage.CREATE_LB_STATE, s -> {
                    s.computeLinks = links;
                });
            });
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
        lbState.name = this.lbDescription.name;
        lbState.protocol = this.lbDescription.protocol;
        lbState.port = this.lbDescription.port;
        lbState.instanceProtocol = this.lbDescription.instanceProtocol;
        lbState.instancePort = this.lbDescription.instancePort;
        lbState.internetFacing = this.lbDescription.internetFacing;
        lbState.computeLinks = state.computeLinks;
        if (this.lbDescription.subnetLinks != null) {
            lbState.subnetLinks = this.lbDescription.subnetLinks;
        } else {
            // TODO: populate subnet from the network profile
            lbState.subnetLinks = Collections.singleton(SubnetService.FACTORY_LINK + "/dummy-subnet");
        }
        lbState.endpointLink = this.lbDescription.endpointLink;
        lbState.regionId = this.lbDescription.regionId;
        lbState.instanceAdapterReference = this.lbDescription.instanceAdapterReference;

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
