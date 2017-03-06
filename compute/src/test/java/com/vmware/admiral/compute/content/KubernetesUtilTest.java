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

import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.DEPLOYMENT_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.KUBERNETES_LABEL_APP_ID;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.SERVICE_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.fromResourceStateToBaseKubernetesState;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.getStateTypeFromSelfLink;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.entities.deployments.Deployment;
import com.vmware.admiral.compute.kubernetes.entities.services.Service;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.host.HostInitComputeServicesConfig;

public class KubernetesUtilTest {

    private static final String deploymentYaml = "apiVersion: extensions/v1beta1\n"
            + "kind: Deployment\n"
            + "metadata:\n"
            + "  name: wordpress-mysql\n"
            + "  labels:\n"
            + "    app: wordpress\n"
            + "spec:\n"
            + "  strategy:\n"
            + "    type: Recreate\n"
            + "  template:\n"
            + "    metadata:\n"
            + "      labels:\n"
            + "        app: wordpress\n"
            + "        tier: mysql\n"
            + "    spec:\n"
            + "      containers:\n"
            + "      - image: mysql:5.6\n"
            + "        name: mysql\n"
            + "        env:\n"
            + "        - name: MYSQL_ROOT_PASSWORD\n"
            + "          value: pass@word01\n"
            + "        ports:\n"
            + "        - containerPort: 3306\n"
            + "          name: mysql";

    private static final String invalidDeploymentYaml = "apiVersion: extensions/v1beta1\n"
            + "kind: Deployment\n";

    private static final String deploymentWithoutSpecYaml = "apiVersion: extensions/v1beta1\n"
            + "kind: Deployment\n"
            + "metadata:\n"
            + "  name: wordpress-mysql\n"
            + "  labels:\n"
            + "    app: wordpress\n";

    private static final String serviceYamlFormat = "---\n" +
            "apiVersion: \"v1\"\n" +
            "kind: \"Service\"\n" +
            "metadata:\n" +
            "  name: \"db_sufix\"\n" +
            "  labels:\n" +
            "    app: \"my-app\"\n" +
            "spec:\n" +
            "  ports:\n" +
            "  - name: \"3306\"\n" +
            "    port: 3306\n" +
            "    protocol: \"TCP\"\n" +
            "    targetPort: 3306\n" +
            "  selector:\n" +
            "    app: \"my-app\"\n" +
            "    tier: \"db\"\n";

    @Before
    public void beforeForKubernetesUtilTest() throws Throwable {
        HostInitComputeServicesConfig.initCompositeComponentRegistry();
    }

