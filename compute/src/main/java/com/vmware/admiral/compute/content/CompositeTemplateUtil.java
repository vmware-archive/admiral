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

package com.vmware.admiral.compute.content;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.compute.container.CompositeComponentRegistry.metaByDescriptionLink;
import static com.vmware.admiral.compute.container.PortBinding.fromDockerPortMapping;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.BindingUtils;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.TemplateSerializationUtils;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.CompositeTemplateContainerDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.Ipam;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.content.compose.CommonDescriptionEntity;
import com.vmware.admiral.compute.content.compose.DockerCompose;
import com.vmware.admiral.compute.content.compose.DockerComposeNetwork;
import com.vmware.admiral.compute.content.compose.DockerComposeService;
import com.vmware.admiral.compute.content.compose.DockerComposeVolume;
import com.vmware.admiral.compute.content.compose.Logging;
import com.vmware.admiral.compute.content.compose.NetworkExternal;
import com.vmware.admiral.compute.content.compose.ServiceNetworks;
import com.vmware.admiral.compute.content.compose.VolumeExternal;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Utils;

/**
 * Utility class with methods for transforming Composite Templates from/to: - Docker Compose (see
 * {@link "https://docs.docker.com/compose/compose-file/"})
 */
public class CompositeTemplateUtil {

    public static final String DOCKER_COMPOSE_VERSION_2 = "2";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH.mm.ss.SSS O");

    public enum YamlType {
        DOCKER_COMPOSE,
        COMPOSITE_TEMPLATE,
        UNKNOWN
    }

    /**
     * Returns the {@link YamlType} of the provided YAML.
     *
     * // * @param yaml The YAML content to process.
     *
     * @return {@link YamlType} of the provided YAML
     */
    public static YamlType getYamlType(String yaml) throws IOException {
        assertNotEmpty(yaml, "yaml");

        CommonDescriptionEntity template;
        try {
            template = YamlMapper.objectMapper().readValue(yaml, CommonDescriptionEntity.class);
        } catch (JsonProcessingException e) {
            throw new LocalizableValidationException(
                    "Error processing YAML content: " + e.getOriginalMessage(),
                    "compute.template.yaml.content.error", e.getOriginalMessage());
        }

        if (DOCKER_COMPOSE_VERSION_2.equals(template.version)
                && (!isNullOrEmpty(template.services))) {
            return YamlType.DOCKER_COMPOSE;
        } else if (!isNullOrEmpty(template.components)) {
            return YamlType.COMPOSITE_TEMPLATE;
        } else {
            return YamlType.UNKNOWN;
        }
    }

    public static DockerCompose deserializeDockerCompose(String yaml) throws IOException {
        assertNotEmpty(yaml, "yaml");
        DockerCompose entity;
        try {
            entity = YamlMapper.objectMapper().readValue(yaml.trim(),
                    DockerCompose.class);
        } catch (JsonProcessingException e) {
            throw new LocalizableValidationException(
                    "Error processing Docker Compose v2 YAML content: " + e.getOriginalMessage(),
                    "compute.template.yaml.compose2.error", e.getOriginalMessage());
        }
        sanitizeDockerCompose(entity);
        return entity;
    }

    public static String serializeDockerCompose(DockerCompose entity) throws IOException {
        sanitizeDockerCompose(entity);
        return YamlMapper.objectWriter().writeValueAsString(entity).trim();
    }

