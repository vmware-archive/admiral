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

package com.vmware.admiral.compute.network;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

/**
 * Service responsible to allocate and deallocate subnet CIDRs for a network.
 * To use this class:
 * <ol>
 * <li>Create a new {@link ComputeNetworkCIDRAllocationState} by issuing a POST request and
 * providing a link to a network that will be used to create isolated subnets from.</li>
 * <li>Allocate a new subnet CIDR by issuing a PATCH request and providing a
 * {@link ComputeNetworkCIDRAllocationRequest}.</li>
 * <li>Deallocate a subnet CIDR by issuing a PATCH request and providing a
 * {@link ComputeNetworkCIDRAllocationRequest}.</li>
 * </ol>
 */
public class ComputeNetworkCIDRAllocationService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.COMPUTE_NETWORK_CIDR_ALLOCATIONS;

    public static class ComputeNetworkCIDRAllocationState extends MultiTenantDocument {
        public static final String FIELD_NAME_NETWORK_LINK = "networkLink";

        /**
         * {@link com.vmware.photon.controller.model.resources.NetworkService.NetworkState} link
         */
        @Documentation(
                description = "The link of the NetworkState to be partitioned to subnet CIDRs.")
        @PropertyOptions(
                usage = { PropertyUsageOption.LINK, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String networkLink;

        @Documentation(
                description = "The number of bits identifying the subnets part (1-32).")
        public int subnetCIDRPrefixLength;

        @Documentation(description = "A map of key: subnet id; value: allocated CIDR.")
        @PropertyOptions(usage = { PropertyUsageOption.SERVICE_USE, PropertyUsageOption.OPTIONAL })
        public Map<String, String> allocatedCIDRs;

        @Documentation(description = "A list of deallocated CIDRs that are ready to be reused for"
                + " allocation.")
        @PropertyOptions(usage = { PropertyUsageOption.SERVICE_USE, PropertyUsageOption.OPTIONAL })
        public List<String> deallocatedCIDRs;
    }

    /**
     * Request to allocate a new subnet CIDR.
     */
    public static class ComputeNetworkCIDRAllocationRequest {
        public enum RequestType {
            ALLOCATE,
            DEALLOCATE
        }

        /**
         * The type of the request (allocate or deallocate).
         */
        public RequestType requestType;

        /**
         * In case of allocation request: id of the subnet that will consume the allocated CIDR.
         * In case of deallocation request: id to the subnet which CIDR will be deallocated for.
         */
        public String subnetId;

        public static ComputeNetworkCIDRAllocationRequest allocationRequest(String subnetId) {
            return new ComputeNetworkCIDRAllocationRequest(RequestType.ALLOCATE, subnetId);
        }

        public static ComputeNetworkCIDRAllocationRequest deallocationRequest(String subnetId) {
            return new ComputeNetworkCIDRAllocationRequest(RequestType.DEALLOCATE, subnetId);
        }

        private ComputeNetworkCIDRAllocationRequest(RequestType requestType, String subnetId) {
            this.requestType = requestType;
            this.subnetId = subnetId;
        }
    }

    public ComputeNetworkCIDRAllocationService() {
        super(ComputeNetworkCIDRAllocationState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation post) {
        if (!checkForBody(post)) {
            return;
        }
        ComputeNetworkCIDRAllocationState state =
                post.getBody(ComputeNetworkCIDRAllocationState.class);

        logFine(() -> "Create ComputeNetworkCIDRAllocation for network link: " + state.networkLink);

        validateStateOnCreate(state);
        state.allocatedCIDRs = new HashMap<>();
        state.deallocatedCIDRs = new LinkedList<>();

        post.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!checkForBody(patch)) {
            return;
        }

        ComputeNetworkCIDRAllocationState state = this.getState(patch);
        if (patch.getBodyRaw() instanceof ComputeNetworkCIDRAllocationState) {
            // Request to update some of the properties.
            ComputeNetworkCIDRAllocationState newState = patch.getBody
                    (ComputeNetworkCIDRAllocationState.class);

            if (state.subnetCIDRPrefixLength != newState.subnetCIDRPrefixLength) {
                validateCIDRPrefixLength(newState.subnetCIDRPrefixLength);

                if (state.allocatedCIDRs.size() > 0) {
                    String msg = "Cannot change the subnet CIDR Prefix length if there are already "
                            + "allocated subnets. Deallocate all subnets before changing the "
                            + "subnet CIDR prefix length.";

                    IllegalStateException ex = new IllegalStateException(msg);
                    patch.fail(ex);
                    return;
                }

                // No allocated subnets -> change the subnet CIDR prefix length.
                state.subnetCIDRPrefixLength = newState.subnetCIDRPrefixLength;
                state.deallocatedCIDRs.clear();
            }
            patch.setBody(state).complete();
            return;
        }

        ComputeNetworkCIDRAllocationRequest request = patch.getBody
                (ComputeNetworkCIDRAllocationRequest.class);

        if (!isValidRequest(request)) {
            patch.fail(Operation.STATUS_CODE_BAD_REQUEST);
            return;
        }

        switch (request.requestType) {
        case ALLOCATE:
            handleAllocation(patch, state, request);
            break;
        case DEALLOCATE:
            handleDeallocation(patch, state, request);
            break;
        default:
            logWarning(() -> "Unsupported request type.");
            patch.fail(Operation.STATUS_CODE_BAD_REQUEST);
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ComputeNetworkCIDRAllocationState state =
                (ComputeNetworkCIDRAllocationState) super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(state);
        state.networkLink = "/someNetworkLink";
        state.allocatedCIDRs = new HashMap<>();
        state.deallocatedCIDRs = new LinkedList<>();
        return state;
    }

    private void handleAllocation(Operation patch, ComputeNetworkCIDRAllocationState state,
            ComputeNetworkCIDRAllocationRequest request) {

        logFine(() -> "Allocate CIDR for subnet id: [" + request.subnetId + "].");

        DeferredResult.completed(new AllocationContext(request, state))
                .thenCompose(this::populateNetwork)
                .thenCompose(this::allocateCIDR)
                .whenComplete((context, throwable) -> {
                    if (throwable != null) {
                        patch.fail(throwable);
                        return;
                    }

                    patch.setBody(context.state).complete();
                });
    }

    private void handleDeallocation(Operation patch, ComputeNetworkCIDRAllocationState state,
            ComputeNetworkCIDRAllocationRequest request) {

        logFine(() -> "Deallocate subnet id: [" + request.subnetId + "].");

        deallocateCIDR(state, request);
        patch.setBody(state).complete();
    }

    private DeferredResult<AllocationContext> populateNetwork(AllocationContext context) {
        AssertUtil.assertNotNull(context.state, "context.state");

        return this.sendWithDeferredResult(
                Operation.createGet(this, context.state.networkLink), NetworkState.class)
                .thenApply(network -> {
                    context.network = network;
                    return context;
                });
    }

    private DeferredResult<AllocationContext> allocateCIDR(AllocationContext context) {
        AssertUtil.assertNotNull(context, "context");
        AssertUtil.assertNotNull(context.request, "context.request");
        AssertUtil.assertNotNull(context.state, "context.state");
        AssertUtil.assertNotNull(context.network, "context.network");

        try {
            String allocatedSubnetCIDR;
            if (context.state.deallocatedCIDRs.size() > 0) {
                // If there is a deallocated CIDR already -> use it directly.
                allocatedSubnetCIDR = context.state.deallocatedCIDRs.remove(0);
            } else {
                int subnetPrefix = context.state.subnetCIDRPrefixLength;

                // Split network CIDR into a network address and network CIDR prefix
                // Example "192.168.0.0/16" -> ["192.168.0.0", "16"]
                String[] networkCIDRSplit = context.network.subnetCIDR.split("/");
                String networkAddressAsStr = networkCIDRSplit[0];

                // Convert network address from string to integer.
                int networkAddress = ByteBuffer
                        .wrap(InetAddress.getByName(networkAddressAsStr).getAddress())
                        .getInt();
                // Convert network CIDR prefix to integer.
                int networkPrefix = Integer.parseInt(networkCIDRSplit[1]);

                if (subnetPrefix <= networkPrefix) {
                    throw new IllegalStateException("Subnet prefix is less than network prefix.");
                }

                int maxSubnets = 1 << (subnetPrefix - networkPrefix);

                if (context.state.allocatedCIDRs.size() >= maxSubnets) {
                    throw new IllegalStateException("Max number of subnets reached ("
                            + maxSubnets + "). Unable to allocate new subnet CIDR. Network Link: "
                            + context.state.networkLink);
                }

                // Calculate the next subnet CIDR based on the network address and the count of
                // already allocated subnet CIDRs.
                int subnetAddress = networkAddress +
                        (context.state.allocatedCIDRs.size() << (32 - subnetPrefix));

                // Convert subnet network address to string and append the configured subnet CIDR
                // prefix to form subnet CIDR.
                allocatedSubnetCIDR = InetAddress
                        .getByAddress(ByteBuffer.allocate(4).putInt(subnetAddress).array())
                        .getHostAddress() + "/" + subnetPrefix;

                logFine(() -> "Newly allocated CIDR: [" + allocatedSubnetCIDR + "] for subnet "
                        + "id: [" + context.request.subnetId + "].");
            }

            AssertUtil.assertTrue(!context.state.allocatedCIDRs.containsValue(allocatedSubnetCIDR),
                    "Attempt to double allocate the same subnet CIDR: [" +
                            allocatedSubnetCIDR + "].");

            // Update service document state.
            context.state.allocatedCIDRs.put(context.request.subnetId, allocatedSubnetCIDR);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
        return DeferredResult.completed(context);
    }

    private void deallocateCIDR(ComputeNetworkCIDRAllocationState state,
            ComputeNetworkCIDRAllocationRequest request) {

        AssertUtil.assertNotNull(state, "state");
        AssertUtil.assertNotNull(request, "request");

        // Update service document state.
        String deallocatedCIDR = state.allocatedCIDRs.remove(request.subnetId);
        // Store for reuse.
        if (deallocatedCIDR != null) {
            state.deallocatedCIDRs.add(deallocatedCIDR);
        }
    }

    private void validateStateOnCreate(ComputeNetworkCIDRAllocationState state) {
        AssertUtil.assertNotEmpty(state.networkLink, "networkLink");
        AssertUtil.assertTrue(state.allocatedCIDRs == null, "allocatedCIDRs");
        AssertUtil.assertTrue(state.deallocatedCIDRs == null, "deallocatedCIDRs");
        validateCIDRPrefixLength(state.subnetCIDRPrefixLength);
    }

    private void validateCIDRPrefixLength(int cidrPrefixLength) {
        AssertUtil.assertTrue(cidrPrefixLength > 0 && cidrPrefixLength < 32,
                "subnetCIDRPrefixLength");
    }

    private boolean isValidRequest(ComputeNetworkCIDRAllocationRequest request) {
        if (request == null) {
            logWarning(() -> "Request is null.");
            return false;
        }

        if (StringUtils.isEmpty(request.subnetId)) {
            logWarning(() -> "Subnet id is mandatory.");
            return false;
        }

        if (request.requestType == null) {
            logWarning(() -> "Request type is mandatory.");
            return false;
        }

        return true;
    }

    // Helper class to store allocation context variables.
    private static class AllocationContext {
        ComputeNetworkCIDRAllocationRequest request;
        ComputeNetworkCIDRAllocationState state;
        NetworkState network;

        AllocationContext(ComputeNetworkCIDRAllocationRequest request,
                ComputeNetworkCIDRAllocationState state) {
            this.request = request;
            this.state = state;
        }
    }
}
