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

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.isNullOrEmpty;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.DEPLOYMENT_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.KUBERNETES_API_VERSION_V1;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.KUBERNETES_API_VERSION_V1_BETA1;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.KUBERNETES_LABEL_APP;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.KUBERNETES_LABEL_TIER;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.SERVICE_TYPE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.HttpVersion;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.RequestProtocol;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.content.kubernetes.deployments.Deployment;
import com.vmware.admiral.compute.content.kubernetes.deployments.DeploymentSpec;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainer;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerEnvVar;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerPort;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerProbe;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerProbeExecAction;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerProbeHTTPGetAction;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerProbeTCPSocketAction;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerResources;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerSecurityContext;
import com.vmware.admiral.compute.content.kubernetes.pods.PodSpec;
import com.vmware.admiral.compute.content.kubernetes.pods.PodTemplateSpec;
import com.vmware.admiral.compute.content.kubernetes.services.Service;
import com.vmware.admiral.compute.content.kubernetes.services.ServicePort;
import com.vmware.admiral.compute.content.kubernetes.services.ServiceSpec;
import com.vmware.xenon.common.Service.Action;

public class KubernetesConverter {

    public static enum KubernetesProtocol {
        TCP, UDP
    }

    public static final String RESOURCES_LIMITS = "limits";

    public static final String KUBERNETES_RESTART_POLICY_NEVER = "Never";
    public static final String KUBERNETES_RESTART_POLICY_ON_FAILURE = "OnFailure";
    public static final String KUBERNETES_RESTART_POLICY_ALWAYS = "Always";

