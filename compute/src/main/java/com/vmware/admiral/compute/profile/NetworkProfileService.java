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
import java.util.List;
import java.util.Set;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

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
        @PropertyOptions(usage = {AUTO_MERGE_IF_NOT_NULL, OPTIONAL})
        public IsolationSupportType isolationType = IsolationSupportType.NONE;

        @Documentation(description = "Link to the network used for creating isolated subnets. "
                + "This field should be populated only when isolation Type is SUBNET.")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL, OPTIONAL })
        public String isolationNetworkLink;

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
                targetState.tenantLinks = this.tenantLinks;
                targetState.tagLinks = this.tagLinks;
                targetState.subnetLinks = this.subnetLinks;
                targetState.isolationType = this.isolationType;
                targetState.isolationNetworkLink = this.isolationNetworkLink;
            }
        }
    }

    public static class NetworkProfileExpanded extends NetworkProfile {
        public List<SubnetState> subnetStates;

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
        processInput(post);
        post.complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            logFine("Ignoring converted PUT.");
            put.complete();
            return;
        }

        NetworkProfile newState = processInput(put);
        setState(put, newState);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        NetworkProfile currentState = getState(patch);
        try {
            Utils.mergeWithStateAdvanced(getStateDescription(), currentState,
                    NetworkProfile.class, patch);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            patch.fail(e);
            return;
        }
        patch.setBody(currentState);
        patch.complete();
    }

    private NetworkProfile processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        NetworkProfile state = op.getBody(NetworkProfile.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
