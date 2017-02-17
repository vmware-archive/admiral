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

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNullOrEmpty;

import java.io.IOException;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.content.kubernetes.CommonKubernetesEntity;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;

public class KubernetesService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.KUBERNETES;

    public static class KubernetesState extends ResourceState {

        /**
         * Serialized kubernetes entity in YAML format.
         */
        @Documentation(description = "Serialized kubernetes entity in YAML format.")
        public String kubernetesEntity;

        /**
         * The type of the kubernetes entity.
         */
        @Documentation(description = "The type of the kubernetes entity.")
        public String type;

        /**
         * The namespace where this entity is placed.
         */
        @Documentation(description = "The namespace where this entity is placed.")
        public String namespace;

        /**
         * The kubernetes' self link of the entity.
         */
        @Documentation(description = "The kubernetes' self link of the entity.")
        public String selfLink;

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

        public <T extends CommonKubernetesEntity> T getKubernetesEntity(Class<T> type)
                throws IOException {
            return YamlMapper.objectMapper().readValue(kubernetesEntity, type);
        }

        public String getKubernetesEntityAsJson() throws IOException {
            return YamlMapper.fromYamlToJson(kubernetesEntity);
        }
    }

    public KubernetesService() {
        super(KubernetesState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleCreate(Operation post) {
        if (!checkForBody(post)) {
            return;
        }

        KubernetesState state = post.getBody(KubernetesState.class);

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

        KubernetesState putBody = put.getBody(KubernetesState.class);

        this.setState(put, putBody);
        put.setBody(putBody);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        KubernetesState currentState = getState(patch);
        KubernetesState patchState = patch.getBody(KubernetesState.class);
        validateKubernetesStateOnPatch(patchState);

        PropertyUtils.mergeServiceDocuments(currentState, patchState);
        patch.complete();
    }

    private void validateKubernetesStateOnPatch(KubernetesState state) {
        assertNotNull(state, "kubernetesState");
        assertNotNullOrEmpty(state.selfLink, "kubernetesState.selfLink");
        assertNotNullOrEmpty(state.namespace, "kubernetesState.namespace");
        assertNotNullOrEmpty(state.type, "kubernetesState.type");
    }
}
