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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.admiral.compute.content.CompositeTemplateUtil.assertContainersComponentsOnly;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.deserializeCompositeTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.filterComponentTemplates;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.serializeCompositeTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtilTest.assertContainersComponents;
import static com.vmware.admiral.compute.content.CompositeTemplateUtilTest.getContent;
import static com.vmware.admiral.compute.content.kubernetes.ContainerDescriptionToPodContainerConverter.RESOURCES_LIMITS;
import static com.vmware.admiral.compute.content.kubernetes.ContainerDescriptionToPodContainerConverter.fromPodContainerProbeToContainerDescriptionHealthConfig;
import static com.vmware.admiral.compute.content.kubernetes.ContainerDescriptionToPodContainerConverter.parsePodContainerCpuShares;
import static com.vmware.admiral.compute.content.kubernetes.ContainerDescriptionToPodContainerConverter.parsePodContainerMemoryLimit;
import static com.vmware.admiral.compute.content.kubernetes.ContainerDescriptionToPodContainerConverter.setContainerDescriptionResourcesToPodContainerResources;
import static com.vmware.admiral.compute.content.kubernetes.ContainerDescriptionToPodContainerConverter.setPodContainerResourcesToContainerDescriptionResources;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.deserializeKubernetesEntity;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.fromCompositeTemplateToPod;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.fromDeploymentToCompositeTemplate;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.fromPodToCompositeTemplate;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.serializeKubernetesEntity;

import java.io.IOException;
import java.util.HashMap;

import org.junit.Test;

import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.HttpVersion;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.RequestProtocol;
import com.vmware.admiral.compute.content.kubernetes.deployments.Deployment;
import com.vmware.admiral.compute.content.kubernetes.pods.Pod;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainer;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerEnvVar;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerPort;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerProbe;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerProbeHTTPGetAction;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerProbeTCPSocketAction;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerResources;
import com.vmware.xenon.common.Service.Action;

public class KubernetesUtilTest extends ComputeBaseTest {

    @Test
    public void testConvertKubernetesDeploymentToCompositeTemplate() throws IOException {
        CompositeTemplate expectedTemplate = deserializeCompositeTemplate(
                getContent("composite.nginx-mysql.yaml"));

        Deployment deployment = (Deployment) deserializeKubernetesEntity(getContent(
                "kubernetes.deployment.nginx-mysql.yaml"));

        CompositeTemplate actualTemplate = fromDeploymentToCompositeTemplate(deployment);

        assertContainersComponentsOnly(actualTemplate.components);

        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 2,
                actualTemplate.components);
        assertContainersComponents(ResourceType.CONTAINER_NETWORK_TYPE.getContentType(), 0,
                actualTemplate.components);
        assertContainersComponents(ResourceType.CONTAINER_VOLUME_TYPE.getContentType(), 0,
                actualTemplate.components);

