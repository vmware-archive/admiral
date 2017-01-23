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
import static org.junit.Assert.assertNull;

import static com.vmware.admiral.common.util.FileUtil.switchToUnixLineEnds;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.deserializeCompositeTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtilTest.getContent;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.RESOURCES_LIMITS;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromCompositeProtocolToKubernetesProtocol;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromContainerDescriptionHealthConfigToPodContainerProbe;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromPodContainerCommandToContainerDescriptionCommand;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromPodContainerProbeToContainerDescriptionHealthConfig;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.parsePodContainerCpuShares;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.parsePodContainerMemoryLimit;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.setContainerDescriptionResourcesToPodContainerResources;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.setPodContainerResourcesToContainerDescriptionResources;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.fromCompositeTemplateToKubernetesTemplate;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.serializeKubernetesEntity;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.serializeKubernetesTemplate;

import java.io.IOException;
import java.util.HashMap;

import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.HttpVersion;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.RequestProtocol;
import com.vmware.admiral.compute.content.kubernetes.KubernetesTemplate;
import com.vmware.admiral.compute.content.kubernetes.deployments.Deployment;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainer;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerProbe;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerProbeExecAction;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerProbeHTTPGetAction;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerProbeTCPSocketAction;
import com.vmware.admiral.compute.content.kubernetes.pods.PodContainerResources;
import com.vmware.admiral.compute.content.kubernetes.services.Service;
import com.vmware.xenon.common.Service.Action;

public class KubernetesUtilTest extends ComputeBaseTest {

