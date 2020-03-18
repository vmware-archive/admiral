/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.resources;

import static com.vmware.xenon.common.UriUtils.buildUriPath;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TenantService;

/**
 * Represents a networking router.
 */
public class RouterService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_ROUTERS;

    /**
     * Represents the state of a router.
     */
    public static class RouterState extends ResourceState {

        public static final String FIELD_NAME_TYPE = "type";

        /**
         * Link to the endpoint the router belongs to.
         */
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.REQUIRED)
        public String endpointLink;

        /**
         * Router type defined by adapter.
         */
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String type;
    }

    public RouterService() {
        super(RouterState.class);

        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation start) {
        processInput(start);
        start.complete();
    }

    @Override
    public void handlePut(Operation put) {
        RouterState returnState = processInput(put);
        returnState.copyTenantLinks(getState(put));
        setState(put, returnState);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patchOp) {
        ResourceUtils.handlePatch(
                patchOp, getState(patchOp), getStateDescription(), RouterState.class, null);
    }

    private RouterState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        RouterState state = op.getBody(RouterState.class);
        validateState(state);
        return state;
    }

    /**
     * Common validation login.
     */
    private void validateState(RouterState routerState) {
        Utils.validateState(getStateDescription(), routerState);
    }

    @Override
    public RouterState getDocumentTemplate() {
        RouterState routerState = (RouterState) super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(routerState);

        routerState.id = "endpoint-specific-router-id";
        routerState.name = "endpoint-specific-router-name";
        routerState.desc = "user-friendly-router-description";
        routerState.regionId = "endpoint-specific-router-region-id";
        routerState.type = "tier-0-logical-router";

        routerState.endpointLink = buildUriPath(EndpointService.FACTORY_LINK, "the-A-cloud");
        routerState.groupLinks = singleton(
                buildUriPath(ResourceGroupService.FACTORY_LINK, "the-A-folder"));
        routerState.tenantLinks = singletonList(
                buildUriPath(TenantService.FACTORY_LINK, "the-A-tenant"));

        return routerState;
    }
}
