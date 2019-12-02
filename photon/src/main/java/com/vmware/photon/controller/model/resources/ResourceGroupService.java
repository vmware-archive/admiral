/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.resources;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Describes a resource group instance. A resource group is a grouping of photon model resources
 * that have the same groupLink field
 */
public class ResourceGroupService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/groups";

    /**
     * This class represents the document state associated with a
     * {@link com.vmware.photon.controller.model.resources.ResourceGroupService}.
     */
    public static class ResourceGroupState extends ResourceState {
        @Documentation(description = "Query used to define resource group membership")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Query query;
    }

    public ResourceGroupService() {
        super(ResourceGroupState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleDelete(Operation delete) {
        logInfo("Deleting ResourceGroup, Path: %s, Operation ID: %d, Referrer: %s",
                delete.getUri().getPath(), delete.getId(),
                delete.getRefererAsString());
        super.handleDelete(delete);
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
            ResourceGroupState returnState = processInput(put);
            returnState.copyTenantLinks(getState(put));
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private ResourceGroupState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ResourceGroupState state = op.getBody(ResourceGroupState.class);
        Utils.validateState(getStateDescription(), state);
        if (state.name == null) {
            throw new IllegalArgumentException("name is required.");
        }
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        ResourceGroupState currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                ResourceGroupState.class, null);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        ResourceGroupState template = (ResourceGroupState) td;
        template.name = "resource-group-1";
        return template;
    }
}
