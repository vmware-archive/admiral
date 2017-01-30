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

package com.vmware.admiral.compute.container;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Host port profile service reserves host ports for a container.
 *
 * It tracks reserved port in the port to container map.
 * When port is released, it is removed from the map.
 */
public class HostPortProfileService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.HOST_PORT_PROFILES;

    public static class HostPortProfileState extends ResourceState {
        public static final String FIELD_HOST_LINK = "hostLink";
        public static final long PROFILE_RANGE_START_PORT = Long.getLong(
                "dcp.management.host.port.profile.range.start.port", 20000);

        public static final long PROFILE_RANGE_END_PORT = Long.getLong(
                "dcp.management.host.port.profile.range.end.port", 30000);

        /** {@link ComputeState} link. */
        @Documentation(description = "The link of the ComputeState associated with this host port profile.")
        @ServiceDocument.PropertyOptions(indexing = {ServiceDocumentDescription.PropertyIndexingOption.EXPAND}, usage = {
                ServiceDocumentDescription.PropertyUsageOption.LINK, ServiceDocumentDescription.PropertyUsageOption.REQUIRED})
        public String hostLink;

        /** Host port profile range start port. */
        @ServiceDocument.Documentation(description = "Host port profile range start port.")
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.REQUIRED)
        public long startPort;

        /** Host port profile range end port. */
        @ServiceDocument.Documentation(description = "Host port profile range end port.")
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.REQUIRED)
        public long endPort;

        /** Host port to container link map. */
        @ServiceDocument.Documentation(description = "Host port to container link map.")
        public Map<Long, String> reservedPorts;
    }

    public enum HostPortProfileReservationRequestMode {
        ALLOCATE,
        RELEASE,
        UPDATE_ALLOCATION
    }

    /** An DTO used during PATCH operation in order to reserve host port. */
    public static class HostPortProfileReservationRequest {
        /**
         * Operation to perform for the {@link com.vmware.admiral.compute.container.ContainerService.ContainerState}
         */
        public HostPortProfileReservationRequestMode mode;

        /** Number of any ports to allocate. */
        public long additionalHostPortCount;

        /** {@link com.vmware.admiral.compute.container.ContainerService.ContainerState} link. */
        public String containerLink;

        /** Specific host ports to allocate. */
        public Set<Long> specificHostPorts;

    }

    public HostPortProfileService() {
        super(HostPortProfileState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!checkForBody(patch)) {
            return;
        }

        if (patch.getBodyRaw() instanceof HostPortProfileState) {
            HostPortProfileState hostPortProfileState = patch.getBody(HostPortProfileState.class);
            validate(hostPortProfileState);
            setState(patch, hostPortProfileState);
            patch.setBody(null).complete();
            return;
        }

        HostPortProfileReservationRequest request = patch.getBody(HostPortProfileReservationRequest.class);

        assertNotEmpty(request.containerLink, "containerLink");
        assertNotNull(request.mode, "mode");
        HostPortProfileState hostPortProfileState = getState(patch);
        switch (request.mode) {
        case ALLOCATE:
            allocatePorts(hostPortProfileState, request);
            break;
        case RELEASE:
            releasePorts(hostPortProfileState, request);
            break;
        case UPDATE_ALLOCATION:
            updatePortAllocationForContainer(hostPortProfileState, request);
            break;
        default:
            throw new UnsupportedOperationException("This operation is not supported: " + request.mode);
        }
        patch.setBody(hostPortProfileState);
        patch.complete();
    }

    public static String getHostPortProfileLink(String hostLink) {
        return UriUtils.buildUriPath(
                HostPortProfileService.FACTORY_LINK, Service.getId(hostLink));
    }

    public static Set<Long> getAllocatedPorts(HostPortProfileState profile, String containerLink) {
        return profile.reservedPorts.entrySet()
                .stream()
                .filter(p -> p.getValue().equals(containerLink))
                .map(k -> k.getKey())
                .collect(Collectors.toSet());
    }

    /**
     * Update container port allocation.
     * Mark container ports as allocated and release ports that are not used anymore
     */
    private void updatePortAllocationForContainer(HostPortProfileState state, HostPortProfileReservationRequest request) {
        Set<Long> previousPorts = getAllocatedPorts(state, request.containerLink);
        // First remove all ports, this will remove ports that are not allocated anymore
        releasePorts(state, request);
        // Second mark ports allocated
        allocateSpecificPorts(state, request);
        logInfo("Updating port allocation from [%s] to [%s] for container [%s] and profile [%s]",
                previousPorts,
                getAllocatedPorts(state, request.containerLink),
                request.containerLink,
                state.documentSelfLink);
    }

    /** Release all ports for HostPortProfileReservationRequest. */
    private void releasePorts(HostPortProfileState state, HostPortProfileReservationRequest request) {
        logInfo("Releasing ports [%s] for container [%s] and profile [%s].",
                getAllocatedPorts(state, request.containerLink),
                request.containerLink,
                state.documentSelfLink);
        // remove container host ports from reserved ports
        state.reservedPorts.entrySet()
                .removeIf(p -> request.containerLink.equals(p.getValue()));
    }

    /** Allocate all ports for HostPortProfileReservationRequest. */
    private void allocatePorts(HostPortProfileState state,
                               HostPortProfileReservationRequest request) {
        allocateSpecificPorts(state, request);
        allocateAdditionalPorts(state, request);

        logInfo("Allocating ports [%s] for container [%s] and profile [%s].",
                getAllocatedPorts(state, request.containerLink),
                request.containerLink,
                state.documentSelfLink);
    }

    /** Allocate a number of any available ports. */
    private void allocateAdditionalPorts(HostPortProfileState state,
                                         HostPortProfileReservationRequest request) {
        long statIndex = state.startPort;

        for (long i = 0; i < request.additionalHostPortCount; i++) {
            Long allocatedPort = null;
            for (long k = statIndex; k < state.endPort; k++) {
                if (!state.reservedPorts.containsKey(k)) {
                    allocatedPort = k;
                    state.reservedPorts.put(allocatedPort, request.containerLink);
                    // start from the next index for the next allocation
                    statIndex = k + 1;
                    break;
                }
            }
            if (allocatedPort == null) {
                LocalizableValidationException exception =
                        new LocalizableValidationException(
                                "Unable to allocate hostPort. There are no available ports left.",
                                "compute.host.port.unavailable");
                throw exception;
            }
        }
    }

    /** Allocate specific ports. */
    private void allocateSpecificPorts(HostPortProfileState state,
                                       HostPortProfileReservationRequest request) {
        if (request.specificHostPorts == null) {
            return;
        }

        request.specificHostPorts.forEach(p -> state.reservedPorts.put(p, request.containerLink));
    }

    @Override
    public void handleCreate(Operation start) {
        if (!checkForBody(start)) {
            return;
        }

        HostPortProfileState state = start.getBody(HostPortProfileState.class);
        if (state.startPort == 0) {
            // Set start port to random number to minimize race condition
            // when the same Docker host used by multiple instances of vRA
            state.startPort = (long) ((Math.random() * 1000 +
                    HostPortProfileState.PROFILE_RANGE_START_PORT));
        }
        if (state.endPort == 0) {
            state.endPort = HostPortProfileState.PROFILE_RANGE_END_PORT;
        }

        if (state.reservedPorts == null) {
            state.reservedPorts = new LinkedHashMap<>();
        }

        validate(state);
        start.complete();
    }

    @Override
    public void handlePut(Operation op) {
        if (op.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            // converted PUT due to IDEMPOTENT_POST option
            logInfo("HostPortProfile is already created. Ignoring converted PUT.");
            op.complete();
            return;
        }

        if (!checkForBody(op)) {
            return;
        }

        HostPortProfileState hostPortProfileState = op.getBody(HostPortProfileState.class);
        validate(hostPortProfileState);

        this.setState(op, hostPortProfileState);
        op.setBody(null).complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        HostPortProfileState template = (HostPortProfileState) super.getDocumentTemplate();
        template.reservedPorts = new HashMap<>();

        return template;
    }

    private void validate(HostPortProfileState state) {
        if (state.startPort < 0) {
            throw new IllegalArgumentException(
                    "'startPort' must be greater or eq to zero.");
        }

        if (state.endPort < state.startPort) {
            throw new IllegalArgumentException(
                    "'endPort' must be greater or eq to startPort.");
        }

        Utils.validateState(getStateDescription(), state);
    }
}
