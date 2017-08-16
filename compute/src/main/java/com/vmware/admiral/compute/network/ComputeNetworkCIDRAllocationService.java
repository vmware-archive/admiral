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

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.photon.controller.model.query.QueryStrategy;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.support.LifecycleState;
import com.vmware.photon.controller.model.util.IpHelper;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.services.common.QueryTask.Query;

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

        @Documentation(description = "A map of key: subnet id; value: allocated CIDR.")
        @PropertyOptions(usage = { PropertyUsageOption.SERVICE_USE, PropertyUsageOption.OPTIONAL })
        public Map<String, String> allocatedCIDRs;
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
         * In case of allocation request: link to the subnet that will consume the allocated CIDR.
         * In case of deallocation request: link to the subnet which CIDR will be deallocated
         * for.
         */
        public String subnetLink;

        /**
         * In case of allocation request: the desired allocated subnet CIDR length;
         */
        public Integer subnetPrefixLength;

        /**
         * In case of allocation request: Optional isolation network CIDR.
         * Should be provided in case the parent network doesn't
         * have CIDR itself.
         */
        public String networkCIDR;

        public static ComputeNetworkCIDRAllocationRequest allocationRequest(String subnetLink,
                int subnetPrefixLength) {
            return new ComputeNetworkCIDRAllocationRequest(RequestType.ALLOCATE, subnetLink,
                    subnetPrefixLength, null);
        }

        public static ComputeNetworkCIDRAllocationRequest allocationRequest(String subnetLink,
                int subnetPrefixLength, String networkCIDR) {
            return new ComputeNetworkCIDRAllocationRequest(RequestType.ALLOCATE, subnetLink,
                    subnetPrefixLength, networkCIDR);
        }

        public static ComputeNetworkCIDRAllocationRequest deallocationRequest(String subnetLink) {
            return new ComputeNetworkCIDRAllocationRequest(RequestType.DEALLOCATE, subnetLink,
                    null, null);
        }

        private ComputeNetworkCIDRAllocationRequest(RequestType requestType, String subnetLink,
                Integer subnetPrefixLength, String networkCIDR) {
            this.requestType = requestType;
            this.subnetLink = subnetLink;
            this.subnetPrefixLength = subnetPrefixLength;
            this.networkCIDR = networkCIDR;
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

        logFine("Create ComputeNetworkCIDRAllocation for network link: %s", state.networkLink);

        validateStateOnCreate(state);
        state.allocatedCIDRs = new HashMap<>();

        post.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!checkForBody(patch)) {
            return;
        }

        ComputeNetworkCIDRAllocationState state = this.getState(patch);

        ComputeNetworkCIDRAllocationRequest request = patch.getBody(
                ComputeNetworkCIDRAllocationRequest.class);

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
            logWarning("Unsupported request type.");
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
        return state;
    }

    private void handleAllocation(Operation patch, ComputeNetworkCIDRAllocationState state,
            ComputeNetworkCIDRAllocationRequest request) {

        logFine("Allocate CIDR for subnet: [%s].", request.subnetLink);

        DeferredResult.completed(new AllocationContext(request, state))
                .thenCompose(this::populateNetwork)
                .thenCompose(this::prepareAvailableCIDRRangeList)
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

        logFine("Deallocate subnet: [%s].", request.subnetLink);

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

    // Add already allocated subnets IP ranges and allocated IP ranges to the Context.availableCIDRRanges
    private DeferredResult<AllocationContext> prepareAvailableCIDRRangeList(
            AllocationContext context) {

        return DeferredResult.completed(context)
                // 1. Start with 1 free range that covers the whole network IP range.
                .thenCompose(this::seedWithOneAvailableSubnet)
                // 2. Query all network subnets and remove their IP range from the free list.
                .thenCompose(this::preAllocateWithExistingSubnets)
                // 3. Remove IP range of all already allocated CIDRs.
                .thenCompose(this::preAllocateAllocatedCIDRs);
    }

    private DeferredResult<AllocationContext> allocateCIDR(AllocationContext context) {
        AssertUtil.assertNotNull(context, "context");
        AssertUtil.assertNotNull(context.request, "context.request");
        AssertUtil.assertNotNull(context.state, "context.state");
        AssertUtil.assertNotNull(context.network, "context.network");

        int prefixLength = context.request.subnetPrefixLength;
        long requestedSize = 1 << (32 - prefixLength);

        // Select ranges large enough to accommodate requested subnet size.
        // Sort them by size (descending) so that we try filling the smallest ranges first.
        List<IpV4Range> candidateRanges = context.availableCIDRRanges.stream()
                .filter(ipV4Range -> (ipV4Range.high - ipV4Range.low) >= requestedSize - 1)
                .sorted(Comparator.comparingLong(item -> (item.high - item.low)))
                .collect(Collectors.toList());
        int inverseSubnetMask = IpHelper.safeLongToInt(requestedSize - 1);
        int subnetMask = ~(inverseSubnetMask);

        IpV4Range createdIpv4Range = null;
        IpV4Range selectedIpv4Range = null;

        for (IpV4Range ipV4Range : candidateRanges) {
            long currentLow = ipV4Range.low;

            // Increase the low value to the appropriate start of the requested subnet mask.
            // Example: /24 sized subnets always start with last 8 bits equal to zero.
            while ((currentLow & subnetMask) != currentLow) {
                currentLow++;
            }

            // Adjust the high value based on the currentLow + requested subnet size.
            long currentHigh = currentLow + inverseSubnetMask;

            // Does this range still fit after adjusting the low and high end?
            if (currentHigh <= ipV4Range.high) {
                createdIpv4Range = new IpV4Range(currentLow, currentHigh);
                selectedIpv4Range = ipV4Range;
                break;
            }
        }

        if (createdIpv4Range == null) {
            String msg = "Network [" + context.network.name + "] doesn't have an available block "
                    + "of IP addresses that is big enough to allocate /" + prefixLength + "subnet.";
            throw new IllegalStateException(msg);
        }

        updateAvailableCIDRRangesList(selectedIpv4Range, createdIpv4Range,
                context.availableCIDRRanges);

        String allocatedSubnetCIDR = IpHelper.calculateCidrFromIpV4Range(createdIpv4Range.low,
                createdIpv4Range.high);

        logFine("Newly allocated CIDR: [%s] for subnet: [%s].", allocatedSubnetCIDR,
                context.request.subnetLink);

        AssertUtil.assertTrue(!context.state.allocatedCIDRs.containsValue(allocatedSubnetCIDR),
                "Attempt to double allocate the same subnet CIDR: [" +
                        allocatedSubnetCIDR + "].");

        // Update service document state.
        context.state.allocatedCIDRs.put(context.request.subnetLink, allocatedSubnetCIDR);
        return DeferredResult.completed(context);
    }

    private DeferredResult<AllocationContext> seedWithOneAvailableSubnet(
            AllocationContext context) {

        AssertUtil.assertTrue(
                !StringUtils.isEmpty(context.network.subnetCIDR) ||
                        !StringUtils.isEmpty(context.request.networkCIDR),
                "Either parent network should have a CIDR or an explicit CIDR should be provided");

        String networkCIDR = context.request.networkCIDR;
        if (StringUtils.isEmpty(networkCIDR)) {
            networkCIDR = context.network.subnetCIDR;
        }

        SubnetUtils subnetUtils = new SubnetUtils(networkCIDR);

        subnetUtils.setInclusiveHostCount(true);
        SubnetUtils.SubnetInfo subnetInfo = subnetUtils.getInfo();
        Long lowIp = IpHelper.ipStringToLong(subnetInfo.getLowAddress());
        Long highIp = IpHelper.ipStringToLong(subnetInfo.getHighAddress());
        IpV4Range ipV4Range = new IpV4Range(lowIp, highIp);

        context.availableCIDRRanges.clear();
        context.availableCIDRRanges.add(ipV4Range);

        return DeferredResult.completed(context);
    }

    private DeferredResult<AllocationContext> preAllocateWithExistingSubnets(
            AllocationContext context) {

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addFieldClause(SubnetState.FIELD_NAME_NETWORK_LINK, context.state.networkLink)
                .addFieldClause(SubnetState.FIELD_NAME_LIFECYCLE_STATE,
                        LifecycleState.READY.name());

        QueryStrategy<SubnetState> querySubnets = new QueryByPages<>(
                this.getHost(),
                qBuilder.build(),
                SubnetState.class,
                context.network.tenantLinks,
                context.network.endpointLink
        );

        return querySubnets.queryDocuments(subnetState ->
                preAllocateCidr(subnetState.subnetCIDR, context.availableCIDRRanges))
                .thenApply(aVoid -> context);
    }

    private DeferredResult<AllocationContext> preAllocateAllocatedCIDRs(
            AllocationContext context) {

        context.state.allocatedCIDRs.values()
                .forEach(cidr -> preAllocateCidr(cidr, context.availableCIDRRanges));
        return DeferredResult.completed(context);
    }

    private void preAllocateCidr(String cidr, List<IpV4Range> availableCIDRRanges) {
        SubnetUtils subnetUtils = new SubnetUtils(cidr);
        subnetUtils.setInclusiveHostCount(true);
        SubnetUtils.SubnetInfo subnetInfo = subnetUtils.getInfo();
        Long lowIp = IpHelper.ipStringToLong(subnetInfo.getLowAddress());
        Long highIp = IpHelper.ipStringToLong(subnetInfo.getHighAddress());
        IpV4Range ipV4Range = new IpV4Range(lowIp, highIp);

        IpV4Range containingRange = availableCIDRRanges.stream()
                .filter(range -> (range.low <= ipV4Range.low && range.high >= ipV4Range.high))
                .findFirst()
                .orElse(null);

        if (containingRange == null) {
            // Already preallocated.
            return;
        }

        updateAvailableCIDRRangesList(containingRange, ipV4Range, availableCIDRRanges);
    }

    private void updateAvailableCIDRRangesList(IpV4Range selectedRange, IpV4Range createdRange,
            List<IpV4Range> availableCIDRRanges) {

        availableCIDRRanges.remove(selectedRange);

        // Create a new free range before the createdRange if selectedRange starts
        // lower than the createdRange.
        if (createdRange.low > selectedRange.low) {
            IpV4Range lowRange = new IpV4Range(selectedRange.low, createdRange.low - 1);
            availableCIDRRanges.add(lowRange);
        }

        // Create a new free range after the createdRange if selectedRange finish higher than the
        // createdRange.
        if (createdRange.high < selectedRange.high) {
            IpV4Range highRange = new IpV4Range(createdRange.high + 1, selectedRange.high);
            availableCIDRRanges.add(highRange);
        }
    }

    private void deallocateCIDR(ComputeNetworkCIDRAllocationState state,
            ComputeNetworkCIDRAllocationRequest request) {

        AssertUtil.assertNotNull(state, "state");
        AssertUtil.assertNotNull(request, "request");

        // Update service document state.
        String deallocatedCIDR = state.allocatedCIDRs.remove(request.subnetLink);
        if (deallocatedCIDR == null) {
            this.logWarning("Unable to deallocate CIDR for subnet [%s]. No previous allocation"
                    + " record for this subnet.", request.subnetLink);
        }
    }

    private void validateStateOnCreate(ComputeNetworkCIDRAllocationState state) {
        AssertUtil.assertNotEmpty(state.networkLink, "networkLink");
        AssertUtil.assertTrue(state.allocatedCIDRs == null, "allocatedCIDRs");
    }

    private boolean isValidRequest(ComputeNetworkCIDRAllocationRequest request) {
        if (request == null) {
            logWarning("Request is null.");
            return false;
        }

        if (StringUtils.isEmpty(request.subnetLink)) {
            logWarning("Subnet link is mandatory.");
            return false;
        }

        if (request.requestType == null) {
            logWarning("Request type is mandatory.");
            return false;
        }

        return true;
    }

    // Helper class to store allocation context variables.
    private static class AllocationContext {
        ComputeNetworkCIDRAllocationRequest request;
        ComputeNetworkCIDRAllocationState state;
        NetworkState network;
        List<IpV4Range> availableCIDRRanges = new LinkedList<>();

        AllocationContext(ComputeNetworkCIDRAllocationRequest request,
                ComputeNetworkCIDRAllocationState state) {
            this.request = request;
            this.state = state;
        }
    }

    private static class IpV4Range {
        IpV4Range(Long low, Long high) {
            this.low = low;
            this.high = high;
        }

        Long low;
        Long high;
    }
}
