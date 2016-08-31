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
import static com.vmware.admiral.compute.container.PortBinding.fromDockerPortMapping;
import static com.vmware.admiral.compute.content.CompositeDescriptionContentService.TEMPLATE_CONTAINER_NETWORK_TYPE;
import static com.vmware.admiral.compute.content.CompositeDescriptionContentService.TEMPLATE_CONTAINER_TYPE;
import static com.vmware.admiral.compute.content.CompositeDescriptionContentService.TEMPLATE_CONTAINER_VOLUME_TYPE;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.compute.BindingUtils;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
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
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Utils;

/**
 * Utility class with methods for transforming Composite Templates from/to:
 * - Docker Compose (see {@link https://docs.docker.com/compose/compose-file/})
 */
public class CompositeTemplateUtil {

    public static final String DOCKER_COMPOSE_VERSION_2 = "2";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH.mm.ss.SSS O");

    public enum YamlType {
        DOCKER_COMPOSE, COMPOSITE_TEMPLATE, UNKNOWN
    }

    /**
     * Returns the {@link YamlType} of the provided YAML.
     *
     * @param yaml
     *            The YAML content to process.
     * @return {@link YamlType} of the provided YAML
     */
    public static YamlType getYamlType(String yaml) throws IOException {
        assertNotEmpty(yaml, "yaml");

        CommonDescriptionEntity template;
        try {
            template = YamlMapper.objectMapper().readValue(yaml, CommonDescriptionEntity.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Error processing YAML content: " + e.getOriginalMessage());
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
            throw new IllegalArgumentException(
                    "Error processing Docker Compose v2 YAML content: " + e.getOriginalMessage());
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

            entity = new ObjectMapper()
                    .convertValue(deserialized, CompositeTemplate.class);

            entity.bindings = new ArrayList<>(componentBindings);
        } catch (JsonProcessingException e) {
            String format = "Error processing Blueprint YAML content: %s";
            Utils.log(CompositeTemplateUtil.class,
                    CompositeTemplateUtil.class.getSimpleName(),
                    Level.INFO, format, e.getMessage());
            throw new IllegalArgumentException(
                    String.format(format, e.getOriginalMessage()));
        }
        sanitizeCompositeTemplate(entity);
        return entity;
    }

    public static String serializeCompositeTemplate(CompositeTemplate entity) throws IOException {
        sanitizeCompositeTemplate(entity);
        return YamlMapper.objectWriter().writeValueAsString(entity).trim();
    }

    private static void sanitizeCompositeTemplate(CompositeTemplate entity) {
        assertNotNull(entity, "entity");

        entity.id = null;
        entity.status = null;

        if (!isNullOrEmpty(entity.components)) {
            for (Entry<String, ComponentTemplate<ContainerDescription>> entry : filterComponentTemplates(
                    entity.components, ContainerDescription.class).entrySet()) {

                ComponentTemplate<ContainerDescription> component = entry.getValue();

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
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, ComponentTemplate<T>> filterComponentTemplates(
            Map<String, ComponentTemplate<?>> templates, Class<T> type) {
        Map<String, ComponentTemplate<T>> templatesFiltered = new LinkedHashMap<>();

        for (Entry<String, ComponentTemplate<?>> template : templates.entrySet()) {
            if (template.getValue().data.getClass().equals(type)) {
                templatesFiltered.put(template.getKey(),
                        (ComponentTemplate<T>) template.getValue());
            }
        }

        return templatesFiltered;
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
                ComponentTemplate<ContainerDescription> component = fromDockerServiceToCompositeComponent(
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

    public static ComponentTemplate<ContainerDescription> fromDockerServiceToCompositeComponent(
            DockerComposeService service) {
        assertNotNull(service, "service");

        ContainerDescription description = new ContainerDescription();

        // properties from Docker Compose NOT AVAILABLE in Container Description

        /*
         * -- Service specific --
         * build
         * context
         * dockerfile
         * args
         * cgroup_parent
         * tmpfs
         * env_file
         * expose
         * extends
         * external_links
         * labels (~ our custom properties?)
         * aliases
         * security_opt
         * stop_signal
         * ulimits
         * cpu_quota
         * cpuset
         * ipc
         * mac_address
         * read_only
         * shm_size
         * stdin_open
         * tty
         */

        // properties from Container Description NOT AVAILABLE in Docker Compose

        /*
         * parentDescriptionLink
         * imageReference
         * instanceAdapterReference
         * zoneId
         * pod
         * affinity
         * _cluster
         * publishAll
         * binds (vs volumes?)
         * exposeService
         * deploymentPolicyId
         * customProperties (~ Docker's labels?)
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

        return fromContainerDescriptionToComponentTemplate(description);
    }

    private static ComponentTemplate<ContainerNetworkDescription> fromDockerNetworkToCompositeComponent(
            DockerComposeNetwork network) {
        assertNotNull(network, "network");

        ContainerNetworkDescription description = new ContainerNetworkDescription();

        description.driver = network.driver;
        description.options = network.driver_opts;
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

    public static ComponentTemplate<ContainerDescription> fromContainerDescriptionToComponentTemplate(
            ContainerDescription description) {
        assertNotNull(description, "description");

        ComponentTemplate<ContainerDescription> template = new ComponentTemplate<>();
        template.type = TEMPLATE_CONTAINER_TYPE;
        template.data = description;
        template.dependsOn = description.dependsOn;
        return template;
    }

    public static ComponentTemplate<ContainerNetworkDescription> fromContainerNetworkDescriptionToComponentTemplate(
            ContainerNetworkDescription description) {
        assertNotNull(description, "description");

        ComponentTemplate<ContainerNetworkDescription> template = new ComponentTemplate<>();
        template.type = TEMPLATE_CONTAINER_NETWORK_TYPE;
        template.data = description;
        return template;
    }

    public static ComponentTemplate<ContainerVolumeDescription> fromContainerVolumeDescriptionToComponentTemplate(
            ContainerVolumeDescription description) {
        assertNotNull(description, "description");

        ComponentTemplate<ContainerVolumeDescription> template = new ComponentTemplate<>();
        template.type = TEMPLATE_CONTAINER_VOLUME_TYPE;
        template.data = description;
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
                        fromCompositeComponentToDockerService(entry.getValue()));
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
            ComponentTemplate<ContainerDescription> component) {
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
        service.depends_on = description.dependsOn;
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

        DockerComposeNetwork network = new DockerComposeNetwork();

        network.driver = description.driver;
        network.driver_opts = description.options;
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

        components.forEach((componentName, component) -> {
            if (!TEMPLATE_CONTAINER_NETWORK_TYPE.equals(component.type)
                    && !TEMPLATE_CONTAINER_TYPE.equals(component.type)
                    && !TEMPLATE_CONTAINER_NETWORK_TYPE.equals(component.type)) {
                throw new IllegalArgumentException(String.format(
                        "Component '%s' has an unsupported type '%s'",
                        componentName, component.type));
            }
        });
    }

    private static <T> boolean isNullOrEmpty(T[] array) {
        return (array == null || array.length == 0);
    }

    private static boolean isNullOrEmpty(Map<?, ?> map) {
        return (map == null || map.isEmpty());
    }

    private static boolean isNullOrEmpty(String s) {
        return (s == null || s.trim().isEmpty());
    }

}
