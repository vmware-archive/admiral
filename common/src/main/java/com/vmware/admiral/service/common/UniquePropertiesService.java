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

package com.vmware.admiral.service.common;

import java.util.ArrayList;
import java.util.List;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

public class UniquePropertiesService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.UNIQUE_PROPERTIES;
    public static final String PROJECT_NAMES_ID = "projectNames";

    public static class UniquePropertiesState extends ServiceDocument {
        public List<String> uniqueProperties;
    }

    public static class UniquePropertiesRequest {
        /**
         * Values to remove.
         */
        public List<String> toRemove;

        /**
         * Values to add.
         */
        public List<String> toAdd;

    }

    public UniquePropertiesService() {
        super(UniquePropertiesState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handlePost(Operation post) {
        if (!checkForBody(post)) {
            return;
        }

        UniquePropertiesState state = post.getBody(UniquePropertiesState.class);
        if (state.uniqueProperties == null) {
            state.uniqueProperties = new ArrayList<>();
        }
        post.setBody(state).complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!checkForBody(patch)) {
            return;
        }

        UniquePropertiesRequest body = patch.getBody(UniquePropertiesRequest.class);
        UniquePropertiesState currentState = getState(patch);

        if (body.toAdd != null && !body.toAdd.isEmpty()) {
            // First verify there are no duplicates.
            boolean hasDuplicate = false;
            for (String toAdd : body.toAdd) {
                if (currentState.uniqueProperties.contains(toAdd.toLowerCase())) {
                    hasDuplicate = true;
                    break;
                }
            }

            if (hasDuplicate) {
                patch.fail(Operation.STATUS_CODE_CONFLICT);
                return;
            }

            for (String toAdd : body.toAdd) {
                currentState.uniqueProperties.add(toAdd.toLowerCase());
            }
        }

        if (body.toRemove != null && !body.toRemove.isEmpty()) {
            for (String toRemove : body.toRemove) {
                currentState.uniqueProperties.remove(toRemove.toLowerCase());
            }
        }

        patch.setBody(currentState);
        patch.complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        UniquePropertiesState currentState = getState(put);
        UniquePropertiesState putState = put.getBody(UniquePropertiesState.class);

        if (currentState.uniqueProperties == null || currentState.uniqueProperties.isEmpty()) {
            currentState.uniqueProperties = new ArrayList<>();
            currentState.uniqueProperties.addAll(putState.uniqueProperties);
        } else {
            if (putState.uniqueProperties.containsAll(currentState.uniqueProperties)) {
                mergeStates(putState, currentState);
            }
        }

        put.setBody(currentState).complete();
    }

    private static void mergeStates(UniquePropertiesState src, UniquePropertiesState dst) {
        if (src.uniqueProperties == null || src.uniqueProperties.isEmpty()) {
            return;
        }

        if (dst.uniqueProperties == null) {
            dst.uniqueProperties = new ArrayList<>();
        }

        for (String prop : src.uniqueProperties) {
            if (dst.uniqueProperties.contains(prop.toLowerCase())) {
                continue;
            }
            dst.uniqueProperties.add(prop);
        }
    }
}