    private static void sanitizeDockerCompose(DockerCompose entity) {
        assertNotNull(entity, "entity");

        if (!isNullOrEmpty(entity.services)) {
            for (DockerComposeService service : entity.services.values()) {
                // this could be a new serializer...
                Logging lc = service.logging;
                if (lc != null && lc.driver == null && isNullOrEmpty(lc.options)) {
                    service.logging = null;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static CompositeTemplate deserializeCompositeTemplate(String yaml) throws IOException {
        assertNotEmpty(yaml, "yaml");
        CompositeTemplate entity;
        try {
            Map<String, Object> deserialized = YamlMapper.objectMapper().readValue(yaml.trim(),
                    Map.class);
            List<Binding.ComponentBinding> componentBindings = BindingUtils
                    .extractBindings(deserialized);

            entity = TemplateSerializationUtils.deserializeTemplate(deserialized);

            entity.bindings = new ArrayList<>(componentBindings);
        } catch (JsonProcessingException e) {
            String format = "Error processing Blueprint YAML content: %s";
            Utils.log(CompositeTemplateUtil.class,
                    CompositeTemplateUtil.class.getSimpleName(),
                    Level.INFO, format, e.getMessage());
            throw new LocalizableValidationException(String.format(format, e.getOriginalMessage()),
                    "compute.template.yaml.error", e.getOriginalMessage());
        }
        sanitizeCompositeTemplate(entity, false);
        return entity;
    }

    public static String serializeCompositeTemplate(CompositeTemplate entity) throws IOException {
        sanitizeCompositeTemplate(entity, true);

        Map<String, Object> stringObjectMap = TemplateSerializationUtils.serializeTemplate(entity);
        return YamlMapper.objectWriter().writeValueAsString(stringObjectMap);
    }

    private static void normalizeContainerDescription(
            ComponentTemplate<ContainerDescription> component, boolean serialize) {
        if (serialize) {
            CompositeTemplateContainerDescription newData = new CompositeTemplateContainerDescription();
            PropertyUtils.mergeServiceDocuments(newData, component.data);
            if (newData.networks != null) {
                newData.networks.entrySet().forEach((e -> {
                    if (e.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> map = (Map<String, String>) e.getValue();
                        map.put("name", e.getKey());
                    } else {
                        e.getValue().name = e.getKey();
                    }
                }));

                newData.networksList = newData.networks.entrySet().stream()
                        .map(Entry::getValue)
                        .collect(Collectors.toList());
                newData.networks = null;
            }
            component.data = newData;
        } else {
            // During deserialization the actual type is CompositeTemplateContainerDescription.
            CompositeTemplateContainerDescription templateDescription = (CompositeTemplateContainerDescription) component.data;
            if (templateDescription.networksList != null) {
                templateDescription.networks = templateDescription.networksList.stream()
                        .collect(Collectors.toMap(n -> n.name, n -> n));
                templateDescription.networks.entrySet().stream().forEach(e -> {
                    e.getValue().name = null;
                });

                templateDescription.networksList = null;
            }
        }
    }

    private static void normalizeClosureDescriptions(CompositeTemplate entity, boolean serialize) {
        if (!serialize) {
            return;
        }

        for (Entry<String, ComponentTemplate<ClosureDescription>> entry : filterComponentTemplates(
                entity.components, ClosureDescription.class).entrySet()) {

            ComponentTemplate<ClosureDescription> component = entry.getValue();
            CustomClosureDescription newData = new CustomClosureDescription();
            PropertyUtils.mergeServiceDocuments(newData, component.data);

            if (newData.inputs != null) {
                newData.serializedInputs = new HashMap<>();
                newData.inputs.entrySet().forEach((e -> {
                    newData.serializedInputs.put(e.getKey(), e.getValue().toString());
                }));

                newData.inputs = null;
            }
            if (newData.logConfiguration != null) {
                newData.serializedLogConfiguration = jsonToMap(newData.logConfiguration);
                newData.logConfiguration = null;
            }

            component.data = newData;
        }
    }

    public static Map<String, Object> jsonToMap(JsonElement json) {
        Map<String, Object> retMap = new HashMap<String, Object>();
        if (json != null && json.isJsonObject()) {
            retMap = toMap((JsonObject) json);
        } else {
            Utils.log(CompositeTemplateUtil.class, CompositeTemplateUtil.class.getSimpleName(),
                    Level.WARNING, "Log configuration is not a valuid JsonObject!");
        }
        return retMap;
    }

    public static Map<String, Object> toMap(JsonObject object) {
        Map<String, Object> map = new HashMap<String, Object>();
        Iterator<Entry<String, JsonElement>> keysItr = object.entrySet().iterator();
        while (keysItr.hasNext()) {
            String key = keysItr.next().getKey();
            Object value = object.get(key);
            if (((JsonElement) value).isJsonArray()) {
                value = toList(((JsonElement) value).getAsJsonArray());
                map.put(key, value);
            } else if (value instanceof JsonObject) {
                value = toMap((JsonObject) value);
                map.put(key, value);
            } else {
                map.put(key, ((JsonElement) value).toString());
            }

        }
        return map;
    }

    public static List<Object> toList(JsonArray array) {
        List<Object> list = new ArrayList<Object>();
        for (Object obj : list) {
            if (obj instanceof JsonArray) {
                obj = toList((JsonArray) obj);
                list.add(obj);
            } else if (obj instanceof JsonObject) {
                obj = toMap((JsonObject) obj);
                list.add(obj);
            } else {
                list.add(((JsonElement) obj).toString());
            }

        }
        return list;
    }

    @JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
    public static class CustomClosureDescription extends ClosureDescription {

        @JsonProperty("inputs")
        public Map<String, String> serializedInputs;

        @JsonProperty("logConfiguration")
        public Map<String, Object> serializedLogConfiguration;
    }

    private static void sanitizeCompositeTemplate(CompositeTemplate entity, boolean serialize) {
        assertNotNull(entity, "entity");

        entity.id = null;
        entity.status = null;

        if (!isNullOrEmpty(entity.components)) {
            sanitizeContainerComponents(entity, serialize);
            sanitizeContainerNerworkComponents(entity);
            normalizeClosureDescriptions(entity, serialize);
        }
    }

    private static void sanitizeContainerNerworkComponents(CompositeTemplate entity) {
        for (Entry<String, ComponentTemplate<ContainerNetworkDescription>> entry : filterComponentTemplates(
                entity.components, ContainerNetworkDescription.class).entrySet()) {

            ComponentTemplate<ContainerNetworkDescription> component = entry.getValue();

            component.data.id = null;
            component.data.tenantLinks = null;
        }
    }

    private static void sanitizeContainerComponents(CompositeTemplate entity, boolean serialize) {
        for (Entry<String, ComponentTemplate<ContainerDescription>> entry : filterComponentTemplates(
                entity.components, ContainerDescription.class).entrySet()) {

            ComponentTemplate<ContainerDescription> component = entry.getValue();

            normalizeContainerDescription(component, serialize);

            if (!entry.getKey().equals(component.data.name)) {
                Utils.log(CompositeTemplateUtil.class,
                        CompositeTemplateUtil.class.getSimpleName(),
                        Level.WARNING,
                        "Container name '%s' differs from component name '%s' and "
                                + "it will be overriden with the component name!",
                        component.data.name, entry.getKey());
                component.data.name = entry.getKey();
            }

            component.data.tenantLinks = null;

            // this could be a new serializer...
            HealthConfig hc = component.data.healthConfig;
            if (hc != null && hc.protocol == null) {
                component.data.healthConfig = null;
            }

            // this could be a new serializer...
            LogConfig lc = component.data.logConfig;
            if (lc != null && lc.type == null && isNullOrEmpty(lc.config)) {
                component.data.logConfig = null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, ComponentTemplate<T>> filterComponentTemplates(
            Map<String, ComponentTemplate<?>> templates, Class<T> type) {

        return templates.entrySet().stream()
                .filter(e -> type.isAssignableFrom(e.getValue().data.getClass()))
                .collect(Collectors.toMap(e -> e.getKey(),
                        e -> (ComponentTemplate<T>) e.getValue()));
    }

    public static CompositeTemplate fromDockerComposeToCompositeTemplate(DockerCompose compose) {
        assertNotNull(compose, "compose");

        CompositeTemplate template = new CompositeTemplate();

        template.name = "Docker Compose " + ZonedDateTime.now(ZoneOffset.UTC).format(FORMATTER);
        // e.g. "Docker Compose 2016-05-30 11.31.44.100 GMT"

        Map<String, DockerComposeService> services = compose.services;
        if (!isNullOrEmpty(services)) {
            template.components = new LinkedHashMap<>();
            for (Entry<String, DockerComposeService> entry : services.entrySet()) {
                ComponentTemplate<ResourceState> component = fromDockerServiceToCompositeComponent(
                        entry.getValue());
                // set the service name as the component name since in the ComponentTemplate
                // context the component name must match the container name
                component.data.name = entry.getKey();
                template.components.put(entry.getKey(), component);
            }
        }

        Map<String, DockerComposeNetwork> networks = compose.networks;
        if (!isNullOrEmpty(networks)) {
            if (template.components == null) {
                template.components = new LinkedHashMap<>();
            }
            for (Entry<String, DockerComposeNetwork> entry : networks.entrySet()) {
                ComponentTemplate<ContainerNetworkDescription> component = fromDockerNetworkToCompositeComponent(
                        entry.getValue());
                // set the service name as the component name since in the ComponentTemplate
                // context the component name must match the container name
                component.data.name = entry.getKey();
                template.components.put(entry.getKey(), component);
            }
        }

        Map<String, DockerComposeVolume> volumes = compose.volumes;
        if (!isNullOrEmpty(volumes)) {
            if (template.components == null) {
                template.components = new LinkedHashMap<>();
            }
            for (Entry<String, DockerComposeVolume> entry : volumes.entrySet()) {
                ComponentTemplate<ContainerVolumeDescription> component = fromDockerVolumeToCompositeComponent(
                        entry.getValue());
                // set the service name as the component name since in the ComponentTemplate
                // context the component name must match the container name
                component.data.name = entry.getKey();
                template.components.put(entry.getKey(), component);
            }
        }

        return template;
    }

    public static ComponentTemplate<ResourceState> fromDockerServiceToCompositeComponent(
            DockerComposeService service) {
        assertNotNull(service, "service");

        ContainerDescription description = new ContainerDescription();

        // properties from Docker Compose NOT AVAILABLE in Container Description

        /*
         * -- Service specific -- build context dockerfile args cgroup_parent tmpfs env_file expose
         * extends external_links labels (~ our custom properties?) aliases security_opt stop_signal
         * ulimits cpu_quota cpuset ipc mac_address read_only shm_size stdin_open tty
         */

        // properties from Container Description NOT AVAILABLE in Docker Compose

        /*
         * parentDescriptionLink imageReference instanceAdapterReference zoneId pod affinity
         * _cluster publishAll binds (vs volumes?) exposeService deploymentPolicyId customProperties
         * (~ Docker's labels?)
         */

        // properties mapping:

        description.image = service.image;

        // ignore the container_name since in the ComponentTemplate context the component name must
        // match the container name
        // description.name = service.container_name;

        description.capAdd = service.cap_add;
        description.capDrop = service.cap_drop;
        description.command = service.command;
        description.dependsOn = service.depends_on;
        description.device = service.devices;
        description.dns = service.dns;
        description.dnsSearch = service.dns_search;
        description.entryPoint = service.entrypoint;
        description.env = service.environment;
        description.extraHosts = service.extra_hosts;
        description.links = fromDockerLinksToCompositeLinks(service.links);
        description.logConfig = fromDockerLoggingToCompositeLogConfig(service.logging);
        description.networkMode = service.network_mode;
        description.networks = fromDockerNetworksToCompositeServiceNetworks(service.networks);
        description.portBindings = fromDockerPortsToCompositePortBindings(service.ports);
        description.pidMode = service.pid;
        description.volumes = service.volumes;
        description.volumesFrom = service.volumes_from;
        description.volumeDriver = service.volume_driver;
        description.cpuShares = service.cpu_shares;
        description.domainName = service.domainname;
        description.hostname = service.hostname;
        description.memoryLimit = service.mem_limit;
        description.memorySwapLimit = service.memswap_limit;
        description.privileged = service.privileged;
        description.restartPolicy = service.restart;
        description.user = service.user;
        description.workingDir = service.working_dir;

        NestedState nestedState = new NestedState();
        nestedState.object = description;
        return fromDescriptionToComponentTemplate(nestedState,
                ResourceType.CONTAINER_TYPE.getName());
    }

    private static ComponentTemplate<ContainerNetworkDescription> fromDockerNetworkToCompositeComponent(
            DockerComposeNetwork network) {
        assertNotNull(network, "network");

        ContainerNetworkDescription description = new ContainerNetworkDescription();

        description.driver = network.driver;
        description.customProperties = network.driver_opts;
        description.ipam = network.ipam;

        if (network.external != null) {
            if (!isNullOrEmpty(network.external.name)) {
                description.externalName = network.external.name;
            } else if (network.external.value != null) {
                description.external = network.external.value;
            }
        }

        return fromContainerNetworkDescriptionToComponentTemplate(description);
    }

    private static Map<String, ServiceNetwork> fromDockerNetworksToCompositeServiceNetworks(
            ServiceNetworks networks) {
        if (networks == null) {
            return null;
        }
        if (networks.values != null) {
            Map<String, ServiceNetwork> map = new LinkedHashMap<>();
            for (String value : networks.values) {
                map.put(value, new ServiceNetwork());
            }
            return map;
        } else {
            return networks.valuesMap;
        }
    }

    private static ComponentTemplate<ContainerVolumeDescription> fromDockerVolumeToCompositeComponent(
            DockerComposeVolume volume) {
        assertNotNull(volume, "volume");

        ContainerVolumeDescription description = new ContainerVolumeDescription();

        description.driver = volume.driver;
        description.options = volume.driver_opts;

        if (volume.external != null) {
            if (!isNullOrEmpty(volume.external.name)) {
                description.externalName = volume.external.name;
            } else if (volume.external.value != null) {
                description.external = volume.external.value;
            }
        }

        return fromContainerVolumeDescriptionToComponentTemplate(description);
    }

    public static ComponentTemplate<ResourceState> fromDescriptionToComponentTemplate(
            NestedState nestedState, String resourceTypeName) {

        ResourceState description = (ResourceState) nestedState.object;
        assertNotNull(description, "description");

        ComponentTemplate<ResourceState> template = new ComponentTemplate<>();

        ResourceType resourceType = ResourceType.fromName(resourceTypeName);

        template.data = description;

        template.type = resourceType.getContentType();

        template.children = nestedState.children;

        if (description instanceof ContainerDescription) {
            template.dependsOn = ((ContainerDescription) description).dependsOn;
            ((ContainerDescription) description).dependsOn = null;
        }

        if (description instanceof ContainerNetworkDescription) {
            transformDriversToComponentTemplate((ContainerNetworkDescription) description);
        }

        return template;
    }

    public static ComponentTemplate<ContainerNetworkDescription> fromContainerNetworkDescriptionToComponentTemplate(
            ContainerNetworkDescription description) {
        assertNotNull(description, "description");

        ComponentTemplate<ContainerNetworkDescription> template = new ComponentTemplate<>();
        template.type = ResourceType.CONTAINER_NETWORK_TYPE.getContentType();
        template.data = description;
        transformDriversToComponentTemplate(description);
        template.data.id = null;
        return template;
    }

    public static ComponentTemplate<ContainerVolumeDescription> fromContainerVolumeDescriptionToComponentTemplate(
            ContainerVolumeDescription description) {
        assertNotNull(description, "description");

        ComponentTemplate<ContainerVolumeDescription> template = new ComponentTemplate<>();
        template.type = ResourceType.CONTAINER_VOLUME_TYPE.getContentType();
        template.data = description;
        template.data.id = null;
        return template;
    }

    public static ComponentTemplate<ClosureDescription> fromClosureDescriptionToComponentTemplate(
            ClosureDescription description) {
        assertNotNull(description, "description");

        ComponentTemplate<ClosureDescription> template = new ComponentTemplate<>();
        template.type = ResourceType.CLOSURE_TYPE.getContentType();
        template.data = description;
        template.data.id = null;
        return template;
    }

    public static String[] fromDockerLinksToCompositeLinks(String[] links) {
        if (isNullOrEmpty(links)) {
            return null;
        }
        for (int i = 0; i < links.length; i++) {
            if (!links[i].contains(":")) {
                links[i] = links[i] + ":" + links[i];
            }
        }
        return links;
    }

    public static LogConfig fromDockerLoggingToCompositeLogConfig(Logging logging) {
        if (logging == null) {
            return null;
        }
        LogConfig logConfig = new LogConfig();
        logConfig.type = logging.driver;
        logConfig.config = logging.options;
        return logConfig;
    }

    private static PortBinding[] fromDockerPortsToCompositePortBindings(String[] ports) {
        if (isNullOrEmpty(ports)) {
            return null;
        }
        PortBinding[] portBindings = new PortBinding[ports.length];
        for (int i = 0; i < ports.length; i++) {
            portBindings[i] = fromDockerPortMapping(DockerPortMapping.fromString(ports[i]));
        }
        return portBindings;
    }

    public static DockerCompose fromCompositeTemplateToDockerCompose(CompositeTemplate template) {
        assertNotNull(template, "template");

        DockerCompose compose = new DockerCompose();

        compose.version = DOCKER_COMPOSE_VERSION_2;

        Map<String, ComponentTemplate<?>> components = template.components;
        if (!isNullOrEmpty(components)) {
            compose.services = new LinkedHashMap<>();
            for (Entry<String, ComponentTemplate<ContainerDescription>> entry : filterComponentTemplates(
                    components, ContainerDescription.class).entrySet()) {
                compose.services.put(entry.getKey(),
                        fromCompositeComponentToDockerService(entry.getValue(), components));
            }

            for (Entry<String, ComponentTemplate<ContainerNetworkDescription>> entry : filterComponentTemplates(
                    components, ContainerNetworkDescription.class).entrySet()) {
                if (compose.networks == null) {
                    compose.networks = new LinkedHashMap<>();
                }
                compose.networks.put(entry.getKey(),
                        fromCompositeComponentToDockerNetwork(entry.getValue()));
            }

            for (Entry<String, ComponentTemplate<ContainerVolumeDescription>> entry : filterComponentTemplates(
                    components, ContainerVolumeDescription.class).entrySet()) {
                if (compose.volumes == null) {
                    compose.volumes = new LinkedHashMap<>();
                }
                compose.volumes.put(entry.getKey(),
                        fromCompositeComponentToDockerVolume(entry.getValue()));
            }
        }

        return compose;
    }

    public static DockerComposeService fromCompositeComponentToDockerService(
            ComponentTemplate<ContainerDescription> component,
            Map<String, ComponentTemplate<?>> components) {
        assertNotNull(component, "component");

        ContainerDescription description = component.data;

        DockerComposeService service = new DockerComposeService();

        service.image = description.image;

        // ignore the name since in the Docker context container names must be unique and you cannot
        // scale a service beyond 1 container if you have specified a custom name.
        // service.container_name = description.name;

        service.cap_add = description.capAdd;
        service.cap_drop = description.capDrop;
        service.command = description.command;
        // set dependsOn from the component for the valid types (containers, networks and volumes)
        if (component.dependsOn != null) {
            List<String> dependsOn = new ArrayList<>();
            for (String dependency : component.dependsOn) {
                ComponentTemplate<?> dependsOnComponent = components.get(dependency);
                if (dependsOnComponent != null && ResourceType.CONTAINER_TYPE.getContentType()
                        .equals(dependsOnComponent.type)) {
                    dependsOn.add(dependency);
                }
            }
            service.depends_on = dependsOn.toArray(new String[dependsOn.size()]);
        }
        service.devices = description.device;
        service.dns = description.dns;
        service.dns_search = description.dnsSearch;
        service.entrypoint = description.entryPoint;
        service.environment = description.env;
        service.extra_hosts = description.extraHosts;
        service.links = fromCompositeLinksToDockerLinks(description.links);
        service.logging = fromCompositeLogConfigToDockerLogging(description.logConfig);
        service.network_mode = description.networkMode;
        service.networks = fromCompositeServiceNetworksToDockerNetworks(description.networks);
        service.ports = fromCompositePortBindingsToDockerPorts(description.portBindings);
        service.pid = description.pidMode;
        service.volumes = description.volumes;
        service.volumes_from = description.volumesFrom;
        service.volume_driver = description.volumeDriver;
        service.cpu_shares = description.cpuShares;
        service.domainname = description.domainName;
        service.hostname = description.hostname;
        service.mem_limit = description.memoryLimit;
        service.memswap_limit = description.memorySwapLimit;
        service.privileged = description.privileged;
        service.restart = description.restartPolicy;
        service.user = description.user;
        service.working_dir = description.workingDir;

        return service;
    }

    private static DockerComposeNetwork fromCompositeComponentToDockerNetwork(
            ComponentTemplate<ContainerNetworkDescription> component) {
        assertNotNull(component, "component");

        ContainerNetworkDescription description = component.data;
        transformDriversFromComponentTemplate(description);

        DockerComposeNetwork network = new DockerComposeNetwork();

        network.driver = description.driver;

        network.driver_opts = PropertyUtils.mergeCustomProperties(description.options,
                description.customProperties);

        network.ipam = description.ipam;

        if (!isNullOrEmpty(description.externalName)) {
            network.external = new NetworkExternal();
            network.external.name = description.externalName;
        } else if (description.external != null) {
            network.external = new NetworkExternal();
            network.external.value = description.external;
        }

        return network;
    }

    private static ServiceNetworks fromCompositeServiceNetworksToDockerNetworks(
            Map<String, ServiceNetwork> networks) {
        if (isNullOrEmpty(networks)) {
            return null;
        }
        boolean asNames = true;
        for (ServiceNetwork network : networks.values()) {
            asNames = asNames && network.useDefaults();
            if (!asNames) {
                break;
            }
        }
        ServiceNetworks serviceNetworks = new ServiceNetworks();
        if (asNames) {
            serviceNetworks.values = networks.keySet().toArray(new String[] {});
        } else {
            serviceNetworks.valuesMap = networks;
        }
        return serviceNetworks;
    }

    private static DockerComposeVolume fromCompositeComponentToDockerVolume(
            ComponentTemplate<ContainerVolumeDescription> component) {
        assertNotNull(component, "component");

        ContainerVolumeDescription description = component.data;

        DockerComposeVolume network = new DockerComposeVolume();

        network.driver = description.driver;
        network.driver_opts = description.options;

        if (!isNullOrEmpty(description.externalName)) {
            network.external = new VolumeExternal();
            network.external.name = description.externalName;
        } else if (description.external != null) {
            network.external = new VolumeExternal();
            network.external.value = description.external;
        }

        return network;
    }

    private static String[] fromCompositeLinksToDockerLinks(String[] links) {
        if (isNullOrEmpty(links)) {
            return null;
        }
        for (int i = 0; i < links.length; i++) {
            if (links[i].contains(":")) {
                String[] parts = links[i].split(":");
                if ((parts.length == 1) || (parts[0].equals(parts[1]))) {
                    links[i] = parts[0];
                }
            }
        }
        return links;
    }

    private static Logging fromCompositeLogConfigToDockerLogging(LogConfig logConfig) {
        if (logConfig == null) {
            return null;
        }
        Logging logging = new Logging();
        logging.driver = logConfig.type;
        logging.options = logConfig.config;
        return logging;
    }

    private static String[] fromCompositePortBindingsToDockerPorts(PortBinding[] portBindings) {
        if (isNullOrEmpty(portBindings)) {
            return null;
        }
        String[] ports = new String[portBindings.length];
        for (int i = 0; i < portBindings.length; i++) {
            ports[i] = portBindings[i].toString();
        }
        return ports;
    }

    public static CompositeTemplate fromCompositeDescriptionToCompositeTemplate(
            CompositeDescription description) {
        assertNotNull(description, "description");

        CompositeTemplate template = new CompositeTemplate();
        template.id = Service.getId(description.documentSelfLink);
        template.name = description.name;
        template.status = description.status;
        template.properties = description.customProperties;
        template.bindings = description.bindings;
        return template;
    }

    public static CompositeDescription fromCompositeTemplateToCompositeDescription(
            CompositeTemplate template) {
        assertNotNull(template, "template");

        CompositeDescription description = new CompositeDescription();
        description.documentSelfLink = template.id;
        description.name = template.name;
        description.status = template.status;
        description.customProperties = template.properties;
        description.bindings = template.bindings;
        return description;
    }

    public static void assertContainersComponentsOnly(
            Map<String, ComponentTemplate<?>> components) {
        assertNotEmpty(components, "components");

        Set<String> yamlLiterals = Stream.of(ResourceType.values())
                .map(type -> type.getContentType())
                .collect(Collectors.toSet());

        components.forEach((componentName, component) -> {
            if (!yamlLiterals.contains(component.type)) {
                String errorMessage = String.format("Component '%s' has an unsupported type '%s'",
                        componentName, component.type);
                throw new LocalizableValidationException(errorMessage,
                        "compute.template.components.unsupported.type",
                        componentName, component.type);
            }
        });
    }

    public static DeferredResult<CompositeTemplate> convertCompositeDescriptionToCompositeTemplate(
            Service service, CompositeDescription compositeDescription) {

        //get each component recursively
        List<DeferredResult<NestedState>> components = compositeDescription.descriptionLinks
                .stream()
                .map(link -> {
                    ComponentMeta meta = metaByDescriptionLink(link);
                    return NestedState.get(service, link, meta.descriptionClass);
                }).collect(Collectors.toList());

        //creat the ComponentTemplate objects
        return DeferredResult.allOf(components).thenApply(nestedStates -> {
                    CompositeTemplate template = fromCompositeDescriptionToCompositeTemplate(
                            compositeDescription);
                    if (nestedStates == null || nestedStates.isEmpty()) {
                        return template;
                    }
                    template.components = new HashMap<>();
                    for (int i = 0; i < compositeDescription.descriptionLinks.size(); i++) {
                        String link = compositeDescription.descriptionLinks.get(i);
                        NestedState nestedState = nestedStates.get(i);

                        ComponentMeta meta = metaByDescriptionLink(link);

                        ComponentTemplate<?> component = fromDescriptionToComponentTemplate(
                                nestedState, meta.resourceType);
                        template.components
                                .put(((ResourceState) nestedState.object).name,
                                        component);
                    }
                    return template;
                }

        );
    }

    public static <T> boolean isNullOrEmpty(T[] array) {
        return (array == null || array.length == 0);
    }

    public static boolean isNullOrEmpty(Map<?, ?> map) {
        return (map == null || map.isEmpty());
    }

    public static boolean isNullOrEmpty(String s) {
        return (s == null || s.trim().isEmpty());
    }

    private static void transformDriversToComponentTemplate(
            ContainerNetworkDescription description) {
        Map<String, String> customProps = new HashMap<>();
        if (description.driver != null) {
            customProps.put(ContainerNetworkDescription.CUSTOM_PROPERTY_NETWORK_DRIVER,
                    description.driver);
            description.driver = null;
        }

        if (description.ipam != null && description.ipam.driver != null) {
            customProps.put(ContainerNetworkDescription.CUSTOM_PROPERTY_IPAM_DRIVER,
                    description.ipam.driver);
            description.ipam.driver = null;
        }

        if (!customProps.isEmpty()) {
            description.customProperties = PropertyUtils
                    .mergeCustomProperties(description.customProperties, customProps);
        }
    }

    private static void transformDriversFromComponentTemplate(
            ContainerNetworkDescription description) {
        if (description.customProperties != null) {
            description.driver = description.customProperties
                    .remove(ContainerNetworkDescription.CUSTOM_PROPERTY_NETWORK_DRIVER);

            String ipamDriver = description.customProperties
                    .remove(ContainerNetworkDescription.CUSTOM_PROPERTY_IPAM_DRIVER);
            if (ipamDriver != null) {
                if (description.ipam == null) {
                    description.ipam = new Ipam();
                }
                description.ipam.driver = ipamDriver;
            }
        }
    }
}
