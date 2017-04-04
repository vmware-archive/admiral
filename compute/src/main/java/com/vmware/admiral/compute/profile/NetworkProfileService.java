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

package com.vmware.admiral.compute.profile;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationState;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.ResourceUtils;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryTop;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.Utils.MergeResult;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Endpoint network profile.
 */
public class NetworkProfileService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.NETWORK_PROFILES;

    public static class NetworkProfile extends MultiTenantDocument {
        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_SUBNET_LINKS = "subnetLinks";

        @Documentation(description = "The name that can be used to refer to this network profile")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String name;

        @Documentation(description = "TagStates associated with the network profile")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public Set<String> tagLinks;

        @Documentation(description = "SubnetStates included in this network profile")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public List<String> subnetLinks;

        @Documentation(description = "Specifies the isolation support type e.g. none, subnet or "
                + "security group")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL, OPTIONAL })
        public IsolationSupportType isolationType = IsolationSupportType.NONE;

        @Documentation(description = "Link to the network used for creating isolated subnets. "
                + "This field should be populated only when isolation Type is SUBNET.")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL, OPTIONAL })
        public String isolationNetworkLink;

        @Documentation(description = "Link to CIDR Allocation Service matching the provided ")
        @PropertyOptions(usage = { OPTIONAL })
        public String isolationNetworkCIDRAllocationLink;

        /**
         * Defines the isolation network support this network profile provides.
         */
        public enum IsolationSupportType {
            NONE, SUBNET, SECURITY_GROUP
        }

        public void copyTo(MultiTenantDocument target) {
            super.copyTo(target);
            if (target instanceof NetworkProfile) {
                NetworkProfile targetState = (NetworkProfile) target;
                targetState.name = this.name;
                targetState.tagLinks = this.tagLinks;
                targetState.subnetLinks = this.subnetLinks;
                targetState.isolationType = this.isolationType;
                targetState.isolationNetworkLink = this.isolationNetworkLink;
                targetState.isolationNetworkCIDRAllocationLink =
                        this.isolationNetworkCIDRAllocationLink;
            }
        }
    }

    public static class NetworkProfileExpanded extends NetworkProfile {
        public List<SubnetState> subnetStates;

        public NetworkState isolatedNetworkState;

        public static URI buildUri(URI networkProfileUri) {
            return UriUtils.buildExpandLinksQueryUri(networkProfileUri);
        }
    }

    public NetworkProfileService() {
        super(NetworkProfile.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleGet(Operation get) {
        NetworkProfile currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        NetworkProfileExpanded expanded = new NetworkProfileExpanded();
        expanded.subnetStates = new ArrayList<>();
        currentState.copyTo(expanded);

        List<Operation> getOps = new ArrayList<>();
        if (currentState.subnetLinks != null) {
            currentState.subnetLinks.forEach(sp ->
                    getOps.add(Operation.createGet(this, sp)
                            .setReferer(this.getUri())
                            .setCompletion((o, e) -> {
                                if (e == null) {
                                    expanded.subnetStates.add(o.getBody(SubnetState.class));
                                }
                            })));
        }
        if (currentState.isolationNetworkLink != null) {
            getOps.add(Operation.createGet(this, currentState.isolationNetworkLink)
                    .setReferer(this.getUri())
                    .setCompletion((o, e) -> {
                        if (e == null) {
                            expanded.isolatedNetworkState = o.getBody(NetworkState.class);
                        }
                    }));
        }

        if (!getOps.isEmpty()) {
            OperationJoin.create(getOps)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            get.fail(exs.values().iterator().next());
                        } else {
                            get.setBody(expanded).complete();
                        }
                    }).sendWith(this);
        } else {
            get.setBody(expanded).complete();
        }
    }

    @Override
    public void handleCreate(Operation post) {
        NetworkProfile state = processInput(post);
        updateIsolationNetworkCIDRAllocation(state)
                .whenComplete((networkProfile, throwable) -> {
                    if (throwable != null) {
                        post.fail(throwable);
                        return;
                    }
                    post.complete();
                });
    }

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            logFine("Ignoring converted PUT.");
            put.complete();
            return;
        }

        NetworkProfile newState = processInput(put);
        updateIsolationNetworkCIDRAllocation(newState)
                .whenComplete((networkProfile, throwable) -> {
                    if (throwable != null) {
                        put.fail(throwable);
                        return;
                    }
                    setState(put, newState);
                    put.complete();
                });
    }

    @Override
    public void handlePatch(Operation patch) {
        NetworkProfile currentState = getState(patch);
        String currentIsolationNetworkLink = currentState.isolationNetworkLink;

        NetworkProfile newState = getBody(patch);
        String newIsolationNetworkLink = newState.isolationNetworkLink;

        DeferredResult<NetworkProfile> completion;
        try {
            ServiceDocumentDescription description = getStateDescription();
            EnumSet<MergeResult> mergeResult =
                    Utils.mergeWithStateAdvanced(description, currentState,
                            NetworkProfile.class, patch);

            if (!mergeResult.contains(Utils.MergeResult.SPECIAL_MERGE)) {
                nullifyLinkFields(description, currentState, patch.getBody(NetworkProfile.class));
            }

            // Isolation network is changed -> Update the CIDR Allocation Link.
            if (!StringUtils.equals(currentIsolationNetworkLink, newIsolationNetworkLink)) {
                completion = updateIsolationNetworkCIDRAllocation(currentState);
            } else {
                completion = DeferredResult.completed(currentState);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            patch.fail(e);
            return;
        }
        completion.whenComplete((networkProfile, throwable) -> {
            if (throwable != null) {
                patch.fail(throwable);
                return;
            }
            patch.setBody(networkProfile);
            patch.complete();

        });
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        NetworkProfile np = (NetworkProfile) super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(np);

        np.name = "My Network Profile";
        np.tagLinks = new HashSet<>();
        np.subnetLinks = new ArrayList<>();
        return np;
    }

    private NetworkProfile processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        NetworkProfile state = op.getBody(NetworkProfile.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }

    public DeferredResult<NetworkProfile> updateIsolationNetworkCIDRAllocation(
            NetworkProfile networkProfile) {

        logInfo(() -> "Update Isolation Network CIDR Allocation for network profile: " +
                networkProfile.name);

        if (networkProfile.isolationNetworkLink == null) {
            networkProfile.isolationNetworkCIDRAllocationLink = null;
            return DeferredResult.completed(networkProfile);
        }

        return DeferredResult.completed(networkProfile)
                .thenCompose(this::getExistingCIDRAllocationLink)
                .thenCompose(this::createCIDRAllocationIfNeeded);
    }

    private DeferredResult<NetworkProfile> getExistingCIDRAllocationLink(
            NetworkProfile networkProfile) {

        logInfo(() -> "Query for existing Isolation Network CIDR Allocation for network: " +
                networkProfile.isolationNetworkLink);

        // Check if ComputeNetworkCIDRAllocationService exists for the isolated network.
        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeNetworkCIDRAllocationState.class)
                .addFieldClause(
                        ComputeNetworkCIDRAllocationState.FIELD_NAME_NETWORK_LINK,
                        networkProfile.isolationNetworkLink)
                .build();

        QueryTop<ComputeNetworkCIDRAllocationState> queryCIDRAllocation =
                new QueryTop<>(this.getHost(),
                        query,
                        ComputeNetworkCIDRAllocationState.class,
                        networkProfile.tenantLinks)
                        .setMaxResultsLimit(1);

        return queryCIDRAllocation.collectLinks(Collectors.toList())
                .thenApply(cidrAllocationLinks -> {
                    if (cidrAllocationLinks != null && cidrAllocationLinks.size() == 1) {
                        // Found existing CIDRAllocationService
                        networkProfile.isolationNetworkCIDRAllocationLink =
                                cidrAllocationLinks.get(0);
                    } else {
                        // Clean up any previous CIDRAllocation link
                        networkProfile.isolationNetworkCIDRAllocationLink = null;
                    }
                    return networkProfile;
                });

    }

    private DeferredResult<NetworkProfile> createCIDRAllocationIfNeeded(
            NetworkProfile networkProfile) {

        if (networkProfile.isolationNetworkCIDRAllocationLink != null) {
            // CIDR Allocation is already found. Do nothing.
            return DeferredResult.completed(networkProfile);
        }

        logInfo(() -> "Create new Network CIDR Allocation for network: " +
                networkProfile.isolationNetworkLink);

        ComputeNetworkCIDRAllocationState cidrAllocationState = new
                ComputeNetworkCIDRAllocationState();
        cidrAllocationState.networkLink = networkProfile.isolationNetworkLink;
        Operation createOp = Operation.createPost(getHost(),
                ComputeNetworkCIDRAllocationService.FACTORY_LINK)
                .setBody(cidrAllocationState);

        return sendWithDeferredResult(createOp, ComputeNetworkCIDRAllocationState.class)
                .thenApply(resultCIDRAllocationState -> {
                    networkProfile.isolationNetworkCIDRAllocationLink =
                            resultCIDRAllocationState.documentSelfLink;
                    return networkProfile;
                });
    }

    /**
     * Nullifies link fields if the patch body contains NULL_LINK_VALUE links.
     * TODO: This is the same as ResourceUtils.nullifyLinkFields(). Unify in the next changelist.
     */
    private static <T extends ResourceState> boolean nullifyLinkFields(
            ServiceDocumentDescription desc, NetworkProfile currentState, NetworkProfile patchBody) {
        boolean modified = false;
        for (PropertyDescription prop : desc.propertyDescriptions.values()) {
            if (prop.usageOptions != null &&
                    prop.usageOptions.contains(PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) &&
                    prop.usageOptions.contains(PropertyUsageOption.LINK)) {
                Object patchValue = ReflectionUtils.getPropertyValue(prop, patchBody);
                if (ResourceUtils.NULL_LINK_VALUE.equals(patchValue)) {
                    Object currentValue = ReflectionUtils.getPropertyValue(prop, currentState);
                    modified |= currentValue != null;
                    ReflectionUtils.setPropertyValue(prop, currentState, null);
                }
            }
        }
        return modified;
    }
}