        for (ComponentTemplate<ContainerDescription> component :
                filterComponentTemplates(actualTemplate.components, ContainerDescription.class)
                        .values()) {
            assertEquals(deployment.spec.replicas, component.data._cluster);
        }

    }

    @Test
    public void testConvertKubernetesPodToCompositeTemplate() throws IOException {
        CompositeTemplate expectedTemplate = deserializeCompositeTemplate(
                getContent("composite.nginx-mysql.yaml"));

        String expectedTemplateYaml = serializeCompositeTemplate(expectedTemplate);

        Pod pod = (Pod) deserializeKubernetesEntity(getContent("kubernetes.pod.nginx-mysql.yaml"));
        CompositeTemplate actualTemplate = fromPodToCompositeTemplate(pod);

        assertContainersComponentsOnly(actualTemplate.components);

        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 2,
                actualTemplate.components);
        assertContainersComponents(ResourceType.CONTAINER_NETWORK_TYPE.getContentType(), 0,
                actualTemplate.components);
        assertContainersComponents(ResourceType.CONTAINER_VOLUME_TYPE.getContentType(), 0,
                actualTemplate.components);

        String actualTemplateYaml = serializeCompositeTemplate(actualTemplate);

        assertEquals(expectedTemplateYaml, actualTemplateYaml);
    }

    @Test
    public void testConvertCompositeTemplateToKubernetesPod() throws IOException {
        Pod expectedPod = (Pod) deserializeKubernetesEntity(
                getContent("kubernetes.pod.nginx-mysql.yaml"));
        String expectedPodYaml = serializeKubernetesEntity(expectedPod);

        CompositeTemplate template = deserializeCompositeTemplate(
                getContent("composite.nginx-mysql.yaml"));

        Pod actualPod = fromCompositeTemplateToPod(template);
        String actualPodYaml = serializeKubernetesEntity(actualPod);

        assertEquals(expectedPod.kind, actualPod.kind);
        assertEquals(expectedPod.apiVersion, actualPod.apiVersion);
        assertEquals(expectedPod.metadata.name, actualPod.metadata.name);
        assertEquals(expectedPod.spec.containers.length, actualPod.spec.containers.length);

        for (int i = 0; i < expectedPod.spec.containers.length; i++) {
            PodContainer expectedContainer = expectedPod.spec.containers[i];
            PodContainer actualContainer = actualPod.spec.containers[i];

            assertEquals(expectedContainer.name, actualContainer.name);
            assertEquals(expectedContainer.image, actualContainer.image);
            assertEquals(expectedContainer.workingDir, actualContainer.workingDir);

            for (int j = 0; j < expectedContainer.ports.length; j++) {
                PodContainerPort expectedPort = expectedContainer.ports[j];
                PodContainerPort actualPort = actualContainer.ports[j];

                assertEquals(expectedPort.containerPort, actualPort.containerPort);
                assertEquals(expectedPort.hostPort, actualPort.hostPort);
            }

            for (int j = 0; j < expectedContainer.env.length; j++) {
                PodContainerEnvVar expectedEnv = expectedContainer.env[j];
                PodContainerEnvVar actualEnv = actualContainer.env[j];

                assertEquals(expectedEnv.name, actualEnv.name);
                assertEquals(expectedEnv.value, actualEnv.value);
            }
        }

        assertEquals(expectedPodYaml, actualPodYaml);
    }

    @Test
    public void testConvertPodContainerProbeToHealthConfigTCP() {
        PodContainer podContainer = new PodContainer();
        podContainer.livenessProbe = new PodContainerProbe();
        podContainer.livenessProbe.tcpSocket = new PodContainerProbeTCPSocketAction();
        podContainer.livenessProbe.tcpSocket.port = "8080";
        podContainer.livenessProbe.timeoutSeconds = 3L;

        HealthConfig expectedHealthConfig = new HealthConfig();
        expectedHealthConfig.protocol = RequestProtocol.TCP;
        expectedHealthConfig.port = 8080;
        expectedHealthConfig.timeoutMillis = 3000;

        HealthConfig actualHealthConfig = fromPodContainerProbeToContainerDescriptionHealthConfig
                (podContainer);

        assertNotNull(actualHealthConfig);
        assertEquals(actualHealthConfig.protocol, expectedHealthConfig.protocol);
        assertEquals(actualHealthConfig.port, expectedHealthConfig.port);
        assertEquals(actualHealthConfig.timeoutMillis, expectedHealthConfig.timeoutMillis);
    }

    @Test
    public void testConvertPodContainerProbeToHealthConfigHTTP() {
        PodContainer podContainer = new PodContainer();
        podContainer.livenessProbe = new PodContainerProbe();
        podContainer.livenessProbe.httpGet = new PodContainerProbeHTTPGetAction();
        podContainer.livenessProbe.httpGet.port = "8080";
        podContainer.livenessProbe.httpGet.path = "/health";
        podContainer.livenessProbe.timeoutSeconds = 3L;

        HealthConfig expectedHealthConfig = new HealthConfig();
        expectedHealthConfig.protocol = RequestProtocol.HTTP;
        expectedHealthConfig.httpVersion = HttpVersion.HTTP_v1_1;
        expectedHealthConfig.httpMethod = Action.GET;
        expectedHealthConfig.urlPath = "/health";
        expectedHealthConfig.port = 8080;
        expectedHealthConfig.timeoutMillis = 3000;

        HealthConfig actualHealthConfig = fromPodContainerProbeToContainerDescriptionHealthConfig
                (podContainer);

        assertNotNull(actualHealthConfig);
        assertEquals(actualHealthConfig.protocol, expectedHealthConfig.protocol);
        assertEquals(actualHealthConfig.port, expectedHealthConfig.port);
        assertEquals(actualHealthConfig.httpMethod, expectedHealthConfig.httpMethod);
        assertEquals(actualHealthConfig.httpVersion, expectedHealthConfig.httpVersion);
        assertEquals(actualHealthConfig.urlPath, expectedHealthConfig.urlPath);
        assertEquals(actualHealthConfig.timeoutMillis, expectedHealthConfig.timeoutMillis);
    }

    @Test
    public void testParsePodContainerMemoryLimit() {
        String[] in = new String[] { "100M", "100Mi", "100K", "100Ki" };
        Long[] out = new Long[] { 100000000L, 104857600L, 100000L, 102400L };
        for (int i = 0; i < in.length; i++) {
            Long actual = parsePodContainerMemoryLimit(in[i]);
            assertEquals("Failed on iteration: " + i, out[i], actual);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsePodContainerMemoryLimitInvalidValue() {
        String failIn = "52x34Li";
        parsePodContainerMemoryLimit(failIn);
    }

    @Test
    public void testParsePodContainerCpuShares() {
        String[] in = new String[] { "500m", "2300m", "1000m", "3700m" };
        Integer[] out = new Integer[] { 1, 2, 1, 3 };

        for (int i = 0; i < in.length; i++) {
            Integer actual = parsePodContainerCpuShares(in[i]);
            assertEquals("Failed on iteration: " + i, out[i], actual);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsePodContainerCpuSharesInvalidValue() {
        String failIn = "500z";
        parsePodContainerCpuShares(failIn);
    }

    @Test
    public void testSetPodContainerResourcesToContainerDescriptionResources() {
        PodContainer podContainer = new PodContainer();
        podContainer.resources = new HashMap<>();
        PodContainerResources podContainerResources = new PodContainerResources();
        podContainerResources.memory = "100M";
        podContainerResources.cpu = "500m";
        podContainer.resources.put(RESOURCES_LIMITS, podContainerResources);

        ContainerDescription containerDescription = new ContainerDescription();

        setPodContainerResourcesToContainerDescriptionResources(podContainer, containerDescription);

        Long expectedMemoryLimit = 100000000L;
        Long actualMemoryLimit = containerDescription.memoryLimit;

        Integer expectedCpuShares = 1;
        Integer actualCpuShares = containerDescription.cpuShares;

        assertEquals(expectedMemoryLimit, actualMemoryLimit);
        assertEquals(expectedCpuShares, actualCpuShares);
    }

    @Test
    public void testSetContainerDescriptionResourcesToPodContainerResources() {
        ContainerDescription description = new ContainerDescription();
        description.memoryLimit = 100000L;
        description.cpuShares = 3;

        PodContainer podContainer = new PodContainer();

        setContainerDescriptionResourcesToPodContainerResources(description, podContainer);

        String expectedPodContainerMemoryLimit = "100000";
        String expectedPodContainerCpuShares = "3";

        String actualPodContainerMemoryLimit = podContainer.resources.get(RESOURCES_LIMITS).memory;
        String actualPodContainerCpuShares = podContainer.resources.get(RESOURCES_LIMITS).cpu;

        assertEquals(expectedPodContainerMemoryLimit, actualPodContainerMemoryLimit);
        assertEquals(expectedPodContainerCpuShares, actualPodContainerCpuShares);
    }
}
