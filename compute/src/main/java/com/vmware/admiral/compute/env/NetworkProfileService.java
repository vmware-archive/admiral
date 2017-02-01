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

package com.vmware.admiral.compute.env;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;

import java.util.List;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Endpoint network profile.
 */
public class NetworkProfileService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.NETWORK_PROFILES;

    public static class NetworkProfile extends MultiTenantDocument {
        public static final String FIELD_NAME_NAME = "name";

        @Documentation(description = "The name that can be used to refer to this network profile")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String name;

        @Documentation(description = "Subnets included in this network profile")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public List<String> subnetLinks;
    }

    public NetworkProfileService() {
        super(NetworkProfile.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
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