    // @Test
    // public void testConvertCompositeTemplateToKubernetesTemplate() throws IOException {
    //     CompositeTemplate template = deserializeCompositeTemplate(
    //             getContent("composite.wordpress.kubernetes.yaml"));
    //
    //     KubernetesTemplate kubernetesTemplate = fromCompositeTemplateToKubernetesTemplate(template);
    //
    //     assertEquals(2, kubernetesTemplate.deployments.size());
    //     assertEquals(2, kubernetesTemplate.services.size());
    //
    //     Service wordpressService = kubernetesTemplate.services.get("wordpress");
    //     Service mysqlService = kubernetesTemplate.services.get("db");
    //     Deployment wordpressDeployment = kubernetesTemplate.deployments.get("wordpress");
    //     Deployment mysqlDeployment = kubernetesTemplate.deployments.get("db");
    //
    //     String wordpressDeploymentSerialized = serializeKubernetesEntity(wordpressDeployment);
    //     String mysqlDeploymentSerialized = serializeKubernetesEntity(mysqlDeployment);
    //     String wordpressServiceSerialized = serializeKubernetesEntity(wordpressService);
    //     String mysqlServiceSerialized = serializeKubernetesEntity(mysqlService);
    //
    //     String expectedWordpressDeployment = getContent("kubernetes.wordpress.deployment.yaml");
    //     String expectedWordpressService = getContent("kubernetes.wordpress.service.yaml");
    //     String expectedMysqlDeployment = getContent("kubernetes.mysql.deployment.yaml");
    //     String expectedMySqlService = getContent("kubernetes.mysql.service.yaml");
    //
    //     assertEquals(switchToUnixLineEnds(expectedMysqlDeployment).trim(),
    //             mysqlDeploymentSerialized);
    //     assertEquals(switchToUnixLineEnds(expectedWordpressService).trim(),
    //             wordpressServiceSerialized);
    //     assertEquals(switchToUnixLineEnds(expectedWordpressDeployment).trim(),
    //             wordpressDeploymentSerialized);
    //     assertEquals(switchToUnixLineEnds(expectedMySqlService).trim(),
    //             mysqlServiceSerialized);
    //
    //     StringBuilder builder = new StringBuilder();
    //     builder.append(expectedWordpressService);
    //     builder.append("\n");
    //     builder.append(expectedMySqlService);
    //     builder.append("\n");
    //     builder.append(expectedWordpressDeployment);
    //     builder.append("\n");
    //     builder.append(expectedMysqlDeployment);
    //     builder.append("\n");
    //
    //     String kubernetesTemplateSerialized = serializeKubernetesTemplate(kubernetesTemplate);
    //     assertEquals(switchToUnixLineEnds(builder.toString()).trim(), kubernetesTemplateSerialized);
    // }
    //
    // @Test
    // public void testConvertPodContainerProbeToHealthConfigTCP() {
    //     PodContainer podContainer = new PodContainer();
    //     podContainer.livenessProbe = new PodContainerProbe();
    //     podContainer.livenessProbe.tcpSocket = new PodContainerProbeTCPSocketAction();
    //     podContainer.livenessProbe.tcpSocket.port = "8080";
    //     podContainer.livenessProbe.timeoutSeconds = 3L;
    //
    //     HealthConfig expectedHealthConfig = new HealthConfig();
    //     expectedHealthConfig.protocol = RequestProtocol.TCP;
    //     expectedHealthConfig.port = 8080;
    //     expectedHealthConfig.timeoutMillis = 3000;
    //
    //     HealthConfig actualHealthConfig = fromPodContainerProbeToContainerDescriptionHealthConfig(
    //             podContainer);
    //
    //     assertNotNull(actualHealthConfig);
    //     assertEquals(actualHealthConfig.protocol, expectedHealthConfig.protocol);
    //     assertEquals(actualHealthConfig.port, expectedHealthConfig.port);
    //     assertEquals(actualHealthConfig.timeoutMillis, expectedHealthConfig.timeoutMillis);
    // }
    //
    // @Test
    // public void testConvertPodContainerProbeToHealthConfigHTTP() {
    //     PodContainer podContainer = new PodContainer();
    //     podContainer.livenessProbe = new PodContainerProbe();
    //     podContainer.livenessProbe.httpGet = new PodContainerProbeHTTPGetAction();
    //     podContainer.livenessProbe.httpGet.port = "8080";
    //     podContainer.livenessProbe.httpGet.path = "/health";
    //     podContainer.livenessProbe.timeoutSeconds = 3L;
    //
    //     HealthConfig expectedHealthConfig = new HealthConfig();
    //     expectedHealthConfig.protocol = RequestProtocol.HTTP;
    //     expectedHealthConfig.httpVersion = HttpVersion.HTTP_v1_1;
    //     expectedHealthConfig.httpMethod = Action.GET;
    //     expectedHealthConfig.urlPath = "/health";
    //     expectedHealthConfig.port = 8080;
    //     expectedHealthConfig.timeoutMillis = 3000;
    //
    //     HealthConfig actualHealthConfig = fromPodContainerProbeToContainerDescriptionHealthConfig(
    //             podContainer);
    //
    //     assertNotNull(actualHealthConfig);
    //     assertEquals(actualHealthConfig.protocol, expectedHealthConfig.protocol);
    //     assertEquals(actualHealthConfig.port, expectedHealthConfig.port);
    //     assertEquals(actualHealthConfig.httpMethod, expectedHealthConfig.httpMethod);
    //     assertEquals(actualHealthConfig.httpVersion, expectedHealthConfig.httpVersion);
    //     assertEquals(actualHealthConfig.urlPath, expectedHealthConfig.urlPath);
    //     assertEquals(actualHealthConfig.timeoutMillis, expectedHealthConfig.timeoutMillis);
    // }
    //
    // @Test
    // public void testParsePodContainerMemoryLimit() {
    //     String[] in = new String[] { "100M", "100Mi", "100K", "100Ki" };
    //     Long[] out = new Long[] { 100000000L, 104857600L, 100000L, 102400L };
    //     for (int i = 0; i < in.length; i++) {
    //         Long actual = parsePodContainerMemoryLimit(in[i]);
    //         assertEquals("Failed on iteration: " + i, out[i], actual);
    //     }
    // }
    //
    // @Test(expected = IllegalArgumentException.class)
    // public void testParsePodContainerMemoryLimitInvalidValue() {
    //     String failIn = "52x34Li";
    //     parsePodContainerMemoryLimit(failIn);
    // }
    //
    // @Test
    // public void testParsePodContainerCpuShares() {
    //     String[] in = new String[] { "500m", "2300m", "1000m", "3700m" };
    //     Integer[] out = new Integer[] { 1, 2, 1, 3 };
    //
    //     for (int i = 0; i < in.length; i++) {
    //         Integer actual = parsePodContainerCpuShares(in[i]);
    //         assertEquals("Failed on iteration: " + i, out[i], actual);
    //     }
    // }
    //
    // @Test(expected = IllegalArgumentException.class)
    // public void testParsePodContainerCpuSharesInvalidValue() {
    //     String failIn = "500z";
    //     parsePodContainerCpuShares(failIn);
    // }
    //
    // @Test
    // public void testSetPodContainerResourcesToContainerDescriptionResources() {
    //     PodContainer podContainer = new PodContainer();
    //     podContainer.resources = new HashMap<>();
    //     PodContainerResources podContainerResources = new PodContainerResources();
    //     podContainerResources.memory = "100M";
    //     podContainerResources.cpu = "500m";
    //     podContainer.resources.put(RESOURCES_LIMITS, podContainerResources);
    //
    //     ContainerDescription containerDescription = new ContainerDescription();
    //
    //     setPodContainerResourcesToContainerDescriptionResources(podContainer, containerDescription);
    //
    //     Long expectedMemoryLimit = 100000000L;
    //     Long actualMemoryLimit = containerDescription.memoryLimit;
    //
    //     Integer expectedCpuShares = 1;
    //     Integer actualCpuShares = containerDescription.cpuShares;
    //
    //     assertEquals(expectedMemoryLimit, actualMemoryLimit);
    //     assertEquals(expectedCpuShares, actualCpuShares);
    // }
    //
    // @Test
    // public void testSetContainerDescriptionResourcesToPodContainerResources() {
    //     ContainerDescription description = new ContainerDescription();
    //     description.memoryLimit = 100000L;
    //     description.cpuShares = 3;
    //
    //     PodContainer podContainer = new PodContainer();
    //
    //     setContainerDescriptionResourcesToPodContainerResources(description, podContainer);
    //
    //     String expectedPodContainerMemoryLimit = "100000";
    //     String expectedPodContainerCpuShares = "3";
    //
    //     String actualPodContainerMemoryLimit = podContainer.resources.get(RESOURCES_LIMITS).memory;
    //     String actualPodContainerCpuShares = podContainer.resources.get(RESOURCES_LIMITS).cpu;
    //
    //     assertEquals(expectedPodContainerMemoryLimit, actualPodContainerMemoryLimit);
    //     assertEquals(expectedPodContainerCpuShares, actualPodContainerCpuShares);
    // }
    //
    // @Test(expected = IllegalArgumentException.class)
    // public void testFromCompositeProtocolToKubernetesProtocol() {
    //     String expectedProtocol = "TCP";
    //     String actualProtocol = fromCompositeProtocolToKubernetesProtocol("tcp");
    //     assertEquals(expectedProtocol, actualProtocol);
    //
    //     expectedProtocol = "UDP";
    //     actualProtocol = fromCompositeProtocolToKubernetesProtocol("udp");
    //     assertEquals(expectedProtocol, actualProtocol);
    //
    //     expectedProtocol = null;
    //     actualProtocol = fromCompositeProtocolToKubernetesProtocol(null);
    //     assertEquals(expectedProtocol, actualProtocol);
    //
    //     fromCompositeProtocolToKubernetesProtocol("invalid");
    // }
    //
    // @Test
    // public void testFromContainerDescriptionHealthConfigToPodContainerProbe() {
    //     PodContainerProbe expectedProbe1 = new PodContainerProbe();
    //     expectedProbe1.exec = new PodContainerProbeExecAction();
    //     expectedProbe1.exec.command = new String[] { "test", "command" };
    //     expectedProbe1.timeoutSeconds = 1L;
    //     expectedProbe1.failureThreshold = 3;
    //     expectedProbe1.successThreshold = 1;
    //
    //     PodContainerProbe expectedProbe2 = new PodContainerProbe();
    //     expectedProbe2.httpGet = new PodContainerProbeHTTPGetAction();
    //     expectedProbe2.httpGet.path = "/test";
    //     expectedProbe2.httpGet.port = "32000";
    //
    //     PodContainerProbe expectedProbe3 = new PodContainerProbe();
    //     expectedProbe3.tcpSocket = new PodContainerProbeTCPSocketAction();
    //     expectedProbe3.tcpSocket.port = "32000";
    //
    //     HealthConfig healthConfig1 = new HealthConfig();
    //     healthConfig1.protocol = RequestProtocol.COMMAND;
    //     healthConfig1.command = "test command";
    //     healthConfig1.timeoutMillis = 1000;
    //     healthConfig1.unhealthyThreshold = 3;
    //     healthConfig1.healthyThreshold = 1;
    //
    //     HealthConfig healthConfig2 = new HealthConfig();
    //     healthConfig2.protocol = RequestProtocol.HTTP;
    //     healthConfig2.urlPath = "/test";
    //     healthConfig2.port = 32000;
    //
    //     HealthConfig healthConfig3 = new HealthConfig();
    //     healthConfig3.protocol = RequestProtocol.TCP;
    //     healthConfig3.port = 32000;
    //
    //     PodContainerProbe actualProbe1 = fromContainerDescriptionHealthConfigToPodContainerProbe(
    //             healthConfig1);
    //
    //     PodContainerProbe actualProbe2 = fromContainerDescriptionHealthConfigToPodContainerProbe(
    //             healthConfig2);
    //
    //     PodContainerProbe actualProbe3 = fromContainerDescriptionHealthConfigToPodContainerProbe(
    //             healthConfig3);
    //
    //     assertNotNull(actualProbe1.exec);
    //     for (int i = 0; i < expectedProbe1.exec.command.length; i++) {
    //         assertEquals(expectedProbe1.exec.command[i], actualProbe1.exec.command[i]);
    //     }
    //     assertEquals(expectedProbe1.timeoutSeconds, actualProbe1.timeoutSeconds);
    //     assertEquals(expectedProbe1.failureThreshold, actualProbe1.failureThreshold);
    //     assertEquals(expectedProbe1.successThreshold, actualProbe1.successThreshold);
    //
    //     assertNotNull(actualProbe2.httpGet);
    //     assertEquals(expectedProbe2.httpGet.path, actualProbe2.httpGet.path);
    //     assertEquals(expectedProbe2.httpGet.port, actualProbe2.httpGet.port);
    //
    //     assertNotNull(actualProbe3.tcpSocket);
    //     assertEquals(expectedProbe3.tcpSocket.port, actualProbe3.tcpSocket.port);
    // }
    //
    // @Test
    // public void testFromPodContainerCommandToContainerDescriptionCommand() {
    //     assertNull(fromPodContainerCommandToContainerDescriptionCommand(null, null));
    //
    //     assertNull(fromPodContainerCommandToContainerDescriptionCommand(new String[] {},
    //             new String[] { "ps" }));
    //
    //     String[] podCommand = new String[] { "admiral", "rm" };
    //     String[] podCommandArgs = new String[] { "container1", "container2", "container3" };
    //
    //     String[] expectedContainerDescriptionCmd = new String[] { "admiral", "rm", "container1",
    //             "container2", "container3" };
    //     String[] actualContainerDescriptionCmd = fromPodContainerCommandToContainerDescriptionCommand(
    //             podCommand, podCommandArgs);
    //
    //     for (int i = 0; i < expectedContainerDescriptionCmd.length; i++) {
    //         assertEquals(expectedContainerDescriptionCmd[i], actualContainerDescriptionCmd[i]);
    //     }
    //
    //     String[] podCommand1 = new String[] { "admiral", "login" };
    //     String[] expectedContainerDescriptionCmd1 = new String[] { "admiral", "login" };
    //     String[] actualContainerDescriptionCmd1 = fromPodContainerCommandToContainerDescriptionCommand(
    //             podCommand1, null);
    //
    //     assertEquals(expectedContainerDescriptionCmd1.length,
    //             actualContainerDescriptionCmd1.length);
    //
    //     for (int i = 0; i < expectedContainerDescriptionCmd1.length; i++) {
    //         assertEquals(expectedContainerDescriptionCmd1[i], actualContainerDescriptionCmd1[i]);
    //     }
    //
    // }

