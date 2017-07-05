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
import com.vmware.admiral.compute.profile.ImageProfileService.ImageProfileState;
import com.vmware.admiral.compute.profile.InstanceTypeService.InstanceTypeState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Endpoint compute profile.
 */
public class ComputeProfileService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.COMPUTE_PROFILES;

    public static class ComputeProfile extends ResourceState {

        @Documentation(description = "Link to the endpoint this profile is associated with")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String endpointLink;

        @Documentation(description = "The endpoint type if this profile is not for a specific endpoint ")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String endpointType;

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

        @Documentation(description = "Link to the image profile for this profile")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String imageProfileLink;

        @Documentation(description = "Link to the instance type profile for this profile")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String instanceTypeProfileLink;
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
        ComputeProfile state = post.getBody(ComputeProfile.class);
        ImageProfileState imageProfile = createImageProfile(state);
        InstanceTypeState instanceTypeState = createInstanceTypeProfile(state);
        sendWithDeferredResult(
                Operation.createPost(this, ImageProfileService.FACTORY_LINK).setBody(imageProfile),
                ImageProfileState.class)
                        .thenApply(ip -> state.imageProfileLink = ip.documentSelfLink)
                        .thenCompose(
                                x -> sendWithDeferredResult(
                                        Operation.createPost(this, InstanceTypeService.FACTORY_LINK)
                                                .setBody(instanceTypeState),
                                        InstanceTypeState.class))
                        .thenApply(it -> state.instanceTypeProfileLink = it.documentSelfLink)
                        .whenComplete((ignore, t) -> {
                            if (t != null) {
                                post.fail(t);
                            } else {
                                post.complete();
                            }
                        });
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
        ImageProfileState imageProfile = createImageProfile(newState);
        InstanceTypeState instanceTypeState = createInstanceTypeProfile(newState);
        sendWithDeferredResult(
                Operation.createPatch(this, newState.imageProfileLink).setBody(imageProfile),
                ImageProfileState.class)
                        .thenCompose(
                                x -> sendWithDeferredResult(
                                        Operation
                                                .createPatch(this, newState.instanceTypeProfileLink)
                                                .setBody(instanceTypeState),
                                        InstanceTypeState.class))
                        .whenComplete((ignore, t) -> {
                            if (t != null) {
                                put.fail(t);
                            } else {
                                put.complete();
                            }
                        });
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
        ImageProfileState imageProfile = createImageProfile(currentState);
        InstanceTypeState instanceTypeState = createInstanceTypeProfile(currentState);
        sendWithDeferredResult(
                Operation.createPatch(this, currentState.imageProfileLink).setBody(imageProfile),
                ImageProfileState.class)
                        .thenCompose(
                                x -> sendWithDeferredResult(
                                        Operation.createPatch(this,
                                                currentState.instanceTypeProfileLink)
                                                .setBody(instanceTypeState),
                                        InstanceTypeState.class))
                        .whenComplete((ignore, t) -> {
                            if (t != null) {
                                patch.fail(t);
                            } else {
                                patch.complete();
                            }
                        });

    }

    private ComputeProfile processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ComputeProfile state = op.getBody(ComputeProfile.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

    private InstanceTypeState createInstanceTypeProfile(ComputeProfile state) {
        InstanceTypeState its = new InstanceTypeState();
        its.name = "instance";
        its.endpointType = state.endpointType;
        its.endpointLink = state.endpointLink;
        its.instanceTypeMapping = state.instanceTypeMapping;
        its.tenantLinks = state.tenantLinks;
        return its;
    }

    private ImageProfileState createImageProfile(ComputeProfile state) {
        ImageProfileState ips = new ImageProfileState();
        ips.name = "image";
        ips.endpointLink = state.endpointLink;
        ips.endpointType = state.endpointType;
        ips.imageMapping = state.imageMapping;
        ips.tenantLinks = state.tenantLinks;
        return ips;
    }
}
