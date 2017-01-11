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
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.serializeCompositeTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtilTest.assertContainersComponents;
import static com.vmware.admiral.compute.content.CompositeTemplateUtilTest.getContent;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.deserializeKubernetesEntity;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.fromCompositeTemplateToPod;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.fromPodContainerProbeToContainerDescriptionHealthConfig;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.fromPodToCompositeTemplate;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.parsePodContainerMemoryLimit;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.serializeKubernetesEntity;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.setPodContainerResourcesToContainerDescriptionResources;

import java.io.IOException;
import java.util.HashMap;

import org.junit.Test;

import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.HttpVersion;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.RequestProtocol;
import com.vmware.admiral.compute.content.kubernetes.Pod;
import com.vmware.admiral.compute.content.kubernetes.PodContainer;
import com.vmware.admiral.compute.content.kubernetes.PodContainerEnvVar;
import com.vmware.admiral.compute.content.kubernetes.PodContainerPort;
import com.vmware.admiral.compute.content.kubernetes.PodContainerProbe;
import com.vmware.admiral.compute.content.kubernetes.PodContainerProbeHTTPGetAction;
import com.vmware.admiral.compute.content.kubernetes.PodContainerProbeTCPSocketAction;
import com.vmware.admiral.compute.content.kubernetes.PodContainerResources;
import com.vmware.xenon.common.Service.Action;

public class KubernetesUtilTest extends ComputeBaseTest {

    @Test
    public void testConvertKubernetesPodToCompositeTemplate() throws IOException {
        CompositeTemplate expectedTemplate = deserializeCompositeTemplate(
                getContent("composite.nginx-mysql.yaml"));

        String expectedTemplateYaml = serializeCompositeTemplate(expectedTemplate);

        Pod pod = (Pod) deserializeKubernetesEntity(getContent("kubernetes.nginx-mysql.yaml"));
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
                getContent("kubernetes.nginx-mysql.yaml"));
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
        String[] in = new String[] { "100M", "100Mi", "100K", "100Ki", "Invalid" };
        Long[] out = new Long[] { 100000000L, 104857600L, 100000L, 102400L, 0L };
        for (int i = 0; i < in.length; i++) {
            Long actual = parsePodContainerMemoryLimit(in[i]);
            assertEquals(out[i], actual);
        }
    }

    @Test
    public void testSetPodContainerResourcesToContainerDescriptionResources() {
        PodContainer podContainer = new PodContainer();
        podContainer.resources = new HashMap<>();
        PodContainerResources podContainerResources = new PodContainerResources();
        podContainerResources.memory = "100M";
        podContainer.resources.put("limits", podContainerResources);

        ContainerDescription containerDescription = new ContainerDescription();

        setPodContainerResourcesToContainerDescriptionResources(podContainer, containerDescription);

        Long expectedMemoryLimit = 100000000L;
        Long actualMemoryLimit = containerDescription.memoryLimit;

        assertEquals(expectedMemoryLimit, actualMemoryLimit);
    }
}
