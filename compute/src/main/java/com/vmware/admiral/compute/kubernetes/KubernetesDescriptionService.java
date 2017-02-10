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

package com.vmware.admiral.compute.kubernetes;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNullOrEmpty;

import java.io.IOException;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.content.kubernetes.CommonKubernetesEntity;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;

public class KubernetesDescriptionService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.KUBERNETES_DESC;

    public static class KubernetesDescription extends ResourceState {

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

        public <T extends CommonKubernetesEntity> T getKubernetesEntity(Class<T> type)
                throws IOException {
            return YamlMapper.objectMapper().readValue(kubernetesEntity, type);
        }

        public String getKubernetesEntityAsJson() throws IOException {
            return YamlMapper.fromYamlToJson(kubernetesEntity);
        }
    }

    public KubernetesDescriptionService() {
        super(KubernetesDescription.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleCreate(Operation startPost) {
        if (!checkForBody(startPost)) {
            return;
        }

        KubernetesDescription description = startPost.getBody(KubernetesDescription.class);

        try {
            validateDescription(description);
            startPost.setBody(description);
            startPost.complete();
        } catch (Throwable e) {
            logSevere(e);
            startPost.fail(e);
        }
    }

    private void validateDescription(KubernetesDescription description) throws IOException {
        CommonKubernetesEntity kubernetesEntity = description
                .getKubernetesEntity(CommonKubernetesEntity.class);

        assertNotNullOrEmpty(kubernetesEntity.apiVersion, "apiVersion");
        assertNotNullOrEmpty(kubernetesEntity.kind, "kind");

        description.type = kubernetesEntity.kind;
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        KubernetesDescription description = put.getBody(KubernetesDescription.class);

        try {
            validateDescription(description);
            this.setState(put, description);
            put.setBody(description).complete();
        } catch (Throwable e) {
            put.fail(e);
        }
    }

}
