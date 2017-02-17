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

package com.vmware.admiral.compute.kubernetes.service;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.kubernetes.entities.Pod;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;

public class PodService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.KUBERNETES_PODS;

    public static class PodState extends ResourceState {

        /**
         * Pod is a collection of containers that can run on a host.
         * This resource is created by clients and scheduled onto hosts.
         */
        @Documentation(description = "Pod is a collection of containers that can run on a host. "
                + "This resource is created by clients and scheduled onto hosts.")
        public Pod pod;

        /**
         * Defines the description of the entity
         */
        @Documentation(description = "Defines the description of the container.")
        public String descriptionLink;

        /**
         * Link to CompositeComponent when a entity is part of App/Composition request.
         */
        @Documentation(
                description = "Link to CompositeComponent when a entity is part of App/Composition request.")
        public String compositeComponentLink;

        /**
         * Entity host link
         */
        @Documentation(description = "Entity host link")
        public String parentLink;
    }

    public PodService() {
        super(PodState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleCreate(Operation post) {
        if (!checkForBody(post)) {
            return;
        }

        PodState state = post.getBody(PodState.class);

        try {
            post.setBody(state);
            post.complete();
        } catch (Throwable e) {
            logSevere(e);
            post.fail(e);
        }
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        PodState putBody = put.getBody(PodState.class);

        this.setState(put, putBody);
        put.setBody(putBody);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        PodState currentState = getState(patch);
        PodState patchState = patch.getBody(PodState.class);

        PropertyUtils.mergeServiceDocuments(currentState, patchState);
        patch.complete();
    }
}
