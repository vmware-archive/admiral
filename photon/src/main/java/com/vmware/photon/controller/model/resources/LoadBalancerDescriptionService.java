/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.HealthCheckConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.Protocol;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.RouteConfiguration;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Represents the desired state of a load balancer.
 */
public class LoadBalancerDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_LOAD_BALANCER_DESCRIPTIONS;

    public static final String FIELD_NAME_ENDPOINT_LINK = PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK;
    public static final String FIELD_NAME_COMPUTE_DESCRIPTION_LINK = "computeDescriptionLink";
    public static final String FIELD_NAME_SUBNET_LINKS = "subnetLinks";
    public static final String FIELD_NAME_SECURITY_GROUP_LINKS = "securityGroupLinks";
    public static final String FIELD_NAME_ROUTES = "routes";
    public static final String FIELD_NAME_ROUTES_PROTOCOL = "protocol";
    public static final String FIELD_NAME_ROUTES_PORT = "port";
    public static final String FIELD_NAME_ROUTES_INSTANCE_PROTOCOL = "instanceProtocol";
    public static final String FIELD_NAME_ROUTES_INSTANCE_PORT = "instancePort";
    public static final String FIELD_NAME_INTERNET_FACING = "internetFacing";

    public static final int MIN_PORT_NUMBER = 1;
    public static final int MAX_PORT_NUMBER = 65535;

    /**
     * Represents the desired state of a load balancer.
     */
    public static class LoadBalancerDescription extends ResourceState {
        /**
         * Non-exhaustive list of commonly used protocols for routing and health checks.
         */
        public static enum Protocol {
            HTTP,
            HTTPS,
            TCP,
            UDP
        }

        /**
         * Represents a load balancer configuration for checking the health of the load-balanced
         * back-end instances.
         */
        public static class HealthCheckConfiguration {
            /**
             * (Required) Protocol used for the health check. String representation of
             * {@link LoadBalancerDescription.Protocol} values can be used or a custom string that
             * will be sent to the cloud provider.
             */
            public String protocol;

            /**
             * (Required) Port on the back-end instance machine to use for the health check.
             */
            public String port;

            /**
             * (Optional) URL path on the back-end instance against which a {@code GET} request will
             * be performed for the health check. Useful when the health check protocol is
             * HTTP/HTTPS.
             */
            public String urlPath;

            /**
             * (Optional) Interval (in seconds) at which the health checks will be performed. If
             * not specified, a provider-default will be used.
             */
            public Integer intervalSeconds;

            /**
             * (Optional) Timeout (in seconds) to wait for a response from the back-end instance. If
             * not specified, a provider-default will be used.
             */
            public Integer timeoutSeconds;

            /**
             * (Optional) Number of consecutive check failures before considering a particular
             * back-end instance as unhealthy. If not specified, a provider-default will be used.
             */
            public Integer unhealthyThreshold;

            /**
             * (Optional) Number of consecutive successful checks before considering a particular
             * back-end instance as healthy. If not specified, a provider-default will be used.
             */
            public Integer healthyThreshold;
        }

        /**
         * Represents a configuration for routing incoming requests to the back-end instances.
         * A load balancer may support multiple such configurations.
         */
        public static class RouteConfiguration {
            /**
             * (Required) Front-end (incoming) protocol. String representation of
             * {@link LoadBalancerDescription.Protocol} values can be used or a custom string that
             * will be sent to the cloud provider.
             */
            public String protocol;

            /**
             * (Required) Front-end (incoming) port where the load balancer is listening to.
             */
            public String port;

            /**
             * (Required) Back-end instance protocol. String representation of
             * {@link LoadBalancerDescription.Protocol} values can be used or a custom string that
             * will be sent to the cloud provider.
             */
            public String instanceProtocol;

            /**
             * (Required) Back-end instance port where the traffic is routed to.
             */
            public String instancePort;

            /**
             * (Optional) Health check configuration for this route configuration. Note that some
             * providers may only support a single health check configuration even if there are
             * multiple route configurations. In that case, it is up to the provider to pick the
             * health configuration to use.
             */
            public HealthCheckConfiguration healthCheckConfiguration;
        }

        /**
         * Link to the cloud account endpoint the load balancer belongs to.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String endpointLink;

        /**
         * Link to the description of the instance cluster.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String computeDescriptionLink;

        /**
         * Name of the network the load balancer is attached to. Similar to the {@code name} field
         * in {@code NetworkInterfaceDescription} that is used to find a network for each NIC on
         * the compute.
         *
         * Overridden by the {@link #subnetLinks} field, if set. One of the two fields must be set.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_19)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String networkName;

        /**
         * List of subnets the load balancer is attached to. Typically these must be in different
         * availability zones, and have nothing to do with the subnets the cluster instances are
         * attached to.
         *
         * If not set, the subnets are determined based on the {@link #networkName} field. One of
         * the two fields must be set.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> subnetLinks;

        /**
         * Optional list of security groups to apply on the load balancer.
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
         * The adapter to use to create the load balancer instance.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI instanceAdapterReference;
    }

    public LoadBalancerDescriptionService() {
        super(LoadBalancerDescription.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleStart(Operation start) {
        processInput(start);
        start.complete();
    }

    @Override
    public void handlePut(Operation put) {
        LoadBalancerDescription returnState = processInput(put);
        returnState.copyTenantLinks(getState(put));
        setState(put, returnState);
        put.complete();
    }

    @Override
    public void handlePost(Operation post) {
        LoadBalancerDescription returnState = processInput(post);
        setState(post, returnState);
        post.complete();
    }

    private LoadBalancerDescription processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        LoadBalancerDescription state = op.getBody(LoadBalancerDescription.class);
        validateState(state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        LoadBalancerDescription currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                LoadBalancerDescription.class, op -> {
                    LoadBalancerDescription patchBody = op.getBody(LoadBalancerDescription.class);
                    boolean hasChanged = false;

                    if (patchBody.regionId != null && currentState.regionId == null) {
                        hasChanged = true;
                        currentState.regionId = patchBody.regionId;
                    }

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

    private void validateState(LoadBalancerDescription state) {
        Utils.validateState(getStateDescription(), state);
        AssertUtil.assertTrue((state.networkName == null) != (state.subnetLinks == null),
                "Either networkName or subnetLinks must be set.");
        validateRoutes(state.routes);
    }

    static void validateRoutes(List<RouteConfiguration> routes) {
        if (routes != null) {
            routes.forEach(LoadBalancerDescriptionService::validateRoute);
        }
    }

    private static void validateRoute(RouteConfiguration route) {
        AssertUtil.assertNotNull(route, "A route configuration must not be null");
        AssertUtil.assertNotEmpty(route.protocol, "No protocol provided in route configuration");
        AssertUtil.assertNotEmpty(route.port, "No port provided in route configuration");
        AssertUtil.assertNotEmpty(route.instanceProtocol,
                "No instance protocol provided in route configuration");
        AssertUtil.assertNotEmpty(route.instancePort,
                "No instance port provided in route configuration");
        validatePort(route.port);
        validatePort(route.instancePort);

        if (route.healthCheckConfiguration != null) {
            validateHealthCheck(route.healthCheckConfiguration);
        }
    }

    private static void validateHealthCheck(HealthCheckConfiguration config) {
        AssertUtil.assertNotEmpty(config.protocol,
                "No protocol provided in health check configuration");
        AssertUtil.assertNotEmpty(config.port, "No port provided in health check configuration");
        validatePort(config.port);
    }

    private static void validatePort(String port) {
        int portNumber = Integer.parseInt(port);
        AssertUtil.assertTrue(portNumber >= MIN_PORT_NUMBER && portNumber <= MAX_PORT_NUMBER,
                "Invalid port number: %d." + portNumber);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        LoadBalancerDescription template = (LoadBalancerDescription) td;

        template.id = UUID.randomUUID().toString();
        template.name = "load-balancer";
        template.endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK,
                "my-endpoint");
        template.networkName = "lb-net";

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
}
