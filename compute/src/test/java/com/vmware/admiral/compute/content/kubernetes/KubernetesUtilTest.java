/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
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
import static com.vmware.admiral.common.util.FileUtil.switchToUnixLineEnds;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.deserializeCompositeTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtilTest.getContent;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromCompositeProtocolToKubernetesProtocol;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromContainerDescriptionHealthConfigToPodContainerProbe;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromPodContainerCommandToContainerDescriptionCommand;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromPodContainerProbeToContainerDescriptionHealthConfig;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.parsePodContainerCpuShares;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.parsePodContainerMemoryLimit;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.setContainerDescriptionResourcesToPodContainerResources;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.setPodContainerResourcesToContainerDescriptionResources;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.DEPLOYMENT_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.KUBERNETES_LABEL_APP_ID;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.SERVICE_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.fromCompositeTemplateToKubernetesTemplate;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.fromResourceStateToBaseKubernetesState;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.getStateTypeFromSelfLink;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.serializeKubernetesEntity;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.serializeKubernetesTemplate;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.HttpVersion;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.RequestProtocol;
import com.vmware.admiral.compute.content.CompositeTemplate;
import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.admiral.compute.kubernetes.entities.common.ResourceRequirements;
import com.vmware.admiral.compute.kubernetes.entities.config.KubeConfig;
import com.vmware.admiral.compute.kubernetes.entities.deployments.Deployment;
import com.vmware.admiral.compute.kubernetes.entities.pods.Container;
import com.vmware.admiral.compute.kubernetes.entities.pods.ExecAction;
import com.vmware.admiral.compute.kubernetes.entities.pods.HTTPGetAction;
import com.vmware.admiral.compute.kubernetes.entities.pods.Pod;
import com.vmware.admiral.compute.kubernetes.entities.pods.PodTemplate;
import com.vmware.admiral.compute.kubernetes.entities.pods.Probe;
import com.vmware.admiral.compute.kubernetes.entities.pods.TCPSocketAction;
import com.vmware.admiral.compute.kubernetes.entities.replicaset.ReplicaSet;
import com.vmware.admiral.compute.kubernetes.entities.replicationcontrollers.ReplicationController;
import com.vmware.admiral.compute.kubernetes.entities.services.Service;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService.DeploymentState;
import com.vmware.admiral.compute.kubernetes.service.GenericKubernetesEntityService.GenericKubernetesEntityState;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.service.PodFactoryService;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.compute.kubernetes.service.ReplicaSetService.ReplicaSetState;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService.ReplicationControllerState;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler.ServiceState;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

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

    private static final String podYaml = "apiVersion: v1\n"
            + "kind: Pod\n"
            + "metadata:\n"
            + "  name: some-pod\n"
            + "spec:\n"
            + "  containers:\n"
            + "  - name: some-test-pod\n"
            + "    image: alpine\n";

    private static final String podTemplateYaml = "apiVersion: v1\n"
            + "kind: PodTemplate\n"
            + "metadata:\n"
            + "  name: pod-template-test\n"
            + "template:\n"
            + "  metadata:\n"
            + "    name: some-test-pod-from-template\n"
            + "  spec:\n"
            + "    containers:\n"
            + "    - name: some-test-alpine\n"
            + "      image: alpine\n";

    private static final String replicationControllerYaml = "apiVersion: v1\n"
            + "kind: ReplicationController\n"
            + "metadata:\n"
            + "  name: replication-controller-test\n"
            + "spec:\n"
            + "  replicas: 2\n"
            + "  selector:\n"
            + "    app: replication-controller-test\n"
            + "  template:\n"
            + "    metadata:\n"
            + "      name: replication-controller-test\n"
            + "      labels:\n"
            + "        app: replication-controller-test\n"
            + "    spec:\n"
            + "      containers:\n"
            + "      - name: nginx\n"
            + "        image: nginx\n"
            + "        ports:\n"
            + "        - containerPort: 80\n";

    private static final String replicaSetYaml = "apiVersion: apps/v1\n"
            + "kind: ReplicaSet\n"
            + "metadata:\n"
            + "  name: replicaset-test\n"
            + "spec:\n"
            + "  replicas: 2\n"
            + "  selector:\n"
            + "    matchLabels:\n"
            + "      app: replicaset-test\n"
            + "  template:\n"
            + "    metadata:\n"
            + "      labels:\n"
            + "        app: replicaset-test\n"
            + "    spec:\n"
            + "      containers:\n"
            + "      - name: some-alpine\n"
            + "        image: alpine\n";

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

    private static final String secretYaml = "apiVersion: v1\n" +
            "kind: Secret\n" +
            "metadata:\n" +
            "  name: some-secret\n" +
            "type: Opaque\n" +
            "data:\n" +
            "  key: value\n";

    @Before
    public void beforeForKubernetesUtilTest() throws Throwable {
        HostInitComputeServicesConfig.initCompositeComponentRegistry();
    }

    @Test
    public void testDeserializeKubernetesEntityHasCorrectClass() throws IOException {
        assertEquals(Pod.class,
                KubernetesUtil.deserializeKubernetesEntity(podYaml).getClass());
        assertEquals(PodTemplate.class,
                KubernetesUtil.deserializeKubernetesEntity(podTemplateYaml).getClass());
        assertEquals(ReplicationController.class,
                KubernetesUtil.deserializeKubernetesEntity(replicationControllerYaml).getClass());
        assertEquals(Deployment.class,
                KubernetesUtil.deserializeKubernetesEntity(deploymentYaml).getClass());
        assertEquals(Service.class,
                KubernetesUtil.deserializeKubernetesEntity(serviceYamlFormat).getClass());
        assertEquals(BaseKubernetesObject.class,
                KubernetesUtil.deserializeKubernetesEntity(secretYaml).getClass());
    }

    @Test
    public void testCreateKubernetesEntityStateCreatesInstancessOfCorrectClass() {
        assertEquals(PodState.class,
                KubernetesUtil.createKubernetesEntityState(KubernetesUtil.POD_TYPE).getClass());
        assertEquals(ServiceState.class,
                KubernetesUtil.createKubernetesEntityState(KubernetesUtil.SERVICE_TYPE).getClass());
        assertEquals(DeploymentState.class, KubernetesUtil
                .createKubernetesEntityState(KubernetesUtil.DEPLOYMENT_TYPE).getClass());
        assertEquals(ReplicationControllerState.class,
                KubernetesUtil
                        .createKubernetesEntityState(KubernetesUtil.REPLICATION_CONTROLLER_TYPE)
                        .getClass());
        assertEquals(ReplicaSetState.class, KubernetesUtil
                .createKubernetesEntityState(KubernetesUtil.REPLICA_SET_TYPE).getClass());
        assertEquals(GenericKubernetesEntityState.class,
                KubernetesUtil.createKubernetesEntityState("any-other-kind").getClass());
    }

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
        Container podContainer = new Container();
        podContainer.livenessProbe = new Probe();
        podContainer.livenessProbe.tcpSocket = new TCPSocketAction();
        podContainer.livenessProbe.tcpSocket.port = "8080";
        podContainer.livenessProbe.timeoutSeconds = 3;

        HealthConfig expectedHealthConfig = new HealthConfig();
        expectedHealthConfig.protocol = RequestProtocol.TCP;
        expectedHealthConfig.port = 8080;
        expectedHealthConfig.timeoutMillis = 3000;

        HealthConfig actualHealthConfig = fromPodContainerProbeToContainerDescriptionHealthConfig(
                podContainer);

        assertNotNull(actualHealthConfig);
        assertEquals(actualHealthConfig.protocol, expectedHealthConfig.protocol);
        assertEquals(actualHealthConfig.port, expectedHealthConfig.port);
        assertEquals(actualHealthConfig.timeoutMillis, expectedHealthConfig.timeoutMillis);
    }

    @Test
    public void testConvertPodContainerProbeToHealthConfigHTTP() {
        Container podContainer = new Container();
        podContainer.livenessProbe = new Probe();
        podContainer.livenessProbe.httpGet = new HTTPGetAction();
        podContainer.livenessProbe.httpGet.port = "8080";
        podContainer.livenessProbe.httpGet.path = "/health";
        podContainer.livenessProbe.timeoutSeconds = 3;

        HealthConfig expectedHealthConfig = new HealthConfig();
        expectedHealthConfig.protocol = RequestProtocol.HTTP;
        expectedHealthConfig.httpVersion = HttpVersion.HTTP_v1_1;
        expectedHealthConfig.httpMethod = Action.GET;
        expectedHealthConfig.urlPath = "/health";
        expectedHealthConfig.port = 8080;
        expectedHealthConfig.timeoutMillis = 3000;

        HealthConfig actualHealthConfig = fromPodContainerProbeToContainerDescriptionHealthConfig(
                podContainer);

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
        Container podContainer = new Container();
        podContainer.resources = new ResourceRequirements();
        podContainer.resources.limits = new HashMap<>();
        podContainer.resources.limits.put("memory", "100M");
        podContainer.resources.limits.put("cpu", "500m");

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

        Container podContainer = new Container();

        setContainerDescriptionResourcesToPodContainerResources(description, podContainer);

        String expectedPodContainerMemoryLimit = "100000";
        String expectedPodContainerCpuShares = "3";

        String actualPodContainerMemoryLimit = (String) podContainer.resources.limits.get("memory");
        String actualPodContainerCpuShares = (String) podContainer.resources.limits.get("cpu");

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
        Probe expectedProbe1 = new Probe();
        expectedProbe1.exec = new ExecAction();
        expectedProbe1.exec.command = new String[] { "test", "command" };
        expectedProbe1.timeoutSeconds = 1;
        expectedProbe1.failureThreshold = 3;
        expectedProbe1.successThreshold = 1;

        Probe expectedProbe2 = new Probe();
        expectedProbe2.httpGet = new HTTPGetAction();
        expectedProbe2.httpGet.path = "/test";
        expectedProbe2.httpGet.port = "32000";

        Probe expectedProbe3 = new Probe();
        expectedProbe3.tcpSocket = new TCPSocketAction();
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

        Probe actualProbe1 = fromContainerDescriptionHealthConfigToPodContainerProbe(
                healthConfig1);

        Probe actualProbe2 = fromContainerDescriptionHealthConfigToPodContainerProbe(
                healthConfig2);

        Probe actualProbe3 = fromContainerDescriptionHealthConfigToPodContainerProbe(
                healthConfig3);

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

        assertNull(fromPodContainerCommandToContainerDescriptionCommand(new ArrayList<>(),
                Collections.singletonList("ps")));

        List<String> podCommand = Arrays.asList("admiral", "rm");
        List<String> podCommandArgs = Arrays.asList("container1", "container2", "container3");

        List<String> expectedContainerDescriptionCmd = Arrays.asList("admiral", "rm", "container1",
                "container2", "container3");
        List<String> actualContainerDescriptionCmd = fromPodContainerCommandToContainerDescriptionCommand(
                podCommand, podCommandArgs);

        for (int i = 0; i < expectedContainerDescriptionCmd.size(); i++) {
            assertEquals(expectedContainerDescriptionCmd.get(i), actualContainerDescriptionCmd
                    .get(i));
        }

        List<String> podCommand1 = Arrays.asList("admiral", "login");
        List<String> expectedContainerDescriptionCmd1 = Arrays.asList("admiral", "login");
        List<String> actualContainerDescriptionCmd1 = fromPodContainerCommandToContainerDescriptionCommand(
                podCommand1, null);

        assertEquals(expectedContainerDescriptionCmd1.size(),
                actualContainerDescriptionCmd1.size());

        for (int i = 0; i < expectedContainerDescriptionCmd1.size(); i++) {
            assertEquals(expectedContainerDescriptionCmd1.get(i),
                    actualContainerDescriptionCmd1.get(i));
        }

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

    @Test
    public void testSetApplicationLabelOnReplicationController() throws IOException {
        KubernetesDescription kd = new KubernetesDescription();
        kd.kubernetesEntity = replicationControllerYaml;
        kd.type = KubernetesUtil.REPLICATION_CONTROLLER_TYPE;

        String testCompositeId = "123456";

        kd = KubernetesUtil.setApplicationLabel(kd, testCompositeId);

        ReplicationController replicationController = kd
                .getKubernetesEntity(ReplicationController.class);

        assertNotNull(replicationController);
        assertNotNull(replicationController.metadata);
        assertNotNull(replicationController.metadata.labels);
        assertEquals(testCompositeId,
                replicationController.metadata.labels.get(KUBERNETES_LABEL_APP_ID));

        assertNotNull(replicationController.spec);
        assertNotNull(replicationController.spec.template);
        assertNotNull(replicationController.spec.template.metadata);
        assertNotNull(replicationController.spec.template.metadata.labels);
        assertEquals(testCompositeId,
                replicationController.spec.template.metadata.labels.get(KUBERNETES_LABEL_APP_ID));
    }

    @Test
    public void testSetApplicationLabelOnReplicaSet() throws IOException {
        KubernetesDescription kd = new KubernetesDescription();
        kd.kubernetesEntity = replicaSetYaml;
        kd.type = KubernetesUtil.REPLICA_SET_TYPE;

        String testCompositeId = "123456";

        kd = KubernetesUtil.setApplicationLabel(kd, testCompositeId);

        ReplicaSet replicaSet = kd.getKubernetesEntity(ReplicaSet.class);

        assertNotNull(replicaSet);
        assertNotNull(replicaSet.metadata);
        assertNotNull(replicaSet.metadata.labels);
        assertEquals(testCompositeId,
                replicaSet.metadata.labels.get(KUBERNETES_LABEL_APP_ID));

        assertNotNull(replicaSet.spec);
        assertNotNull(replicaSet.spec.template);
        assertNotNull(replicaSet.spec.template.metadata);
        assertNotNull(replicaSet.spec.template.metadata.labels);
        assertEquals(testCompositeId,
                replicaSet.spec.template.metadata.labels.get(KUBERNETES_LABEL_APP_ID));
    }

    @Test
    public void testGetStateTypeFromSelfLink() {
        String selfLink = "/resources/kubernetes-pods/376fdq673";
        Class<? extends BaseKubernetesState> expectedClass = PodState.class;
        Class<? extends BaseKubernetesState> actualClass = getStateTypeFromSelfLink(selfLink);
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
    public void testFromResourceStateToBaseKubernetesStateShouldFail() {
        Class<? extends ResourceState> containerClass = CompositeComponentRegistry
                .metaByStateLink("/resources/containers").stateClass;

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
    public void testCreateKubernetesEntityDescription() {
        final String testKey = "testKey";
        final String testValue = "testValue";

        PodState podState = new PodState();

        podState.descriptionLink = "/test/description-" + UUID.randomUUID().toString();
        podState.tenantLinks = Collections.singletonList("/tenants/test-tenant");
        podState.name = UUID.randomUUID().toString();
        podState.id = UUID.randomUUID().toString();
        podState.customProperties = new HashMap<>();
        podState.customProperties.put(testKey, testValue);

        KubernetesDescription podDescription = KubernetesUtil
                .createKubernetesEntityDescription(podState);
        assertThat(podDescription.documentSelfLink, is(podState.descriptionLink));
        assertThat(podDescription.name, is(podState.name));
        assertThat(podDescription.id, is(podState.id));
        assertThat(podDescription.type, is(podState.getType()));

        assertThat(podDescription.tenantLinks, is(podState.tenantLinks));

        assertThat(podDescription.customProperties, is(notNullValue()));
        assertThat(podDescription.customProperties.get(testKey), is(testValue));

        assertEquals(podState.name, podDescription.name);
    }

    @Test
    public void testParseBytes() {
        assertEquals(new Double(624.2), KubernetesUtil.parseBytes("624.2"));
        assertEquals(new Double(624), KubernetesUtil.parseBytes("624"));
        assertEquals(new Double(638976), KubernetesUtil.parseBytes("624Ki"));
        assertEquals(new Double(2.34881024E8), KubernetesUtil.parseBytes("224Mi"));
    }

    @Test
    public void testIsPKSManagedHost() {
        assertFalse(KubernetesUtil.isPKSManagedHost(null));
        assertFalse(KubernetesUtil.isPKSManagedHost(new ComputeState()));
        ComputeState noProperties = new ComputeState();
        noProperties.customProperties = new HashMap<>();
        assertFalse(KubernetesUtil.isPKSManagedHost(noProperties));
        ComputeState pksManagedHost = new ComputeState();
        pksManagedHost.customProperties = new HashMap<>();
        pksManagedHost.customProperties.put(PKS_ENDPOINT_PROP_NAME,
                "/resources/pks/endpoints/8d50dc9a46ed487556f736eb0c8f8");
        assertTrue(KubernetesUtil.isPKSManagedHost(pksManagedHost));
    }

    @Test
    public void testBuildLogUriPath() {
        String uuid = UUID.randomUUID().toString();
        String containerName = "pod1";

        BaseKubernetesState state = new PodState();
        state.documentSelfLink = PodFactoryService.SELF_LINK + "/" + uuid;
        String expectedPath = String.format("/logs/%s-%s", uuid, containerName);
        assertEquals(expectedPath, KubernetesUtil.buildLogUriPath(state, containerName));
    }

    @Test
    public void testConstructKubeConfigWithPublicKey() {
        String clusterAddress = "https://testhost:8443";

        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.publicKey = "certificate";
        creds.privateKey = "private_key";
        creds.type = AuthCredentialsType.PublicKey.toString();
        KubeConfig config = KubernetesUtil.constructKubeConfig(clusterAddress, creds);

        assertEquals("v1", config.apiVersion);
        assertEquals("Config", config.kind);
        assertNotNull(config.currentContext);
        assertNotNull(config.clusters);
        assertNotNull(config.contexts);
        assertNotNull(config.users);
        assertEquals(1, config.users.size());
        assertEquals(1, config.contexts.size());
        assertEquals(1, config.clusters.size());
        assertEquals(config.contexts.get(0).context.user, config.users.get(0).name);
        assertEquals("Y2VydGlmaWNhdGU=", config.users.get(0).user.clientCertificateData);
        assertEquals("cHJpdmF0ZV9rZXk=", config.users.get(0).user.clientKeyData);
        assertEquals(config.clusters.get(0).name, config.contexts.get(0).context.cluster);
        assertEquals(config.currentContext, config.contexts.get(0).name);
        assertEquals(clusterAddress, config.clusters.get(0).cluster.server);
        assertTrue(config.clusters.get(0).cluster.insecureSkipTlsVerify);
    }

    @Test
    public void testConstructKubeConfigWithBearerToken() {
        String clusterAddress = "https://testhost:8443";
        String token = "bearer_token";

        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = token;
        creds.type = AuthCredentialsType.Bearer.toString();
        KubeConfig config = KubernetesUtil.constructKubeConfig(clusterAddress, creds);

        assertNotNull(config);
        assertEquals(token, config.users.get(0).user.token);
    }

    @Test
    public void testConstructKubeConfigWithPassword() {
        String clusterAddress = "https://testhost:8443";
        String username = "user1";
        String password = "password123";

        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.userEmail = username;
        creds.privateKey = password;
        creds.type = AuthCredentialsType.Password.toString();
        KubeConfig config = KubernetesUtil.constructKubeConfig(clusterAddress, creds);

        assertNotNull(config);
        assertEquals(username, config.users.get(0).user.username);
        assertEquals(password, config.users.get(0).user.password);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testFailConstructKubeConfigWithUnsupportedCredentials() {

        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.type = AuthCredentialsType.PublicKeyCA.toString();
        KubernetesUtil.constructKubeConfig("https://localhost:6443", creds);
        fail("KubeConfig construction should have failed with unsupported credentials");
    }

    @Test
    public void testConstructDashboardLink() {
        ComputeState host = new ComputeState();
        host.address = "https://localhost:6443";

        assertEquals("Unexpected dashboard link",
                "https://localhost:6443/api/v1/namespaces/kube-system/services/https:kubernetes-dashboard:/proxy",
                KubernetesUtil.constructDashboardLink(host, null));
    }

    @Test
    public void testExtractTokenFromKubeConfig() {
        String token = "token";

        KubeConfig config = new KubeConfig();
        KubeConfig.UserEntry userEntry = new KubeConfig.UserEntry();
        userEntry.user = new KubeConfig.AuthInfo();

        assertNull("User token should be null", KubernetesUtil.extractTokenFromKubeConfig(config));

        userEntry.user.token = token;
        config.users = Arrays.asList(userEntry);

        assertEquals("Unexpected user token", token,
                KubernetesUtil.extractTokenFromKubeConfig(config));
    }
}
