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

package com.vmware.admiral.compute;

import java.util.Set;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Describes an elastic placement zone where the computes contributing capacity to the resource
 * pool are identified by matching tags instead of explicitly attaching them to the pool.
 *
 * <p>It is based on photon-model's {@link ResourcePoolService} with added tags to match computes
 * against. A job running periodically searches for computes to include in the pool and
 * updates their {@link ComputeState#resourcePoolLink} link so that the elasticity concept is
 * transparent to resource pool clients.</p>
 */
public class ElasticPlacementZoneService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.ELASTIC_PLACEMENT_ZONES;

    /**
     * Represents a document associated with a {@link ElasticPlacementZoneService}.
     */
    public static class ElasticPlacementZoneState extends MultiTenantDocument {
        @Documentation(description = "Link to the resource pool")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
        public String resourcePoolLink;

        @Documentation(description = "Links to tags that must be set on the computes in order"
                + " to add them to this elastic placement zone")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.LINKS)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> tagLinksToMatch;
    }

    public ElasticPlacementZoneService() {
        super(ElasticPlacementZoneState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation create) {
        try {
            processInput(create);
            create.complete();
        } catch (Throwable t) {
            create.fail(t);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ElasticPlacementZoneState currentState = getState(patch);

        boolean hasStateChanged = false;
        try {
            // first check for collection update requests
            if (Utils.mergeWithState(currentState, patch)) {
                hasStateChanged = true; // TODO pmitrov: fix this to correctly reflect changes
            } else {
                // auto-merge properties
                hasStateChanged = Utils.mergeWithState(getStateDescription(),
                        currentState, getBody(patch));
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            patch.fail(e);
            return;
        }

        if (!hasStateChanged) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        } else {
            patch.setBody(currentState);
        }
        patch.complete();
    }

    private ElasticPlacementZoneState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ElasticPlacementZoneState state = op.getBody(ElasticPlacementZoneState.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
