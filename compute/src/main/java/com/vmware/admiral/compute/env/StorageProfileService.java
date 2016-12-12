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

import java.util.Map;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Endpoint storage profile.
 */
public class StorageProfileService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.STORAGE_PROFILES;

    public static class StorageProfile extends ServiceDocument {
        // TODO: temporary needed for Azure; to be generalized
        public Map<String, String> bootDiskPropertyMapping;
    }

    public StorageProfileService() {
        super(StorageProfile.class);
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

        StorageProfile newState = processInput(put);
        setState(put, newState);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        StorageProfile currentState = getState(patch);
        try {
            Utils.mergeWithStateAdvanced(getStateDescription(), currentState,
                    StorageProfile.class, patch);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            patch.fail(e);
        }
        patch.setBody(currentState);
        patch.complete();
    }

    private StorageProfile processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        StorageProfile state = op.getBody(StorageProfile.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
