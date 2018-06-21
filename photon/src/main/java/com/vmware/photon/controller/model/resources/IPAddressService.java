/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.resources;

import static com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState.DEFAULT_IP_VERSION;

import java.util.UUID;

import io.netty.util.internal.StringUtil;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState.IPAddressStatus;
import com.vmware.photon.controller.model.support.IPVersion;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.photon.controller.model.util.SubnetValidator;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a statically assigned ip address from a pre-defined subnet range.
 *
 * @see SubnetRangeService.SubnetRangeState
 */
public class IPAddressService extends StatefulService {
    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/ip-addresses";

    /**
     * Represents the state of an ip address.
     */
    public static class IPAddressState extends ResourceState {

        public static final String FIELD_NAME_SUBNET_RANGE_LINK = "subnetRangeLink";
        public static final String FIELD_NAME_IP_ADDRESS_STATUS = "ipAddressStatus";
        public static final String FIELD_NAME_CONNECTED_RESOURCE_LINK = "connectedResourceLink";

        // Default values for non-required fields
        public static final IPVersion DEFAULT_IP_VERSION = IPVersion.IPv4;

        public enum IPAddressStatus {
            ALLOCATED, // IP is allocated
            RELEASED,  // IP is no longer allocated, but still not available to be re-allocated
            AVAILABLE;  // IP is available for allocation, this is an intermediate state before the IPAddressState is being deleted

            /**
             * Allocated IPs should be in 'released' state before becoming 'available' again for allocation.
             * This method validates the status transitions.
             *
             * @param currentStatus current IPAddressStatus
             * @param newStatus     IPAddressStatus to transition to
             * @return true if the transition is valid
             */
            static boolean isValidTransition(IPAddressStatus currentStatus,
                    IPAddressStatus newStatus) {
                return (currentStatus != null && currentStatus.equals(newStatus) ||
                        (AVAILABLE.equals(currentStatus) && ALLOCATED.equals(newStatus)) ||
                        (ALLOCATED.equals(currentStatus) && RELEASED.equals(newStatus)) ||
                        (RELEASED.equals(currentStatus) && AVAILABLE.equals(newStatus)));
            }
        }

        /**
         * Link to the subnet range this IP belongs to.
         */
        @Documentation(description = "Link to the parent subnet range.")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.OPTIONAL,
                ServiceDocumentDescription.PropertyUsageOption.LINK,
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT
                })
        public String subnetRangeLink;

        /**
         * Link to the resource this IP is assigned to.
         */
        @Documentation(description = "Link to the resource this IP is assigned to.")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.OPTIONAL,
                ServiceDocumentDescription.PropertyUsageOption.LINK,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL
                })
        public String connectedResourceLink;

        /**
         * Ip address.
         */
        @Documentation(description = "IP address")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.REQUIRED,
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT
                })
        public String ipAddress;

        /**
         * Whether the start and end ip address is IPv4 or IPv6.
         * If not set, default to IPv4.
         */
        @Documentation(description = "IP address version: IPv4 or IPv6. Default: IPv4")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.OPTIONAL,
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT
                })
        public IPVersion ipVersion;

        /**
         * The state of the IP address.
         */
        @Documentation(description = "IP address status: ALLOCATED, RELEASED or AVAILABLE")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.REQUIRED
                })
        public IPAddressStatus ipAddressStatus;

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("name: ").append(this.name);
            sb.append(", id: ").append(this.id);
            sb.append(", subnet range link: ").append(this.subnetRangeLink);
            sb.append(", resource link: ").append(this.connectedResourceLink);
            sb.append(", IP address: ").append(this.ipAddress);
            sb.append(", IP version: ").append(this.ipVersion);
            sb.append(", status: ").append(this.ipAddressStatus);

            return sb.toString();
        }
    }

    public IPAddressService() {
        super(IPAddressState.class);
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
    public void handlePost(Operation post) {
        IPAddressState returnState = processInput(post);
        setState(post, returnState);
        post.complete();
    }

    @Override
    public void handlePut(Operation put) {
        IPAddressState newState = processInput(put);

        // Verify valid status changes
        IPAddressState currentState = getState(put);
        validateIPAddressStatusTransition(currentState, newState);
        setState(put, newState);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }

        IPAddressState currentState = getState(patch);

        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                IPAddressState.class, op -> {
                    IPAddressState patchState = patch.getBody(IPAddressState.class);
                    boolean hasChanged = false;

                    // Verify valid status changes
                    if (patchState.ipAddressStatus != null
                            && patchState.ipAddressStatus != currentState.ipAddressStatus) {
                        validateIPAddressStatusTransition(currentState, patchState);
                        currentState.ipAddressStatus = patchState.ipAddressStatus;
                        validateIPAddressStatusWithConnectedResource(currentState);
                        hasChanged = true;
                    }

                    return Boolean.valueOf(hasChanged);
                });
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        IPAddressState template = (IPAddressState) td;

        template.id = UUID.randomUUID().toString();
        template.name = "ip-address";

        return template;
    }

    /**
     * @param op operation
     * @return a valid IPAddressState
     * @throws IllegalArgumentException if input invalid
     */
    private IPAddressState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        IPAddressState state = op.getBody(IPAddressState.class);
        validateState(state);
        return state;
    }

    /**
     * Validation upon creation of an IP address object.
     * - valid IP address
     * No need to validate that the IP is within the range.
     *
     * @param state IpAddressState to validate
     * @throws IllegalArgumentException for invalid state
     */
    private void validateState(IPAddressState state) {
        // Verify values based on the document description
        Utils.validateState(getStateDescription(), state);

        if (state.ipVersion == null) {
            state.ipVersion = DEFAULT_IP_VERSION;
        }

        if (!SubnetValidator.isValidIPAddress(state.ipAddress, state.ipVersion)) {
            throw new LocalizableValidationException(String.format("Invalid IP address: %s",
                    state.ipAddress),
                    "ip.address.invalid", state.ipAddress);
        }

        validateIPAddressStatusWithConnectedResource(state);

        logFine("Completed validation of IPAddressState: " + state);
    }

    /**
     * @param currentState current IP address
     * @param desiredState requested IP address
     * @throws IllegalArgumentException if an invalid transition
     */
    private void validateIPAddressStatusTransition(IPAddressState currentState,
            IPAddressState desiredState) {
        AssertUtil.assertTrue(IPAddressStatus
                        .isValidTransition(currentState.ipAddressStatus, desiredState.ipAddressStatus),
                String.format("Invalid IP address status transition from [%s] to [%s]",
                        currentState.ipAddressStatus, desiredState.ipAddressStatus));
    }

    /**
     * Validate connectedResourceLink is set if IP address is ALLOCATED and not set otherwise
     *
     * @param ipAddressState
     */
    private void validateIPAddressStatusWithConnectedResource(IPAddressState ipAddressState) {
        AssertUtil.assertFalse(ipAddressState.ipAddressStatus == IPAddressStatus.ALLOCATED
                        && StringUtil.isNullOrEmpty(ipAddressState.connectedResourceLink),
                "ConnectedResourceLink is required if IP address status is ALLOCATED");
        AssertUtil.assertFalse((ipAddressState.ipAddressStatus == IPAddressStatus.RELEASED
                        || ipAddressState.ipAddressStatus == IPAddressStatus.AVAILABLE)
                        && !StringUtil.isNullOrEmpty(ipAddressState.connectedResourceLink),
                "ConnectedResourceLink must be null if IP address status is RELEASED");
    }
}