    @Test
    public void testConvertCompositeTemplateToKubernetesTemplate() throws IOException {
        CompositeTemplate template = deserializeCompositeTemplate(
                getContent("composite.wordpress.kubernetes.yaml"));

        KubernetesTemplate kubernetesTemplate = fromCompositeTemplateToKubernetesTemplate(template);

        assertEquals(2, kubernetesTemplate.deployments.size());
        assertEquals(2, kubernetesTemplate.services.size());

        Service wordpressService = kubernetesTemplate.services.get("wordpress");
        Service mysqlService = kubernetesTemplate.services.get("db");
        Deployment wordpressDeployment = kubernetesTemplate.deployments.get("wordpress");
        Deployment mysqlDeployment = kubernetesTemplate.deployments.get("db");

        String wordpressDeploymentSerialized = serializeKubernetesEntity(wordpressDeployment);
        String mysqlDeploymentSerialized = serializeKubernetesEntity(mysqlDeployment);
        String wordpressServiceSerialized = serializeKubernetesEntity(wordpressService);
        String mysqlServiceSerialized = serializeKubernetesEntity(mysqlService);

        String expectedWordpressDeployment = getContent("kubernetes.wordpress.deployment.yaml");
        String expectedWordpressService = getContent("kubernetes.wordpress.service.yaml");
        String expectedMysqlDeployment = getContent("kubernetes.mysql.deployment.yaml");
        String expectedMySqlService = getContent("kubernetes.mysql.service.yaml");

        assertEquals(switchToUnixLineEnds(expectedMysqlDeployment).trim(),
                mysqlDeploymentSerialized);
        assertEquals(switchToUnixLineEnds(expectedWordpressService).trim(),
                wordpressServiceSerialized);
        assertEquals(switchToUnixLineEnds(expectedWordpressDeployment).trim(),
                wordpressDeploymentSerialized);
        assertEquals(switchToUnixLineEnds(expectedMySqlService).trim(),
                mysqlServiceSerialized);

        StringBuilder builder = new StringBuilder();
        builder.append(expectedWordpressService);
        builder.append("\n");
        builder.append(expectedMySqlService);
        builder.append("\n");
        builder.append(expectedWordpressDeployment);
        builder.append("\n");
        builder.append(expectedMysqlDeployment);
        builder.append("\n");

        String kubernetesTemplateSerialized = serializeKubernetesTemplate(kubernetesTemplate);
        assertEquals(switchToUnixLineEnds(builder.toString()).trim(), kubernetesTemplateSerialized);
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

    @Test(expected = IllegalArgumentException.class)
    public void testFromCompositeProtocolToKubernetesProtocol() {
        String expectedProtocol = "TCP";
        String actualProtocol = fromCompositeProtocolToKubernetesProtocol("tcp");
        assertEquals(expectedProtocol, actualProtocol);

        expectedProtocol = "UDP";
        actualProtocol = fromCompositeProtocolToKubernetesProtocol("udp");
        assertEquals(expectedProtocol, actualProtocol);

        expectedProtocol = null;
        actualProtocol = fromCompositeProtocolToKubernetesProtocol(null);
        assertEquals(expectedProtocol, actualProtocol);

        fromCompositeProtocolToKubernetesProtocol("invalid");
    }

    @Test
    public void testFromContainerDescriptionHealthConfigToPodContainerProbe() {
        PodContainerProbe expectedProbe1 = new PodContainerProbe();
        expectedProbe1.exec = new PodContainerProbeExecAction();
        expectedProbe1.exec.command = new String[] { "test", "command" };
        expectedProbe1.timeoutSeconds = 1L;
        expectedProbe1.failureThreshold = 3;
        expectedProbe1.successThreshold = 1;

        PodContainerProbe expectedProbe2 = new PodContainerProbe();
        expectedProbe2.httpGet = new PodContainerProbeHTTPGetAction();
        expectedProbe2.httpGet.path = "/test";
        expectedProbe2.httpGet.port = "32000";

        PodContainerProbe expectedProbe3 = new PodContainerProbe();
        expectedProbe3.tcpSocket = new PodContainerProbeTCPSocketAction();
        expectedProbe3.tcpSocket.port = "32000";

        HealthConfig healthConfig1 = new HealthConfig();
        healthConfig1.protocol = RequestProtocol.COMMAND;
        healthConfig1.command = "test command";
        healthConfig1.timeoutMillis = 1000;
        healthConfig1.unhealthyThreshold = 3;
        healthConfig1.healthyThreshold = 1;

        HealthConfig healthConfig2 = new HealthConfig();
        healthConfig2.protocol = RequestProtocol.HTTP;
        healthConfig2.urlPath = "/test";
        healthConfig2.port = 32000;

        HealthConfig healthConfig3 = new HealthConfig();
        healthConfig3.protocol = RequestProtocol.TCP;
        healthConfig3.port = 32000;

        PodContainerProbe actualProbe1 = fromContainerDescriptionHealthConfigToPodContainerProbe
                (healthConfig1);

        PodContainerProbe actualProbe2 = fromContainerDescriptionHealthConfigToPodContainerProbe
                (healthConfig2);

        PodContainerProbe actualProbe3 = fromContainerDescriptionHealthConfigToPodContainerProbe
                (healthConfig3);

        assertNotNull(actualProbe1.exec);
        for (int i = 0; i < expectedProbe1.exec.command.length; i++) {
            assertEquals(expectedProbe1.exec.command[i], actualProbe1.exec.command[i]);
        }
        assertEquals(expectedProbe1.timeoutSeconds, actualProbe1.timeoutSeconds);
        assertEquals(expectedProbe1.failureThreshold, actualProbe1.failureThreshold);
        assertEquals(expectedProbe1.successThreshold, actualProbe1.successThreshold);

        assertNotNull(actualProbe2.httpGet);
        assertEquals(expectedProbe2.httpGet.path, actualProbe2.httpGet.path);
        assertEquals(expectedProbe2.httpGet.port, actualProbe2.httpGet.port);

        assertNotNull(actualProbe3.tcpSocket);
        assertEquals(expectedProbe3.tcpSocket.port, actualProbe3.tcpSocket.port);
    }

    @Test
    public void testFromPodContainerCommandToContainerDescriptionCommand() {
        assertNull(fromPodContainerCommandToContainerDescriptionCommand(null, null));

        assertNull(fromPodContainerCommandToContainerDescriptionCommand(new String[] {}, new
                String[] { "ps" }));

        String[] podCommand = new String[] { "admiral", "rm" };
        String[] podCommandArgs = new String[] { "container1", "container2", "container3" };

        String[] expectedContainerDescriptionCmd = new String[] { "admiral", "rm", "container1",
                "container2", "container3" };
        String[] actualContainerDescriptionCmd =
                fromPodContainerCommandToContainerDescriptionCommand(podCommand, podCommandArgs);

        for (int i = 0; i < expectedContainerDescriptionCmd.length; i++) {
            assertEquals(expectedContainerDescriptionCmd[i], actualContainerDescriptionCmd[i]);
        }

        String[] podCommand1 = new String[] { "admiral", "login" };
        String[] expectedContainerDescriptionCmd1 = new String[] { "admiral", "login" };
        String[] actualContainerDescriptionCmd1 =
                fromPodContainerCommandToContainerDescriptionCommand(podCommand1, null);

        assertEquals(expectedContainerDescriptionCmd1.length,
                actualContainerDescriptionCmd1.length);

        for (int i = 0; i < expectedContainerDescriptionCmd1.length; i++) {
            assertEquals(expectedContainerDescriptionCmd1[i], actualContainerDescriptionCmd1[i]);
        }
    }
}
