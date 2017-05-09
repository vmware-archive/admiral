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
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * CRUD service for managing image profiles.
 */
public class ImageProfileService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.IMAGE_PROFILES;

    /**
     * Describes an image profile - configuration and mapping for a specific endpoint that allows
     * compute provisioning that is agnostic on the target endpoint type.
     */
    public static class ImageProfileState extends ResourceState {
        public static final String FIELD_NAME_ENDPOINT_LINK = "endpointLink";
        public static final String FIELD_NAME_ENDPOINT_TYPE = "endpointType";

        @Documentation(description = "Link to the endpoint this profile is associated with")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String endpointLink;

        @Documentation(description = "The endpoint type if this profile is not for a specific endpoint ")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String endpointType;

        /**
         * Compute images provided by the particular endpoint. Keyed by global image type
         * identifiers used to unify image types among heterogeneous set of endpoint types.
         */
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public Map<String, ComputeImageDescription> imageMapping;

        @Override
        public void copyTo(ResourceState target) {
            super.copyTo(target);
            if (target instanceof ImageProfileState) {
                ImageProfileState targetState = (ImageProfileState) target;
                targetState.endpointLink = this.endpointLink;
                targetState.endpointType = this.endpointType;
                targetState.imageMapping = this.imageMapping;
            }
        }
    }

    public ImageProfileService() {
        super(ImageProfileState.class);
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

        ImageProfileState newState = processInput(put);
        setState(put, newState);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        ImageProfileState currentState = getState(patch);
        try {
            Utils.mergeWithStateAdvanced(getStateDescription(), currentState,
                    ImageProfileState.class, patch);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            patch.fail(e);
            return;
        }
        patch.setBody(currentState);
        patch.complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

    private ImageProfileState processInput(Operation op) {
        if (!op.hasBody()) {
            throw new IllegalArgumentException("body is required");
        }
        ImageProfileState state = op.getBody(ImageProfileState.class);
        AssertUtil.assertNotNull(state.name, "name");
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
