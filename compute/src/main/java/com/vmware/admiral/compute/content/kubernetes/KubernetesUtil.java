/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.content.kubernetes;

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_ENDPOINT_PROP_NAME;
import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.filterComponentTemplates;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.isNullOrEmpty;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromContainerDescriptionToDeployment;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromContainerDescriptionToService;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.content.ComponentTemplate;
import com.vmware.admiral.compute.content.CompositeTemplate;
import com.vmware.admiral.compute.kubernetes.KubernetesEntityDataCollection.KubernetesEntityData;
import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.admiral.compute.kubernetes.entities.common.ObjectMeta;
import com.vmware.admiral.compute.kubernetes.entities.config.KubeConfig;
import com.vmware.admiral.compute.kubernetes.entities.deployments.Deployment;
import com.vmware.admiral.compute.kubernetes.entities.pods.Pod;
import com.vmware.admiral.compute.kubernetes.entities.pods.PodTemplate;
import com.vmware.admiral.compute.kubernetes.entities.pods.PodTemplateSpec;
import com.vmware.admiral.compute.kubernetes.entities.replicaset.ReplicaSet;
import com.vmware.admiral.compute.kubernetes.entities.replicationcontrollers.ReplicationController;
import com.vmware.admiral.compute.kubernetes.entities.services.Service;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService.DeploymentState;
import com.vmware.admiral.compute.kubernetes.service.GenericKubernetesEntityService.GenericKubernetesEntityState;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.compute.kubernetes.service.ReplicaSetService.ReplicaSetState;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService.ReplicationControllerState;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler.ServiceState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.common.ResourceNamePrefixService.ResourceNamePrefixState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState;
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
    public static final String REPLICA_SET_TYPE = "ReplicaSet";
    public static final String NODE_TYPE = "Node";
    public static final String NAMESPACE_TYPE = "Namespace";
    public static final String ENDPOINTS_TYPE = "Endpoints";
    public static final String CONFIG_TYPE = "Config";

    public static final String KUBERNETES_API_VERSION_V1 = "v1";
    public static final String KUBERNETES_API_VERSION_V1_BETA1 = "extensions/v1beta1";

    public static final String KUBERNETES_LABEL_APP = "app";
    public static final String KUBERNETES_LABEL_TIER = "tier";
    public static final String KUBERNETES_LABEL_APP_ID = "admiral_app_id";

    private static final Map<String, ResourceType> kindToInternalType = new HashMap<>();

    private static final String KUBERNETES_DASHBOARD_ACCESS_LINK =
            "/api/v1/namespaces/kube-system/services/https:kubernetes-dashboard:/proxy/";

    private static final Pattern PATTERN_KUBERNETES_BYTES = Pattern
            .compile("^(?<bytes>\\d+.?(\\d+)?)(?<unit>Ei|Pi|Ti|Gi|Mi|Ki)?$");

    private static final Map<String, Double> units = new HashMap<>();

    static {
        units.put("Ki", Math.pow(2, 10));
        units.put("Mi", Math.pow(2, 20));
        units.put("Gi", Math.pow(2, 30));
        units.put("Ti", Math.pow(2, 40));
        units.put("Pi", Math.pow(2, 50));
        units.put("Ei", Math.pow(2, 60));
    }

    static {
        kindToInternalType.put(POD_TYPE, ResourceType.KUBERNETES_POD_TYPE);
        kindToInternalType.put(SERVICE_TYPE, ResourceType.KUBERNETES_SERVICE_TYPE);
        kindToInternalType.put(DEPLOYMENT_TYPE, ResourceType.KUBERNETES_DEPLOYMENT_TYPE);
        kindToInternalType.put(REPLICA_SET_TYPE, ResourceType.KUBERNETES_REPLICA_SET_TYPE);
        kindToInternalType.put(REPLICATION_CONTROLLER_TYPE,
                ResourceType.KUBERNETES_REPLICATION_CONTROLLER_TYPE);
    }

    public static ResourceType getResourceType(String entityKind) {
        ResourceType type = kindToInternalType.get(entityKind);
        return type != null ? type : ResourceType.KUBERNETES_GENERIC_TYPE;
    }

    public static BaseKubernetesObject deserializeKubernetesEntity(String yaml)
            throws IOException {
        assertNotEmpty(yaml, "yaml");
        BaseKubernetesObject entity;
        try {
            entity = YamlMapper.objectMapper().readValue(yaml.trim(), BaseKubernetesObject.class);
            if (POD_TYPE.equals(entity.kind)) {
                entity = YamlMapper.objectMapper().readValue(yaml.trim(), Pod.class);
            } else if (POD_TEMPLATE.equals(entity.kind)) {
                entity = YamlMapper.objectMapper().readValue(yaml.trim(), PodTemplate.class);
            } else if (REPLICATION_CONTROLLER_TYPE.equals(entity.kind)) {
                entity = YamlMapper.objectMapper().readValue(yaml.trim(),
                        ReplicationController.class);
            } else if (DEPLOYMENT_TYPE.equals(entity.kind)) {
                entity = YamlMapper.objectMapper().readValue(yaml.trim(), Deployment.class);
            } else if (SERVICE_TYPE.equals(entity.kind)) {
                entity = YamlMapper.objectMapper().readValue(yaml.trim(), Service.class);
            } else {
                entity = YamlMapper.objectMapper().readValue(yaml.trim(),
                        BaseKubernetesObject.class);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Error processing Kubernetes configuration YAML content: " + e
                            .getOriginalMessage());
        }
        return entity;
    }

    public static String serializeKubernetesEntity(BaseKubernetesObject kubernetesEntity)
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

    public static KubernetesEntityData createEntityData(BaseKubernetesObject object, String kind) {
        KubernetesEntityData data = new KubernetesEntityData();
        data.name = object.metadata.name;
        data.kind = kind;
        data.selfLink = object.metadata.selfLink;
        data.namespace = object.metadata.namespace;
        if (object.metadata.labels != null
                && object.metadata.labels.containsKey(KUBERNETES_LABEL_APP_ID)) {
            data.compositeComponentId = String
                    .valueOf(object.metadata.labels.get(KUBERNETES_LABEL_APP_ID));
        }
        return data;
    }

    public static BaseKubernetesState createKubernetesEntityState(String kind) {
        switch (kind) {
        case KubernetesUtil.POD_TYPE:
            return new PodState();
        case KubernetesUtil.SERVICE_TYPE:
            return new ServiceState();
        case KubernetesUtil.DEPLOYMENT_TYPE:
            return new DeploymentState();
        case KubernetesUtil.REPLICATION_CONTROLLER_TYPE:
            return new ReplicationControllerState();
        case KubernetesUtil.REPLICA_SET_TYPE:
            return new ReplicaSetState();
        default:
            return new GenericKubernetesEntityState();
        }
    }

    public static KubernetesDescription createKubernetesEntityDescription(
            BaseKubernetesState state) {

        KubernetesDescription entityDescription = new KubernetesDescription();

        entityDescription.documentSelfLink = state.descriptionLink;
        entityDescription.documentDescription = state.documentDescription;
        entityDescription.tenantLinks = state.tenantLinks;
        entityDescription.name = state.name;
        entityDescription.id = state.id;
        entityDescription.type = state.getType();
        entityDescription.customProperties = state.customProperties;

        return entityDescription;
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
            Utils.logWarning("Could not get kubernetes entity, reason %s", Utils.toString(e));
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

    public static KubernetesDescription setApplicationLabel(KubernetesDescription desc,
            String compositeComponentId) {
        BaseKubernetesObject object;
        try {
            object = desc.getKubernetesEntity(BaseKubernetesObject.class);
        } catch (IOException e) {
            Utils.logWarning("Could not get kubernetes entity, reason %s", Utils.toString(e));
            return desc;
        }

        if (object.metadata == null) {
            object.metadata = new ObjectMeta();
        }

        if (object.metadata.labels == null) {
            object.metadata.labels = new HashMap<>();
        }

        object.metadata.labels.put(KUBERNETES_LABEL_APP_ID, compositeComponentId);
        desc.merge(object);

        desc = setApplicationLabelOnTemplate(desc, compositeComponentId);

        return desc;
    }

    public static KubernetesDescription setApplicationLabelOnTemplate(KubernetesDescription desc,
            String compositeComponentId) {
        try {
            switch (desc.type) {
            case DEPLOYMENT_TYPE:
                Deployment deployment = desc.getKubernetesEntity(Deployment.class);
                if (deployment.spec == null || deployment.spec.template == null) {
                    return desc;
                }
                deployment.spec.template = createMetaOrLabelsIfNull(deployment.spec.template);
                deployment.spec.template.metadata.labels.put(KUBERNETES_LABEL_APP_ID,
                        compositeComponentId);
                desc.merge(deployment);
                return desc;

            case REPLICATION_CONTROLLER_TYPE:
                ReplicationController controller = desc
                        .getKubernetesEntity(ReplicationController.class);
                if (controller.spec == null || controller.spec.template == null) {
                    return desc;
                }
                controller.spec.template = createMetaOrLabelsIfNull(controller.spec.template);
                controller.spec.template.metadata.labels.put(KUBERNETES_LABEL_APP_ID,
                        compositeComponentId);
                desc.merge(controller);
                return desc;

            case REPLICA_SET_TYPE:
                ReplicaSet replicaSet = desc.getKubernetesEntity(ReplicaSet.class);
                if (replicaSet.spec == null || replicaSet.spec.template == null) {
                    return desc;
                }
                replicaSet.spec.template = createMetaOrLabelsIfNull(replicaSet.spec.template);
                replicaSet.spec.template.metadata.labels.put(KUBERNETES_LABEL_APP_ID,
                        compositeComponentId);
                desc.merge(replicaSet);
                return desc;

            default:
                return desc;
            }
        } catch (IOException ex) {
            Utils.logWarning("Could not get kubernetes entity, reason %s", Utils.toString(ex));
            return desc;
        }

    }

    @SuppressWarnings("unchecked")
    public static <T extends BaseKubernetesState> Class<T> fromResourceStateToBaseKubernetesState(
            Class<? extends ResourceState> clazz) {

        if (!BaseKubernetesState.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(String.format("Class: %s is not child of "
                    + "BaseKubernetesState.", clazz.getName()));
        }

        return (Class<T>) clazz;

    }

    public static <T extends BaseKubernetesState> Class<T> getStateTypeFromSelfLink(
            String selfLink) {

        Class<? extends ResourceState> resourceStateClass = CompositeComponentRegistry
                .metaByStateLink(selfLink).stateClass;

        return fromResourceStateToBaseKubernetesState(resourceStateClass);

    }

    public static String buildLogUriPath(BaseKubernetesState state, String containerName) {
        return UriUtils.buildUriPath(LogService.FACTORY_LINK, state.documentSelfLink + "-" +
                containerName);
    }

    public static PodTemplateSpec createMetaOrLabelsIfNull(PodTemplateSpec spec) {
        if (spec.metadata == null) {
            spec.metadata = new ObjectMeta();
        }
        if (spec.metadata.labels == null) {
            spec.metadata.labels = new HashMap<>();
        }
        return spec;
    }

    public static String constructDashboardLink(ComputeState host, Service dashboard) {
        String scheme = Optional.ofNullable(UriUtilsExtended.extractScheme(host.address))
                .orElse(UriUtils.HTTPS_SCHEME);
        String hostname = UriUtilsExtended.extractHost(host.address);
        String port = Optional.ofNullable(UriUtilsExtended.extractPort(host.address)).orElse("-1");
        return UriUtils.buildUri(scheme, hostname, Integer.parseInt(port),
                KUBERNETES_DASHBOARD_ACCESS_LINK, null)
                .toString();
    }

    public static Double parseBytes(String bytes) {
        Matcher matcher = PATTERN_KUBERNETES_BYTES.matcher(bytes);
        if (!matcher.matches()) {
            throw new NumberFormatException("Invalid number: " + bytes);
        }
        Double number = Double.valueOf(matcher.group("bytes"));
        String unit = matcher.group("unit");
        if (unit != null) {
            number *= units.getOrDefault(unit, 1D);
        }
        return number;
    }

    public static boolean isPKSManagedHost(ComputeState host) {
        if (host == null || host.customProperties == null) {
            return false;
        }
        return host.customProperties.containsKey(PKS_ENDPOINT_PROP_NAME);
    }

    public static KubeConfig constructKubeConfig(String clusterAddress, String certificate,
            String privateKey) {

        KubeConfig config = new KubeConfig();
        config.apiVersion = KUBERNETES_API_VERSION_V1;
        config.kind = CONFIG_TYPE;
        config.currentContext = UUID.randomUUID().toString();

        KubeConfig.ContextEntry contextEntry = new KubeConfig.ContextEntry();
        contextEntry.name = config.currentContext;
        contextEntry.context = new KubeConfig.Context();
        contextEntry.context.cluster = config.currentContext;
        contextEntry.context.user = UUID.randomUUID().toString();
        config.contexts = Arrays.asList(contextEntry);

        KubeConfig.ClusterEntry clusterEntry = new KubeConfig.ClusterEntry();
        clusterEntry.name = config.currentContext;
        clusterEntry.cluster = new KubeConfig.Cluster();
        clusterEntry.cluster.server = clusterAddress;
        clusterEntry.cluster.insecureSkipTlsVerify = true;
        config.clusters = Arrays.asList(clusterEntry);

        KubeConfig.UserEntry userEntry = new KubeConfig.UserEntry();
        userEntry.name = contextEntry.context.user;
        userEntry.user = new KubeConfig.AuthInfo();
        // TODO base64 encode
        userEntry.user.clientCertificateData = new String(
                Base64.getEncoder().encode(certificate.getBytes()));
        userEntry.user.clientKeyData = new String(
                Base64.getEncoder().encode(privateKey.getBytes()));
        config.users = Arrays.asList(userEntry);

        return config;
    }
}
