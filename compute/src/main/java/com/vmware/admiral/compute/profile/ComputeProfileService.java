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

import java.util.Map;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Endpoint compute profile.
 */
public class ComputeProfileService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.COMPUTE_PROFILES;

    public static class ComputeProfile extends MultiTenantDocument {
        /**
         * Instance types provided by the particular endpoint. Keyed by global instance type
         * identifiers used to unify instance types among heterogeneous set of endpoint types.
         */
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public Map<String, InstanceTypeDescription> instanceTypeMapping;

        /**
         * Compute images provided by the particular endpoint. Keyed by global image type
         * identifiers used to unify image types among heterogeneous set of endpoint types.
         */
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public Map<String, ComputeImageDescription> imageMapping;
    }

    public ComputeProfileService() {
        super(ComputeProfile.class);
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

        ComputeProfile newState = processInput(put);
        setState(put, newState);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        ComputeProfile currentState = getState(patch);
        try {
            Utils.mergeWithStateAdvanced(getStateDescription(), currentState,
                    ComputeProfile.class, patch);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            patch.fail(e);
            return;
        }
        patch.setBody(currentState);
        patch.complete();
    }

    private ComputeProfile processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ComputeProfile state = op.getBody(ComputeProfile.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
