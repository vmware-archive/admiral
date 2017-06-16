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

package com.vmware.admiral.compute.container.loadbalancer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Container load balancer service
 */
public class ContainerLoadBalancerService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.CONTAINER_LOAD_BALANCERS;

    public static final int MIN_PORT_NUMBER = 1;
    public static final int MAX_PORT_NUMBER = 65535;

    /**
     * Represents an instance of a container load balancer.
     */
    @JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
    public static class ContainerLoadBalancerState extends ResourceState {

        /**
         * Link to the load balancer definition.
         */
        @Documentation(description = "Defines the description of the load balancer.")
        @PropertyOptions(usage = { ServiceDocumentDescription.PropertyUsageOption.LINK,
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT })
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE)
        public String descriptionLink;

        @Documentation(description = "Link to CompositeComponent when a load balancer is part of "
                + "App/Composition request.")
        @PropertyOptions(usage = { ServiceDocumentDescription.PropertyUsageOption.OPTIONAL,
                ServiceDocumentDescription.PropertyUsageOption.LINK })
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE)
        public String compositeComponentLink;

        /**
         * Frontend endpoints of the load balancer
         */
        @Documentation(description = "Frontend endpoints of the load balancer.")
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.REQUIRED)
        public List<ContainerLoadBalancerFrontendDescription> frontends;

        /**
         * A list of services (in a blueprint) the load balancer depends on
         */
        @Documentation(description = "A list of services (in a blueprint) the load balancer depends"
                + " on.")
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.OPTIONAL)
        public String[] links;

        /**
         * Port bindings in the format ip:hostPort:containerPort | ip::containerPort |
         * hostPort:containerPort | containerPort where range of ports can also be provided
         */
        @JsonProperty("ports")
        @Documentation(description = "Port bindings in the format: "
                + "ip:hostPort:containerPort | ip::containerPort | hostPort:containerPort | containerPort"
                + " where range of ports can also be provided.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public PortBinding[] portBindings;

        /**
         * Joined networks and the configuration with which they are joined.
         */
        @Documentation(description = "Joined networks.")
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.OPTIONAL)
        public List<ServiceNetwork> networks;
    }

    public ContainerLoadBalancerService() {
        super(ContainerLoadBalancerState.class);

        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation start) {
        processInput(start);
        start.complete();
    }

    @Override
    public void handlePut(Operation put) {
        ContainerLoadBalancerState returnState = processInput(put);
        setState(put, returnState);
        put.complete();
    }

    private ContainerLoadBalancerState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ContainerLoadBalancerState state = op.getBody(ContainerLoadBalancerState.class);
        validateState(state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        ContainerLoadBalancerState currentState = getState(patch);
        ContainerLoadBalancerState patchBody = patch.getBody
                (ContainerLoadBalancerState.class);

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);

        PropertyUtils.mergeServiceDocuments(currentState, patchBody);

        validateState(currentState);
        String newSignature = Utils.computeSignature(currentState, docDesc);
        if (currentSignature.equals(newSignature)) {
            currentState = null;
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }
        patch.setBody(currentState).complete();
    }

    @Override
    public ContainerLoadBalancerState getDocumentTemplate() {
        ContainerLoadBalancerState template = (ContainerLoadBalancerState) super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(template);
        template.name = "name (string)";
        template.frontends = new ArrayList<>();
        ContainerLoadBalancerFrontendDescription frontend = new
                ContainerLoadBalancerFrontendDescription();
        frontend.port = 80;
        frontend.backends = new ArrayList<>();
        ContainerLoadBalancerBackendDescription backend = new
                ContainerLoadBalancerBackendDescription();
        backend.service = "service (string)";
        backend.port = 8080;
        frontend.backends.add(backend);
        template.frontends.add(frontend);
        frontend.healthConfig = new ContainerLoadBalancerHealthConfig();
        frontend.healthConfig.protocol = "http";
        frontend.healthConfig.path = "/test";
        frontend.healthConfig.port = 8088;
        template.links = new String[] { "service:alias" };
        template.portBindings = Arrays.stream(new String[] {
                "80:80",
                "127.0.0.1::20080",
                "127.0.0.1:20080:880",
                "1234:1234/tcp" })
                .map((s) -> PortBinding.fromDockerPortMapping(DockerPortMapping.fromString(s)))
                .collect(Collectors.toList())
                .toArray(new PortBinding[0]);
        template.networks = new ArrayList<>();
        ServiceNetwork network = new ServiceNetwork();
        network.name = "network";
        template.networks.add(network);
        template.customProperties = new HashMap<>(1);
        template.customProperties.put("key (string)", "value (string)");
        return template;
    }

    private void validateState(ContainerLoadBalancerState state) {
        Utils.validateState(getStateDescription(), state);
        Set<String> resolvableLinks = new HashSet<>();
        if (state.links != null && state.links.length > 0) {
            for (String link : state.links) {
                if (link == null || link.trim().isEmpty()) {
                    continue;
                }
                String[] linkDesc = link.split(":");
                resolvableLinks.add(linkDesc[0]);
            }
        }
        state.frontends.forEach(frontend -> {
            validatePort(frontend.port, "Invalid load balancer frontend port number.");
            if (frontend.healthConfig != null && (frontend.healthConfig.protocol == null ||
                    frontend.healthConfig.port == null || frontend.healthConfig.path == null)) {
                frontend.healthConfig = null;
            }
            if (frontend.healthConfig != null && frontend.healthConfig.port != null) {
                validatePort(frontend.healthConfig.port,
                        "Invalid load balancer health config port number.");
            }
            frontend.backends.forEach(backend -> {
                validatePort(backend.port, "Invalid load balancer backend port number.");
                if (!resolvableLinks.contains(backend.service)) {
                    throw new IllegalArgumentException("Invalid service link.");
                }
            });
        });
    }

    private void validatePort(int port, String errMessage) {
        if (port < MIN_PORT_NUMBER || port > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException(errMessage);
        }
    }
}