    //From PodContainer to ContainerDescription
    public static ContainerDescription fromPodContainerToContainerDescription(PodContainer
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

        return description;
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
            portBindings[i] = fromPodContainerPortToPortBinding(ports[i]);
        }
        return portBindings;
    }

    public static PortBinding fromPodContainerPortToPortBinding(PodContainerPort podContainerPort) {
        PortBinding portBinding = new PortBinding();
        portBinding.protocol = podContainerPort.protocol;
        portBinding.containerPort = String.valueOf(podContainerPort.containerPort);
        portBinding.hostIp = podContainerPort.hostIp;
        portBinding.hostPort = String.valueOf(podContainerPort.hostPort);

        return portBinding;
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
                    result = port.containerPort;
                }
            }
        }
        return result;
    }

    public static void setPodContainerResourcesToContainerDescriptionResources(PodContainer
            podContainer, ContainerDescription containerDescription) {
        if ((isNullOrEmpty(podContainer.resources)) || (!podContainer.resources
                .containsKey(RESOURCES_LIMITS))) {
            return;
        }
        String podContainerMemoryLimit = podContainer.resources.get(RESOURCES_LIMITS).memory;
        String podContainerCpuShares = podContainer.resources.get(RESOURCES_LIMITS).cpu;
        try {
            containerDescription.memoryLimit = Long.parseLong(podContainerMemoryLimit);
            containerDescription.cpuShares = Integer.parseInt(podContainerCpuShares);
        } catch (NumberFormatException nfe) {
            containerDescription.memoryLimit = parsePodContainerMemoryLimit
                    (podContainerMemoryLimit);
            containerDescription.cpuShares = parsePodContainerCpuShares(podContainerCpuShares);
        }
    }

    public static Long parsePodContainerMemoryLimit(String podContainerMemoryLimit) {
        if (podContainerMemoryLimit == null || podContainerMemoryLimit.equals("")) {
            return (Long) null;
        }
        Long multiplier;
        Long result;
        char unit;

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

        if (!Character.isLetter(unit)) {
            throw new IllegalArgumentException("Invalid memory value");
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
            throw new IllegalArgumentException("Invalid memory unit: " + unit);
        }

        return result * multiplier;
    }

    public static Integer parsePodContainerCpuShares(String cpuShares) {
        if (cpuShares == null || cpuShares.trim().equals("")) {
            return (Integer) null;
        }
        char unit = cpuShares.charAt(cpuShares.length() - 1);
        if (unit != 'm') {
            throw new IllegalArgumentException("Invalid cpuShares value.");
        }
        Double result = Double.parseDouble(cpuShares.substring(0, cpuShares.length() - 1));
        result = result / 1000;
        return Math.max(1, result.intValue());
    }

    public static PodContainer fromContainerDescriptionToPodContainer(
            ContainerDescription description) {
        if (description == null) {
            return null;
        }
        PodContainer podContainer = new PodContainer();
        podContainer.name = description.name;
        podContainer.image = description.image;
        podContainer.workingDir = description.workingDir;
        podContainer.env = fromContainerDescriptionEnvsToPodContainersEnvs(description.env);
        podContainer.ports = fromContainerDescriptionPortsToPodContainerPorts(
                description.portBindings);
        podContainer.livenessProbe = fromContainerDescriptionHealthConfigToPodContainerProbe
                (description.healthConfig);

        setContainerDescriptionResourcesToPodContainerResources(description, podContainer);

        if (!isNullOrEmpty(description.command)) {
            // PodContainer hold it's command as 2 separate string arrays - one for command
            // and one for it's args. In this approach we assume that the command is the first
            // element and rest elements are it's args, for ContainerDescription's command.
            podContainer.command = new String[] { description.command[0] };
            podContainer.args = (String[]) Arrays.stream(description.command).skip(1)
                    .toArray();
        }
        if (description.privileged != null) {
            podContainer.securityContext = new PodContainerSecurityContext();
            podContainer.securityContext.privileged = description.privileged;
        }

        return podContainer;
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
            podPort.containerPort = Integer.parseInt(ports[i].containerPort);
            podPort.protocol = fromCompositeProtocolToKubernetesProtocol(ports[i].protocol);
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
            probe.exec.command = healthConfig.command.split("\\s+");
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
        if (healthConfig.timeoutMillis != null) {
            probe.timeoutSeconds = TimeUnit.MILLISECONDS.toSeconds(healthConfig.timeoutMillis);
        }
        probe.failureThreshold = healthConfig.unhealthyThreshold;
        probe.successThreshold = healthConfig.healthyThreshold;
        return probe;
    }

    public static void setContainerDescriptionResourcesToPodContainerResources(ContainerDescription
            description, PodContainer podContainer) {
        if ((description.memoryLimit == null || description.memoryLimit <= 0) && (description
                .cpuShares == null || description.cpuShares <= 0)) {
            return;
        }
        podContainer.resources = new HashMap<>();
        podContainer.resources.put(RESOURCES_LIMITS, new PodContainerResources());
        podContainer.resources.get(RESOURCES_LIMITS).memory = String
                .valueOf(description.memoryLimit);
        podContainer.resources.get(RESOURCES_LIMITS).cpu = String.valueOf(description.cpuShares);
    }

    public static Deployment fromContainerDescriptionToDeployment(
            ContainerDescription description, String templateName) {
        if (description == null) {
            return null;
        }

        Deployment deployment = new Deployment();
        deployment.apiVersion = KUBERNETES_API_VERSION_V1_BETA1;
        deployment.kind = DEPLOYMENT_TYPE;
        deployment.metadata = new ObjectMeta();
        deployment.metadata.name = description.name;
        deployment.metadata.labels = new HashMap<>();
        deployment.metadata.labels.put(KUBERNETES_LABEL_APP, templateName);
        deployment.spec = new DeploymentSpec();
        deployment.spec.replicas = (description._cluster == null) ? Integer.valueOf(1) : description
                ._cluster;
        deployment.spec.template = new PodTemplateSpec();
        deployment.spec.template.metadata = new ObjectMeta();
        deployment.spec.template.metadata.labels = new HashMap<>();
        deployment.spec.template.metadata.labels.put(KUBERNETES_LABEL_APP, templateName);
        deployment.spec.template.metadata.labels.put(KUBERNETES_LABEL_TIER, description.name);
        deployment.spec.template.spec = new PodSpec();
        deployment.spec.template.spec.restartPolicy =
                fromContainerDescriptionRestartPolicyToPodRestartPolicy(description.restartPolicy);
        PodContainer podContainer = fromContainerDescriptionToPodContainer(description);
        deployment.spec.template.spec.containers = new PodContainer[] { podContainer };

        return deployment;
    }

    public static Service fromContainerDescriptionToService(ContainerDescription description,
            String templateName) {
        if (description == null) {
            return null;
        }

        Service service = new Service();
        service.apiVersion = KUBERNETES_API_VERSION_V1;
        service.kind = SERVICE_TYPE;
        service.metadata = new ObjectMeta();
        service.metadata.name = description.name;
        service.metadata.labels = new HashMap<>();
        service.metadata.labels.put(KUBERNETES_LABEL_APP, templateName);
        service.spec = new ServiceSpec();
        service.spec.selector = new HashMap<>();
        service.spec.selector.put(KUBERNETES_LABEL_APP, templateName);
        service.spec.selector.put(KUBERNETES_LABEL_TIER, description.name);
        service.spec.ports = fromContainerDescriptionPortsToServicePorts(description);
        return service;
    }

    public static ServicePort[] fromContainerDescriptionPortsToServicePorts(ContainerDescription
            description) {
        ServicePort[] servicePorts = new ServicePort[description.portBindings.length];

        for (int i = 0; i < description.portBindings.length; i++) {
            ServicePort port = new ServicePort();
            port.name = description.portBindings[i].hostPort;
            port.port = Integer.parseInt(description.portBindings[i].hostPort);
            port.targetPort = Integer.parseInt(description.portBindings[i].containerPort);
            port.protocol = fromCompositeProtocolToKubernetesProtocol(description.portBindings[i]
                    .protocol);
            servicePorts[i] = port;
        }
        return servicePorts;
    }

    public static String fromContainerDescriptionRestartPolicyToPodRestartPolicy(String
            descriptionRestartPolicy) {
        if (descriptionRestartPolicy == null) {
            return null;
        }

        switch (descriptionRestartPolicy) {
        case "no":
            return KUBERNETES_RESTART_POLICY_NEVER;
        case "always":
            return KUBERNETES_RESTART_POLICY_ALWAYS;
        case "on-failure":
            return KUBERNETES_RESTART_POLICY_ON_FAILURE;
        default:
            throw new IllegalArgumentException("Invalid restart policy.");
        }
    }

    public static String fromCompositeProtocolToKubernetesProtocol(String compositeProtocol) {
        if (compositeProtocol == null) {
            return null;
        }
        try {
            return KubernetesProtocol.valueOf(compositeProtocol.toUpperCase()).name();
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Invalid port protocol");
        }
    }
}
