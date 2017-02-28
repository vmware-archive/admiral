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

package com.vmware.admiral.compute.content.kubernetes;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.filterComponentTemplates;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.isNullOrEmpty;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromContainerDescriptionToDeployment;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromContainerDescriptionToService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.content.ComponentTemplate;
import com.vmware.admiral.compute.content.CompositeTemplate;
import com.vmware.admiral.compute.content.kubernetes.deployments.Deployment;
import com.vmware.admiral.compute.content.kubernetes.pods.Pod;
import com.vmware.admiral.compute.content.kubernetes.services.Service;
import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.service.KubernetesService;
import com.vmware.admiral.compute.kubernetes.service.KubernetesService.KubernetesState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.common.ResourceNamePrefixService.ResourceNamePrefixState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class KubernetesUtil {

    @SuppressWarnings("unused")
    private static final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory().enable(
            YAMLGenerator.Feature.MINIMIZE_QUOTES));

    public static final String POD_TYPE = "Pod";
    public static final String POD_TEMPLATE = "PodTemplate";
    public static final String REPLICATION_CONTROLLER_TYPE = "ReplicationController";
    public static final String DEPLOYMENT_TYPE = "Deployment";
    public static final String SERVICE_TYPE = "Service";

    public static final String KUBERNETES_API_VERSION_V1 = "v1";
    public static final String KUBERNETES_API_VERSION_V1_BETA1 = "extensions/v1beta1";

    public static final String KUBERNETES_LABEL_APP = "app";
    public static final String KUBERNETES_LABEL_TIER = "tier";

    public static CommonKubernetesEntity deserializeKubernetesEntity(String yaml)
            throws IOException {
        assertNotEmpty(yaml, "yaml");
        CommonKubernetesEntity entity;
        try {
            entity = YamlMapper.objectMapper().readValue(yaml.trim(), CommonKubernetesEntity.class);
            if (POD_TYPE.equals(entity.kind)) {
                entity = YamlMapper.objectMapper().readValue(yaml.trim(), Pod.class);
            } else if (POD_TEMPLATE.equals(entity.kind)) {
                throw new IllegalArgumentException("Not implemented.");
            } else if (REPLICATION_CONTROLLER_TYPE.equals(entity.kind)) {
                throw new IllegalArgumentException("Not implemented.");
            } else if (DEPLOYMENT_TYPE.equals(entity.kind)) {
                entity = YamlMapper.objectMapper().readValue(yaml.trim(), Deployment.class);
            } else if (SERVICE_TYPE.equals(entity.kind)) {
                entity = YamlMapper.objectMapper().readValue(yaml.trim(), Service.class);
            } else {
                throw new IllegalArgumentException("Invalid kubernetes kind.");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Error processing Kubernetes configuration YAML content: " + e
                            .getOriginalMessage());
        }
        return entity;
    }

    public static String serializeKubernetesEntity(CommonKubernetesEntity kubernetesEntity)
            throws IOException {

        return YamlMapper.objectMapper().setSerializationInclusion(Include.NON_NULL)
                .writeValueAsString(kubernetesEntity).trim();
    }

    public static String serializeKubernetesTemplate(KubernetesTemplate template)
            throws IOException {
        StringBuilder builder = new StringBuilder();

        for (Service service : template.services.values()) {
            builder.append(serializeKubernetesEntity(service));
            builder.append("\n");
        }

        for (Deployment deployment : template.deployments.values()) {
            builder.append(serializeKubernetesEntity(deployment));
            builder.append("\n");
        }

        return builder.toString().trim();
    }

    public static KubernetesTemplate fromCompositeTemplateToKubernetesTemplate(
            CompositeTemplate template) {
        if (template == null) {
            return null;
        }

        KubernetesTemplate kubernetesTemplate = new KubernetesTemplate();
        if (!isNullOrEmpty(template.components)) {
            kubernetesTemplate.deployments = new LinkedHashMap<>();
            kubernetesTemplate.services = new LinkedHashMap<>();
            Map<String, ComponentTemplate<ContainerDescription>> containerComponents = filterComponentTemplates(
                    template.components, ContainerDescription.class);

            for (Entry<String, ComponentTemplate<ContainerDescription>> container : containerComponents
                    .entrySet()) {
                Deployment deployment = fromContainerDescriptionToDeployment(
                        container.getValue().data, template.name);
                kubernetesTemplate.deployments.put(deployment.metadata.name, deployment);
                if (!isNullOrEmpty(container.getValue().data.portBindings)) {
                    Service service = fromContainerDescriptionToService(container.getValue().data,
                            template.name);
                    kubernetesTemplate.services.put(service.metadata.name, service);
                }
            }
        }
        return kubernetesTemplate;
    }

    public static KubernetesState makeStateFromJson(String json) {
        KubernetesState result = new KubernetesState();
        CommonKubernetesEntity entity = null;
        try {
            result.kubernetesEntity = YamlMapper.fromJsonToYaml(json);
            entity = result.getKubernetesEntity(CommonKubernetesEntity.class);
        } catch (IOException ignored) {
            return null;
        }
        try {
            result.type = entity.kind;
            result.namespace = entity.metadata.namespace;
            result.name = entity.metadata.name;
            result.id = entity.metadata.uid;
            return result;
        } catch (NullPointerException ignored) {
            return null;
        }
    }

    public static KubernetesDescription createKubernetesEntityDescription(KubernetesState state) {

        KubernetesDescription entityDescription = new KubernetesDescription();

        entityDescription.documentSelfLink = state.descriptionLink;
        entityDescription.documentDescription = state.documentDescription;
        entityDescription.tenantLinks = state.tenantLinks;
        entityDescription.name = state.name;
        entityDescription.id = state.id;
        entityDescription.customProperties = state.customProperties;

        // TODO: double check these
        return entityDescription;
    }

    public static String buildEntityLink(String name) {
        return UriUtils.buildUriPath(KubernetesService.FACTORY_LINK, buildEntityId(name));
    }

    public static String buildEntityId(String name) {
        return name.replaceAll(" ", "-");
    }

    public static KubernetesDescription mapApplicationAffix(KubernetesDescription desc,
            String affix) {
        BaseKubernetesObject object;
        try {
            object = desc.getKubernetesEntity(BaseKubernetesObject.class);
        } catch (IOException e) {
            Utils.logWarning("Could not get kubernetes entity, reason %s", e);
            return desc;
        }

        if (affix.startsWith(ResourceNamePrefixState.PREFIX_DELIMITER)) {
            object.metadata.name = object.metadata.name + affix;
        } else if (affix.endsWith(ResourceNamePrefixState.PREFIX_DELIMITER)) {
            object.metadata.name = affix + object.metadata.name;
        } else {
            object.metadata.name = object.metadata.name + ResourceNamePrefixState.PREFIX_DELIMITER
                    + affix;
        }

        // TODO: consider label and selector

        desc.merge(object);

        return desc;
    }

    public static <T extends BaseKubernetesState> Class<T> fromResourceStateToBaseKubernetesState(
            Class clazz) {

        if (!BaseKubernetesState.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(String.format("Class: %s is not child of "
                    + "BaseKubernetesState.", clazz.getName()));
        }

        return clazz;

    }

    public static String buildLogUriPath(BaseKubernetesState state, String containerName) {
        return UriUtils.buildUriPath(LogService.FACTORY_LINK, state.documentSelfLink + "-" +
                containerName);
    }
}
