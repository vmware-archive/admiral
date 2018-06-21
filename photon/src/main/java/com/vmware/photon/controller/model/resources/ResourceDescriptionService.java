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

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;


/**
 * Describes the resource that is used by a compute type.
 */
public class ResourceDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES
            + "/resource-descriptions";

    /**
     * This class represents the document state associated with a
     * {@link ResourceDescriptionService} task.
     */
    public static class ResourceDescription extends ResourceState {

        /**
         * Type of compute to create. Used to find Computes which can create
         * this child.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String computeType;

        /**
         * The compute description that defines the resource instances.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String computeDescriptionLink;
    }

    public ResourceDescriptionService() {
        super(ResourceDescription.class);
        super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
        super.toggleOption(Service.ServiceOption.REPLICATION, true);
        super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
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
            ResourceDescription returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ResourceDescription currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                ResourceDescription.class, null);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(template);
        return template;
    }

    private ResourceDescription processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ResourceDescription state = op.getBody(ResourceDescription.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