    @Test
    public void testFromResourceStateToBaseKubernetesStateShouldFail() {
        Class containerClass = CompositeComponentRegistry.metaByStateLink("/resources/containers")
                .stateClass;

        String expectedExceptionMsg = "Class: " + ContainerState.class.getName() + " is not child"
                + " of BaseKubernetesState.";

        boolean shouldFail = true;

        try {
            fromResourceStateToBaseKubernetesState(containerClass);
        } catch (IllegalArgumentException iae) {
            assertEquals(expectedExceptionMsg, iae.getMessage());
            shouldFail = false;
        }

        assertEquals(false, shouldFail);
    }

    @Test
    public void testMapApplicationSuffix() throws IOException {
        String suffix = "generate-mcm-10";

        String serviceYaml = serviceYamlFormat.replaceAll("_sufix", "");
        String expetedMappedServiceYaml = serviceYamlFormat.replaceAll("_sufix", "-" + suffix);

        KubernetesDescription kd = new KubernetesDescription();
        kd.kubernetesEntity = serviceYaml;

        kd = KubernetesUtil.mapApplicationAffix(kd, suffix);

        assertEquals(expetedMappedServiceYaml, kd.kubernetesEntity);
    }

    @Test
    public void testSetApplicationLabelOnDeployment() throws IOException {
        KubernetesDescription kd = new KubernetesDescription();
        kd.kubernetesEntity = deploymentYaml;
        kd.type = DEPLOYMENT_TYPE;

        String testCompositeId = "123456";

        kd = KubernetesUtil.setApplicationLabel(kd, testCompositeId);

        Deployment deployment = kd.getKubernetesEntity(Deployment.class);

        assertNotNull(deployment);
        assertNotNull(deployment.metadata);
        assertNotNull(deployment.metadata.labels);
        assertEquals(testCompositeId, deployment.metadata.labels.get(KUBERNETES_LABEL_APP_ID));

        assertNotNull(deployment.spec);
        assertNotNull(deployment.spec.template);
        assertNotNull(deployment.spec.template.metadata);
        assertNotNull(deployment.spec.template.metadata.labels);
        assertEquals(testCompositeId,
                deployment.spec.template.metadata.labels.get(KUBERNETES_LABEL_APP_ID));
    }

