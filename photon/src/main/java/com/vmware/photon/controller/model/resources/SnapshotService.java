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

import java.util.UUID;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a snapshot resource.
 */
public class SnapshotService extends StatefulService {
    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/snapshots";

    /**
     * This class represents the document state associated with a
     * {@link SnapshotService} task.
     */
    public static class SnapshotState extends ResourceState {

        public static final String FIELD_NAME_PARENT_LINK = "parentLink";
        public static final String FIELD_NAME_COMPUTE_LINK = "computeLink";
        public static final String FIELD_NAME_IS_CURRENT = "isCurrent";

        /**
         * Description of this snapshot.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String description;

        /**
         * Compute link for this snapshot.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = {ServiceDocumentDescription.PropertyIndexingOption.EXPAND})
        public String computeLink;

        /**
         * Parent snapshot link for this snapshot.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_23)
        public String parentLink;

        /**
         * Identify this snapshot as the current state for Compute. At a given point amongst all
         * the snapshots for a Compute only one at max may have this flag set.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_23)
        @PropertyOptions(indexing = {ServiceDocumentDescription.PropertyIndexingOption.EXPAND})
        public Boolean isCurrent;
    }

    public SnapshotService() {
        super(SnapshotState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            processInput(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            SnapshotState returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private SnapshotState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        SnapshotState state = op.getBody(SnapshotState.class);
        Utils.validateState(getStateDescription(), state);
        if (state.name == null) {
            throw new IllegalArgumentException("name is required.");
        }
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        SnapshotState currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(), SnapshotState.class,
                null);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        SnapshotState template = (SnapshotState) td;

        template.id = UUID.randomUUID().toString();
        template.name = "snapshot01";
        template.description = "";
        return template;
    }

    public enum SnapshotRequestType {
        CREATE,
        DELETE,
        REVERT;

        public static SnapshotRequestType fromString(String name) {
            SnapshotRequestType result = null;
            for (SnapshotRequestType type: SnapshotRequestType.values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    result = type;
                    return result;
                }
            }
            return result;
        }
    }
}
