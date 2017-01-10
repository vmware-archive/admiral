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
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.fromDescriptionToComponentTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.isNullOrEmpty;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

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

    public static Pod fromCompositeTemplateToPodTemplate(CompositeTemplate template) {
        return null;
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
        description.command = fromPodContainerCommandToCompositeComponentCommand(podContainer
                .command, podContainer.args);
        description.workingDir = podContainer.workingDir;
        description.portBindings = fromPodContainerPortsToCompositeComponentPortBindings(
                podContainer.ports);
        description.env = fromPodContainerEnvVarToCompositeComponentEnv(podContainer.env);
        description.privileged = fromPodPrivilegedModeToCompositeComponentPrivilegedMode
                (podContainer);
        description.restartPolicy = podSpec.restartPolicy;
        description.healthConfig = fromPodContainerProbeToCompositeComponentHealthConfig
                (podContainer);

        NestedState nestedState = new NestedState();
        nestedState.object = description;

        return fromDescriptionToComponentTemplate(nestedState, ResourceType.CONTAINER_TYPE
                .getName());

    }

    public static String[] fromPodContainerCommandToCompositeComponentCommand(String[]
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

    public static PortBinding[] fromPodContainerPortsToCompositeComponentPortBindings(
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

    public static String[] fromPodContainerEnvVarToCompositeComponentEnv(PodContainerEnvVar[] env) {
        if (isNullOrEmpty(env)) {
            return null;
        }
        String[] compositeComponentEnvVars = new String[env.length];
        for (int i = 0; i < env.length; i++) {
            compositeComponentEnvVars[i] = String.format("%s=%s", env[i].name, env[i].value);
        }
        return compositeComponentEnvVars;
    }

    public static Boolean fromPodPrivilegedModeToCompositeComponentPrivilegedMode(PodContainer
            podContainer) {
        if (podContainer.securityContext == null) {
            return (Boolean) null;
        }
        return podContainer.securityContext.privileged;
    }

    public static HealthConfig fromPodContainerProbeToCompositeComponentHealthConfig(PodContainer
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
        } else if (probe.tcpSocket != null) {
            healthConfig.protocol = RequestProtocol.TCP;
            healthConfig.port = fromPodContainerProbePortToHealthCheckPort(podContainer);
            healthConfig.timeoutMillis = (int) TimeUnit.SECONDS.toMillis(probe.timeoutSeconds);
        } else {
            healthConfig = null;
        }
        return healthConfig;
    }

    private static Integer fromPodContainerProbePortToHealthCheckPort(PodContainer podContainer) {
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
}