    @Test
    public void testSetApplicationLabelOnInvalidDeployment() {
        KubernetesDescription kd = new KubernetesDescription();
        kd.kubernetesEntity = invalidDeploymentYaml;
        kd.type = DEPLOYMENT_TYPE;

        String testCompositeId = "123456";

        // Make sure the is no NPE
        kd = KubernetesUtil.setApplicationLabel(kd, testCompositeId);
    }

    @Test
    public void testSetApplicationLabelOnDeploymentWithoutSpec() throws IOException {
        KubernetesDescription kd = new KubernetesDescription();
        kd.kubernetesEntity = deploymentWithoutSpecYaml;
        kd.type = DEPLOYMENT_TYPE;

        String testCompositeId = "123456";

        // Make sure the is no NPE
        kd = KubernetesUtil.setApplicationLabel(kd, testCompositeId);

        Deployment deployment = kd.getKubernetesEntity(Deployment.class);

        assertNotNull(deployment);
        assertNotNull(deployment.metadata);
        assertNotNull(deployment.metadata.labels);
        assertEquals(testCompositeId, deployment.metadata.labels.get(KUBERNETES_LABEL_APP_ID));
    }

    @Test
    public void testGetStateTypeFromSelfLink() {
        String selfLink = "/resources/kubernetes-pods/376fdq673";
        Class expectedClass = PodState.class;
        Class actualClass = getStateTypeFromSelfLink(selfLink);
        assertEquals(expectedClass, actualClass);
    }

    @Test
    public void testGetStateTypeFromSelfLinkShouldFail() {
        String selfLink = "/resources/containers/376fdq673";

        String expectedExceptionMsg = "Class: " + ContainerState.class.getName() + " is not child"
                + " of BaseKubernetesState.";

        boolean shouldFail = true;

        try {
            getStateTypeFromSelfLink(selfLink);
        } catch (IllegalArgumentException iae) {
            assertEquals(expectedExceptionMsg, iae.getMessage());
            shouldFail = false;
        }

        assertEquals(false, shouldFail);
    }

    @Test
    public void testSetApplicationLabelOnService() throws IOException {
        KubernetesDescription kd = new KubernetesDescription();
        kd.kubernetesEntity = serviceYamlFormat;
        kd.type = SERVICE_TYPE;

        String testCompositeId = "123456";

        kd = KubernetesUtil.setApplicationLabel(kd, testCompositeId);

        Service service = kd.getKubernetesEntity(Service.class);

        assertNotNull(service);
        assertNotNull(service.metadata);
        assertNotNull(service.metadata.labels);
        assertEquals(testCompositeId, service.metadata.labels.get(KUBERNETES_LABEL_APP_ID));
    }
}
