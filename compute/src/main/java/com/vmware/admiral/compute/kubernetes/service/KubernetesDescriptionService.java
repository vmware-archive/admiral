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
import static com.vmware.admiral.common.util.YamlMapper.isValidYaml;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class KubernetesDescriptionService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.KUBERNETES_DESC;

    /* Instance to link to when existing entities are discovered on a host */
    public static final String DISCOVERED_INSTANCE = "discovered";
    public static final String DISCOVERED_DESCRIPTION_LINK = UriUtils.buildUriPath(FACTORY_LINK,
            DISCOVERED_INSTANCE);

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

        public <T extends BaseKubernetesObject> T getKubernetesEntity(Class<T> type)
                throws IOException {
            return YamlMapper.objectMapper().readValue(kubernetesEntity, type);
        }

        public String getKubernetesEntityAsJson() throws IOException {
            return YamlMapper.fromYamlToJson(kubernetesEntity);
        }

        @SuppressWarnings("unchecked")
        public void merge(BaseKubernetesObject copyFrom) {
            Map<String, Object> copyTo;
            try {
                copyTo = YamlMapper.objectMapper().readValue(kubernetesEntity,
                        Map.class);
            } catch (Exception e) {
                Utils.logWarning("Could not read value of kubernetes entity from yaml, reason :%s",
                        e);
                return;
            }
            Map<String, Object> copyFromMap = YamlMapper.objectMapper().convertValue(copyFrom,
                    Map.class);

            copyTo.putAll(copyFromMap);

            try {
                kubernetesEntity = YamlMapper.objectMapper().writeValueAsString(copyTo);
            } catch (JsonProcessingException e) {
                Utils.logWarning("Could not write of kubernetes entity to yaml, reason :%s", e);
            }
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
        if (!isValidYaml(description.kubernetesEntity)) {
            throw new LocalizableValidationException("Invalid YAML input.",
                    "compute.template.yaml.invalid");
        }

        BaseKubernetesObject kubernetesEntity = description
                .getKubernetesEntity(BaseKubernetesObject.class);

        assertNotNullOrEmpty(kubernetesEntity.apiVersion, "apiVersion");
        assertNotNullOrEmpty(kubernetesEntity.kind, "kind");
        assertNotNull(kubernetesEntity.metadata, "metadata");
        assertNotNullOrEmpty(kubernetesEntity.metadata.name, "metadata.name");

        description.type = kubernetesEntity.kind;
        description.name = kubernetesEntity.metadata.name;
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
