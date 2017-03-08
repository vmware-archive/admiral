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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

/**
 * Service responsible to allocate and store subnet CIDRs for a network.
 * The workflow is to craete a new {@link ComputeNetworkCIDRAllocationState} with post request to
 * initialize the state for each new network you want to allocate subnet CIDRs for.
 * Then allocate new subnet CIDRs by issuing a patch request and providing a
 * {@link ComputeNetworkCIDRAllocationRequest}.
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
        @UsageOption(option = PropertyUsageOption.LINK)
        public String networkLink;

        @Documentation(description = "A map of key: subnet link; value: allocated CIDR.")
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.OPTIONAL })
        public Map<String, String> allocatedCIDRs;

        @Documentation(description = "The last allocated subnet CIDR.")
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.OPTIONAL })
        public String lastAllocatedCIDR;
    }

    /**
     * Request to allocate/deallocate a new subnet CIDR.
     */
    public static class ComputeNetworkCIDRAllocationRequest {
        public enum RequestType {
            ALLOCATE,
            DEALLOCATE
        }

        /**
         * Link to the subnet that will consume the allocated CIDR.
         */
        public String subnetLink;

        /**
         * The type of the request (allocate or deallocate).
         */
        public RequestType requestType;
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

        logFine(() -> "Create ComputeNetworkDICRAllocation for network link: " + state.networkLink);

        validateStateOnCreate(state);
        state.allocatedCIDRs = new HashMap<>();

        post.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!checkForBody(patch)) {
            return;
        }

        ComputeNetworkCIDRAllocationRequest request =
                patch.getBody(ComputeNetworkCIDRAllocationRequest.class);

        if (!isValidRequest(request)) {
            patch.fail(Operation.STATUS_CODE_BAD_REQUEST);
            return;
        }

        ComputeNetworkCIDRAllocationState state = getState(patch);

        switch (request.requestType) {
        case ALLOCATE:
            allocateCIDR(request, state);
            break;
        case DEALLOCATE:
            deallocateCIDR(request, state);
            break;
        default:
            logWarning(() -> "Unsupported request type.");
        }

        patch.setBody(state).complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ComputeNetworkCIDRAllocationState state =
                (ComputeNetworkCIDRAllocationState) super.getDocumentTemplate();
        state.networkLink = "/someNetworkLink";
        state.allocatedCIDRs = new HashMap<>();
        return state;
    }

    private void allocateCIDR(ComputeNetworkCIDRAllocationRequest request,
            ComputeNetworkCIDRAllocationState state) {

        // Dummy logic for now. Return hardcoded CIDR.
        String allocatedCIDR = "192.168.5.0/24";
        state.allocatedCIDRs.put(request.subnetLink, allocatedCIDR);
        state.lastAllocatedCIDR = allocatedCIDR;
    }

    private void deallocateCIDR(ComputeNetworkCIDRAllocationRequest request,
            ComputeNetworkCIDRAllocationState state) {

        state.allocatedCIDRs.remove(request.subnetLink);
    }

    private void validateStateOnCreate(ComputeNetworkCIDRAllocationState state) {
        AssertUtil.assertNotEmpty(state.networkLink, "networkLink");
        AssertUtil.assertTrue(state.allocatedCIDRs == null, "networkLink");
    }

    private boolean isValidRequest(ComputeNetworkCIDRAllocationRequest request) {
        if (request == null) {
            logWarning(() -> "Request is null.");
            return false;
        }

        if (request.requestType == null) {
            logWarning(() -> "Request type is null.");
            return false;
        }

        if (StringUtils.isEmpty(request.subnetLink)) {
            logWarning(() -> "Subnet link is mandatory.");
            return false;
        }
        return true;
    }
}
