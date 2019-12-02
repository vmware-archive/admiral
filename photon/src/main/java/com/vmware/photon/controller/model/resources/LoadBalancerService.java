/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.resources;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.HealthCheckConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.Protocol;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.RouteConfiguration;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Represents the actual state of a load balancer.
 */
public class LoadBalancerService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_LOAD_BALANCERS;

    public static final String FIELD_NAME_DESCRIPTION_LINKS = "descriptionLink";
    public static final String FIELD_NAME_ENDPOINT_LINK = PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK;
    public static final String FIELD_NAME_COMPUTE_LINKS = "computeLinks";
    public static final String FIELD_NAME_SUBNET_LINKS = "subnetLinks";
    public static final String FIELD_NAME_SECURITY_GROUP_LINKS = "securityGroupLinks";
    public static final String FIELD_NAME_ROUTES = "routes";
    public static final String FIELD_NAME_ROUTES_PROTOCOL = "protocol";
    public static final String FIELD_NAME_ROUTES_PORT = "port";
    public static final String FIELD_NAME_ROUTES_INSTANCE_PROTOCOL = "instanceProtocol";
    public static final String FIELD_NAME_ROUTES_INSTANCE_PORT = "instancePort";
    public static final String FIELD_NAME_INTERNET_FACING = "internetFacing";
    public static final String FIELD_NAME_ADDRESS = "address";

    public static final int MIN_PORT_NUMBER = 1;
    public static final int MAX_PORT_NUMBER = 65535;

    /**
     * Represents the state of a load balancer.
     */
    public static class LoadBalancerState extends ResourceState {
        /**
         * Link to the desired state of the load balancer, if any.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String descriptionLink;

        /**
         * Link to the cloud account endpoint the load balancer belongs to.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String endpointLink;

        /**
         * Links to the load balanced instances.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> computeLinks;

        /**
         * List of subnets the load balancer is attached to. Typically these must be in different
         * availability zones, and have nothing to do with the subnets the cluster instances are
         * attached to.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> subnetLinks;

        /**
         * Security groups applied on the load balancer. If not specified, a default one can be
         * created by the adapter.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_23)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public List<String> securityGroupLinks;

        /**
         * Internet-facing load balancer or an internal load balancer
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_19)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Boolean internetFacing;

        /**
         * Routing configuration between the load balancer and the back-end instances.
         *
         * <p>{@code PATCH} merging strategy: if not {@code NULL} in the patch body, the current
         * value is replaced by the one given in the patch request (no advanced per-item merging).
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_19)
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public List<RouteConfiguration> routes;

        /**
         * The address of this load balancer instance.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_19)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String address;

        /**
         * The adapter to use to create the load balancer instance.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI instanceAdapterReference;

        @Override
        public void copyTo(ResourceState target) {
            super.copyTo(target);
            if (target instanceof LoadBalancerState) {
                LoadBalancerState targetState = (LoadBalancerState) target;
                targetState.descriptionLink = this.descriptionLink;
                targetState.endpointLink = this.endpointLink;
                targetState.regionId = this.regionId;
                targetState.computeLinks = this.computeLinks;
                targetState.subnetLinks = this.subnetLinks;
                targetState.routes = this.routes;
                targetState.instanceAdapterReference = this.instanceAdapterReference;
                targetState.internetFacing = this.internetFacing;
                targetState.address = this.address;
                targetState.securityGroupLinks = this.securityGroupLinks;
            }
        }
    }

    /**
     * Load balancer state with all links expanded.
     */
    public static class LoadBalancerStateExpanded extends LoadBalancerState {
        public LoadBalancerDescription description;
        public EndpointState endpointState;
        public Set<ComputeState> computes;
        public Set<SubnetState> subnets;

        public static URI buildUri(URI loadBalancerStateUri) {
            return UriUtils.buildExpandLinksQueryUri(loadBalancerStateUri);
        }
    }

    public LoadBalancerService() {
        super(LoadBalancerState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleGet(Operation get) {
        LoadBalancerState currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        LoadBalancerStateExpanded expanded = new LoadBalancerStateExpanded();
        currentState.copyTo(expanded);

        DeferredResult
                .allOf(
                        getDr(currentState.descriptionLink, LoadBalancerDescription.class)
                                .thenAccept(description -> expanded.description = description),
                        getDr(currentState.endpointLink, EndpointState.class)
                                .thenAccept(endpointState -> expanded.endpointState = endpointState),
                        getDr(currentState.computeLinks, ComputeState.class, HashSet::new)
                                .exceptionally(e -> {
                                    logWarning("Error retrieving compute states: %s", e.toString());
                                    return new HashSet<>();
                                }).thenAccept(computes -> expanded.computes = computes),
                        getDr(currentState.subnetLinks, SubnetState.class, HashSet::new)
                                .thenAccept(subnets -> expanded.subnets = subnets))
                .whenComplete((ignore, e) -> {
                    if (e != null) {
                        get.fail(e);
                    } else {
                        get.setBody(expanded).complete();
                    }
                });
    }

    @Override
    public void handleStart(Operation start) {
        processInput(start);
        start.complete();
    }

    @Override
    public void handlePut(Operation put) {
        LoadBalancerState returnState = processInput(put);
        returnState.copyTenantLinks(getState(put));
        setState(put, returnState);
        put.complete();
    }

    @Override
    public void handlePost(Operation post) {
        LoadBalancerState returnState = processInput(post);
        setState(post, returnState);
        post.complete();
    }

    private LoadBalancerState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        LoadBalancerState state = op.getBody(LoadBalancerState.class);
        validateState(state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        LoadBalancerState currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                LoadBalancerState.class, op -> {
                    LoadBalancerState patchBody = op.getBody(LoadBalancerState.class);
                    boolean hasChanged = false;

                    // if routes are passed, they override the current ones
                    if (patchBody.routes != null) {
                        hasChanged = true;
                        currentState.routes = patchBody.routes;
                    }

                    if (patchBody.securityGroupLinks != null) {
                        if (currentState.securityGroupLinks == null) {
                            currentState.securityGroupLinks = patchBody.securityGroupLinks;
                            hasChanged = true;
                        } else {
                            for (String link : patchBody.securityGroupLinks) {
                                if (!currentState.securityGroupLinks.contains(link)) {
                                    currentState.securityGroupLinks.add(link);
                                    hasChanged = true;
                                }
                            }
                        }
                    }

                    return Boolean.valueOf(hasChanged);
                });
    }

    private void validateState(LoadBalancerState state) {
        Utils.validateState(getStateDescription(), state);
        PhotonModelUtils.validateRegionId(state);
        LoadBalancerDescriptionService.validateRoutes(state.routes);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        LoadBalancerState template = (LoadBalancerState) td;

        template.id = UUID.randomUUID().toString();
        template.descriptionLink = "lb-description-link";
        template.name = "load-balancer";
        template.endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK,
                "my-endpoint");
        template.internetFacing = Boolean.TRUE;
        template.address = "my-address";

        RouteConfiguration routeConfiguration = new RouteConfiguration();
        routeConfiguration.protocol = Protocol.HTTP.name();
        routeConfiguration.port = "80";
        routeConfiguration.instanceProtocol = Protocol.HTTP.name();
        routeConfiguration.instancePort = "80";
        routeConfiguration.healthCheckConfiguration = new HealthCheckConfiguration();
        routeConfiguration.healthCheckConfiguration.protocol = Protocol.HTTP.name();
        routeConfiguration.healthCheckConfiguration.port = "80";
        template.routes = Arrays.asList(routeConfiguration);

        return template;
    }

    private <T> DeferredResult<T> getDr(String link, Class<T> type) {
        if (link == null) {
            return DeferredResult.completed(null);
        }
        return sendWithDeferredResult(Operation.createGet(this, link), type);
    }

    private <T, C extends Collection<T>> DeferredResult<C> getDr(Collection<String> links,
            Class<T> type, Supplier<C> collectionFactory) {
        if (links == null) {
            return DeferredResult.completed(null);
        }
        return DeferredResult
                .allOf(links.stream().map(link -> getDr(link, type)).collect(Collectors.toList()))
                .thenApply(items -> items.stream()
                        .collect(Collectors.toCollection(collectionFactory)));
    }
}
