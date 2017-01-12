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
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.filterComponentTemplates;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.fromDescriptionToComponentTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.isNullOrEmpty;
import static com.vmware.admiral.compute.content.kubernetes.ContainerDescriptionToPodContainerConverter.fromContainerDescriptionToPodContainer;
import static com.vmware.admiral.compute.content.kubernetes.ContainerDescriptionToPodContainerConverter.fromPodContainerToContainerDescription;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;

import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.content.ComponentTemplate;
import com.vmware.admiral.compute.content.CompositeTemplate;
import com.vmware.admiral.compute.content.NestedState;
import com.vmware.photon.controller.model.resources.ResourceState;

public class KubernetesUtil {

    public static final String POD = "Pod";
    public static final String POD_TEMPLATE = "PodTemplate";
    public static final String REPLICATION_CONTROLLER = "ReplicationController";
    public static final String DEPLOYMENT = "Deployment";

    public static CommonKubernetesEntity deserializeKubernetesEntity(String yaml)
            throws IOException {
        assertNotEmpty(yaml, "yaml");
        CommonKubernetesEntity entity;
        try {
            entity = YamlMapper.objectMapper().readValue(yaml.trim(), CommonKubernetesEntity.class);
            if (POD.equals(entity.kind)) {
                entity = YamlMapper.objectMapper().readValue(yaml.trim(), Pod.class);
            } else if (POD_TEMPLATE.equals(entity.kind)) {
                throw new IllegalArgumentException("Not implemented.");
            } else if (REPLICATION_CONTROLLER.equals(entity.kind)) {
                throw new IllegalArgumentException("Not implemented.");
            } else if (DEPLOYMENT.equals(entity.kind)) {
                throw new IllegalArgumentException("Not implemented.");
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
                .writeValueAsString
                        (kubernetesEntity).trim();
    }

    public static CompositeTemplate fromPodToCompositeTemplate(Pod pod) {
        assertNotNull(pod, "pod");
        CompositeTemplate template = new CompositeTemplate();
        template.name = pod.metadata.name;

        //Containers mapping
        if (!isNullOrEmpty(pod.spec.containers)) {
            template.components = new LinkedHashMap<>();
            for (PodContainer podContainer : pod.spec.containers) {
                ComponentTemplate<ResourceState> component = fromPodContainerToCompositeComponent
                        (podContainer, pod.spec);
                component.data.name = podContainer.name;
                template.components.put(podContainer.name, component);
            }
        }
        return template;
    }

    public static ComponentTemplate<ResourceState> fromPodContainerToCompositeComponent(PodContainer
            podContainer, PodSpec podSpec) {

        ContainerDescription description = fromPodContainerToContainerDescription(podContainer,
                podSpec);

        NestedState nestedState = new NestedState();
        nestedState.object = description;

        return fromDescriptionToComponentTemplate(nestedState, ResourceType.CONTAINER_TYPE
                .getName());

    }

    public static Pod fromCompositeTemplateToPod(CompositeTemplate template) {
        Pod pod = new Pod();
        pod.metadata = new ObjectMeta();
        pod.spec = new PodSpec();
        pod.kind = "Pod";
        pod.apiVersion = "v1";
        pod.metadata.name = template.name;

        if (!isNullOrEmpty(template.components)) {
            Map<String, ComponentTemplate<ContainerDescription>> compositeContainers =
                    filterComponentTemplates(template.components, ContainerDescription.class);
            pod.spec.containers = fromCompositeComponentsToPodContainers(compositeContainers);
        }

        return pod;
    }

    public static PodContainer[] fromCompositeComponentsToPodContainers(Map<String,
            ComponentTemplate<ContainerDescription>> components) {

        List<PodContainer> podContainers = new ArrayList<>();
        for (Entry<String, ComponentTemplate<ContainerDescription>> entry : components.entrySet()) {
            PodContainer podContainer = fromContainerDescriptionToPodContainer(entry.getValue()
                    .data);
            podContainers.add(podContainer);
        }

        return podContainers.toArray(new PodContainer[podContainers.size()]);
    }

}
