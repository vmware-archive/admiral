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
import static com.vmware.admiral.compute.container.PortBinding.fromPodContainerPort;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.filterComponentTemplates;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.fromDescriptionToComponentTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.isNullOrEmpty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;

import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.HttpVersion;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.RequestProtocol;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.content.ComponentTemplate;
import com.vmware.admiral.compute.content.CompositeTemplate;
import com.vmware.admiral.compute.content.NestedState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Service.Action;

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
        assertNotNull(podContainer, "pod container");

        ContainerDescription description = new ContainerDescription();

        description.name = podContainer.name;
        description.image = podContainer.image;
        description.command = fromPodContainerCommandToContainerDescriptionCommand(podContainer
                .command, podContainer.args);
        description.workingDir = podContainer.workingDir;
        description.portBindings = fromPodContainerPortsToContainerDescriptionPortBindings(
                podContainer.ports);
        description.env = fromPodContainerEnvVarToContainerDescriptionEnv(podContainer.env);
        description.privileged = fromPodPrivilegedModeToContainerDescriptionPrivilegedMode
                (podContainer);
        description.restartPolicy = podSpec.restartPolicy;
        description.healthConfig = fromPodContainerProbeToContainerDescriptionHealthConfig
                (podContainer);

        setPodContainerResourcesToContainerDescriptionResources(podContainer, description);

        NestedState nestedState = new NestedState();
        nestedState.object = description;

        return fromDescriptionToComponentTemplate(nestedState, ResourceType.CONTAINER_TYPE
                .getName());

    }

    public static String[] fromPodContainerCommandToContainerDescriptionCommand(String[]
            command, String[] args) {
        if (isNullOrEmpty(command)) {
            return null;
        }
        String[] compositeComponentCommand;
        if (isNullOrEmpty(args)) {
            compositeComponentCommand = new String[command.length];
        } else {
            compositeComponentCommand = new String[command.length + args.length];
        }

        for (int i = 0; i < command.length; i++) {
            compositeComponentCommand[i] = command[i];
        }
        if (!isNullOrEmpty(args)) {
            for (int i = 0; i < args.length; i++) {
                compositeComponentCommand[command.length + i] = args[i];
            }
        }
        return compositeComponentCommand;
    }

    public static PortBinding[] fromPodContainerPortsToContainerDescriptionPortBindings(
            PodContainerPort[] ports) {
        if (isNullOrEmpty(ports)) {
            return null;
        }
        PortBinding[] portBindings = new PortBinding[ports.length];
        for (int i = 0; i < ports.length; i++) {
            portBindings[i] = fromPodContainerPort(ports[i]);
        }
        return portBindings;
    }

    public static String[] fromPodContainerEnvVarToContainerDescriptionEnv(
            PodContainerEnvVar[] env) {
        if (isNullOrEmpty(env)) {
            return null;
        }
        String[] compositeComponentEnvVars = new String[env.length];
        for (int i = 0; i < env.length; i++) {
            compositeComponentEnvVars[i] = String.format("%s=%s", env[i].name, env[i].value);
        }
        return compositeComponentEnvVars;
    }

    public static Boolean fromPodPrivilegedModeToContainerDescriptionPrivilegedMode(PodContainer
            podContainer) {
        if (podContainer.securityContext == null) {
            return (Boolean) null;
        }
        return podContainer.securityContext.privileged;
    }

    public static HealthConfig fromPodContainerProbeToContainerDescriptionHealthConfig(PodContainer
            podContainer) {
        if (podContainer.livenessProbe == null) {
            return null;
        }

        PodContainerProbe probe = podContainer.livenessProbe;
        HealthConfig healthConfig = new HealthConfig();

        if (probe.exec != null) {
            healthConfig.protocol = RequestProtocol.COMMAND;
            healthConfig.command = String.join(" ", probe.exec.command);
        } else if (probe.httpGet != null) {
            healthConfig.protocol = RequestProtocol.HTTP;
            healthConfig.httpVersion = HttpVersion.HTTP_v1_1;
            healthConfig.httpMethod = Action.GET;
            healthConfig.urlPath = probe.httpGet.path;
            healthConfig.port = fromPodContainerProbePortToHealthCheckPort(podContainer);
            healthConfig.timeoutMillis = (int) TimeUnit.SECONDS.toMillis(probe.timeoutSeconds);
            healthConfig.healthyThreshold = probe.successThreshold;
            healthConfig.unhealthyThreshold = probe.failureThreshold;
        } else if (probe.tcpSocket != null) {
            healthConfig.protocol = RequestProtocol.TCP;
            healthConfig.port = fromPodContainerProbePortToHealthCheckPort(podContainer);
            healthConfig.timeoutMillis = (int) TimeUnit.SECONDS.toMillis(probe.timeoutSeconds);
            healthConfig.healthyThreshold = probe.successThreshold;
            healthConfig.unhealthyThreshold = probe.failureThreshold;
        } else {
            healthConfig = null;
        }
        return healthConfig;
    }

    public static Integer fromPodContainerProbePortToHealthCheckPort(PodContainer podContainer) {
        String probePort;
        if (podContainer.livenessProbe.httpGet != null) {
            probePort = podContainer.livenessProbe.httpGet.port;
        } else if (podContainer.livenessProbe.tcpSocket != null) {
            probePort = podContainer.livenessProbe.tcpSocket.port;
        } else {
            return null;
        }
        if (probePort == null || probePort.trim().equals("")) {
            return null;
        }
        Integer result = null;
        try {
            result = Integer.parseInt(probePort);
        } catch (NumberFormatException nfe) {
            for (PodContainerPort port : podContainer.ports) {
                if (probePort.equals(port.name)) {
                    result = Integer.parseInt(port.containerPort);
                }
            }
        }
        return result;
    }

    public static void setPodContainerResourcesToContainerDescriptionResources(PodContainer
            podContainer, ContainerDescription containerDescription) {
        if ((isNullOrEmpty(podContainer.resources)) || (!podContainer.resources
                .containsKey("limits"))) {
            return;
        }
        String podContainerMemoryLimit = podContainer.resources.get("limits").memory;
        String podContainerCpuShares = podContainer.resources.get("limits").cpu;
        try {
            containerDescription.memoryLimit = Long.parseLong(podContainerMemoryLimit);
            containerDescription.cpuShares = Integer.parseInt(podContainerCpuShares);
        } catch (NumberFormatException nfe) {
            containerDescription.memoryLimit = parsePodContainerMemoryLimit
                    (podContainerMemoryLimit);
            // Possible floating point number, investigation is required how to proceed.
            containerDescription.cpuShares = (Integer) null;
        }
    }

    public static Long parsePodContainerMemoryLimit(String podContainerMemoryLimit) {
        Long multiplier;
        Long result;
        char unit;
        try {
            if (podContainerMemoryLimit.charAt(podContainerMemoryLimit.length() - 1) == 'i') {
                multiplier = 1024L;
                unit = podContainerMemoryLimit.charAt(podContainerMemoryLimit.length() - 2);
                result = Long.parseLong(podContainerMemoryLimit.substring(0, podContainerMemoryLimit
                        .length() - 2));
            } else {
                multiplier = 1000L;
                unit = podContainerMemoryLimit.charAt(podContainerMemoryLimit.length() - 1);
                result = Long.parseLong(podContainerMemoryLimit.substring(0, podContainerMemoryLimit
                        .length() - 1));
            }
        } catch (Exception e) {
            return 0L;
        }

        switch (unit) {
        case 'E':
            multiplier = (long) Math.pow(multiplier, 6);
            break;
        case 'P':
            multiplier = (long) Math.pow(multiplier, 5);
            break;
        case 'T':
            multiplier = (long) Math.pow(multiplier, 4);
            break;
        case 'G':
            multiplier = (long) Math.pow(multiplier, 3);
            break;
        case 'M':
            multiplier = (long) Math.pow(multiplier, 2);
            break;
        case 'K':
            break;
        default:
            return 0L;
        }

        return result * multiplier;
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
            ContainerDescription containerDescription = entry.getValue().data;
            PodContainer podContainer = new PodContainer();
            podContainer.name = containerDescription.name;
            podContainer.image = containerDescription.image;
            if (!isNullOrEmpty(containerDescription.command)) {
                // PodContainer hold it's command as 2 separate string arrays - one for command
                // and one for it's args. In this approach we assume that the command is the first
                // element and rest elements are it's args, for ContainerDescription's command.
                podContainer.command = new String[] { containerDescription.command[0] };
                podContainer.args = (String[]) Arrays.stream(containerDescription.command).skip(1)
                        .toArray();
            }
            podContainer.workingDir = containerDescription.workingDir;
            podContainer.env = fromContainerDescriptionEnvsToPodContainersEnvs
                    (containerDescription.env);
            podContainer.ports = fromContainerDescriptionPortsToPodContainerPorts
                    (containerDescription.portBindings);

            if (containerDescription.privileged != null) {
                podContainer.securityContext = new PodContainerSecurityContext();
                podContainer.securityContext.privileged = containerDescription.privileged;
            }

            podContainer.livenessProbe = fromContainerDescriptionHealthConfigToPodContainerProbe
                    (containerDescription.healthConfig);

            podContainers.add(podContainer);
        }
        return podContainers.toArray(new PodContainer[podContainers.size()]);
    }

    public static PodContainerEnvVar[] fromContainerDescriptionEnvsToPodContainersEnvs(String[]
            envs) {
        if (isNullOrEmpty(envs)) {
            return null;
        }
        PodContainerEnvVar[] envVars = new PodContainerEnvVar[envs.length];
        for (int i = 0; i < envs.length; i++) {
            String[] keyValue = envs[i].split("=");
            PodContainerEnvVar podContainerEnvVar = new PodContainerEnvVar();
            podContainerEnvVar.name = keyValue[0];
            podContainerEnvVar.value = keyValue[1];
            envVars[i] = podContainerEnvVar;
        }
        return envVars;
    }

    public static PodContainerPort[] fromContainerDescriptionPortsToPodContainerPorts(
            PortBinding[] ports) {
        if (isNullOrEmpty(ports)) {
            return null;
        }

        PodContainerPort[] podPorts = new PodContainerPort[ports.length];
        for (int i = 0; i < ports.length; i++) {
            PodContainerPort podPort = new PodContainerPort();
            podPort.containerPort = ports[i].containerPort;
            podPort.hostPort = ports[i].hostPort;
            podPort.protocol = ports[i].protocol;
            podPort.hostIp = ports[i].protocol;
            podPorts[i] = podPort;
        }
        return podPorts;
    }

    public static PodContainerProbe fromContainerDescriptionHealthConfigToPodContainerProbe(
            HealthConfig healthConfig) {
        if (healthConfig == null) {
            return null;
        }
        PodContainerProbe probe = new PodContainerProbe();
        switch (healthConfig.protocol) {
        case COMMAND:
            probe.exec = new PodContainerProbeExecAction();
            probe.exec.command = healthConfig.command.split("\\w+");
            break;
        case HTTP:
            probe.httpGet = new PodContainerProbeHTTPGetAction();
            probe.httpGet.path = healthConfig.urlPath;
            probe.httpGet.port = String.valueOf(healthConfig.port);
            break;
        case TCP:
            probe.tcpSocket = new PodContainerProbeTCPSocketAction();
            probe.tcpSocket.port = String.valueOf(healthConfig.port);
            break;
        default:
            return null;
        }
        probe.timeoutSeconds = TimeUnit.MILLISECONDS.toSeconds(healthConfig.timeoutMillis);
        probe.failureThreshold = healthConfig.unhealthyThreshold;
        probe.successThreshold = healthConfig.healthyThreshold;
        return probe;
    }

}
