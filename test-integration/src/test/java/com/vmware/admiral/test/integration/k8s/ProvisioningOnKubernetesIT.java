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

package com.vmware.admiral.test.integration.k8s;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import static com.vmware.admiral.compute.ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME;
import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.vmware.admiral.common.KubernetesHostConstants;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.test.integration.BaseIntegrationSupportIT;
import com.vmware.admiral.test.integration.SimpleHttpsClient;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpResponse;
import com.vmware.admiral.test.integration.data.IntegratonTestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Class for test that adds a k8s host
 */
@RunWith(Parameterized.class)
public class ProvisioningOnKubernetesIT extends BaseIntegrationSupportIT {

    protected ClusterDto kubernetesCluster;

    private AuthCredentialsServiceState kubernetesAuthCredentials;
    private ResourcePoolState placementZone;
    private final AuthCredentialsType credentialsTypeToUse;

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        // "PublicKey" should be supported as well, but the caching in the remote service client, disallows adding the same host twice with different authentication type.
        return Arrays.asList(new Object[][] {
                { "PublicKey" }
        });
    }

    public ProvisioningOnKubernetesIT(String credentialsType) {
        credentialsTypeToUse = AuthCredentialsType.valueOf(credentialsType);
    }

    @After
    public void provisioningTearDown() throws Exception {
        // remove the host
        if (kubernetesCluster != null) {
            removeCluster(kubernetesCluster);
        }

        if (kubernetesAuthCredentials != null) {
            delete(kubernetesAuthCredentials);
        }

        if (placementZone != null) {
            delete(placementZone);
        }
    }

    @Test
    public void testAddKubernetesHost() throws Throwable {
        logger.info("********************************************************************");
        logger.info("-------  Setup: Add CentOS VM as KubernetesHost ComputeState -------");
        logger.info("********************************************************************");
        logger.info("---------- 1. Setup auth credentials for K8S. " + "--------");
        kubernetesAuthCredentials = createAuthCredentials(credentialsTypeToUse);

        assertNotNull("Failed to create host credentials", kubernetesAuthCredentials);

        logger.info("---------- 2. Create Kubernetes Cluster. --------");
        kubernetesCluster = createKubernetesCluster(
                getTestRequiredProp("kubernetes.host.address"),
                kubernetesAuthCredentials.documentSelfLink, null);

        assertNotNull(kubernetesCluster);
    }

    @Test
    public void testAddKubernetesHostWithInvalidNamespace() throws Exception {
        logger.info("********************************************************************");
        logger.info("-------  Setup: Add CentOS VM as KubernetesHost ComputeState -------");
        logger.info("********************************************************************");

        logger.info("---------- 1. Setup auth credentials for K8S. " + "--------");

        kubernetesAuthCredentials = createAuthCredentials(credentialsTypeToUse);
        assertNotNull("Failed to create host credentials", kubernetesAuthCredentials);

        logger.info("---------- 2. Create Kubernetes Host ComputeState for K8S. --------");

        Map<String, String> invalidProps = Collections.singletonMap(
                KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME, "Invalid");

        try {
            kubernetesCluster = createKubernetesCluster(
                    getTestRequiredProp("kubernetes.host.address"),
                    kubernetesAuthCredentials.documentSelfLink,
                    invalidProps);
            fail("Add cluster with invalid namespace was supposed to fail");
        } catch (IllegalArgumentException e) {
        }
        assertNull(kubernetesCluster);
    }

    private static AuthCredentialsServiceState createAuthCredentials(
            AuthCredentialsType credentialsTypeToUse) throws Exception {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.type = credentialsTypeToUse.name();
        if (AuthCredentialsType.Password.equals(credentialsTypeToUse)) {
            authCredentials.userEmail = getTestRequiredProp("kubernetes.host.user");
            authCredentials.privateKey = getTestRequiredProp("kubernetes.host.password");
        } else if (AuthCredentialsType.PublicKey.equals(credentialsTypeToUse)) {
            authCredentials.privateKey = IntegratonTestStateFactory
                    .getFileContent(getTestRequiredProp("kubernetes.client.key.file"));
            authCredentials.publicKey = IntegratonTestStateFactory
                    .getFileContent(getTestRequiredProp("kubernetes.client.cert.file"));
        } else {
            throw new IllegalArgumentException(
                    "Unsupported credentials type: " + credentialsTypeToUse);
        }
        return postDocument(AuthCredentialsService.FACTORY_LINK, authCredentials);
    }

    private static ClusterDto createKubernetesCluster(String address, String credLink,
            Map<String, String> customProperties) throws Exception {

        ComputeState compute = new ComputeState();
        compute.address = address;

        compute.customProperties = new HashMap<>();
        if (customProperties != null) {
            compute.customProperties.putAll(customProperties);
        }
        compute.customProperties.put(HOST_AUTH_CREDENTIALS_PROP_NAME, credLink);
        compute.customProperties.put(CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.KUBERNETES.name());
        compute.customProperties.put(HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());

        return addCluster(compute);
    }

    private static ClusterDto addCluster(ComputeState compute) throws Exception {
        ContainerHostSpec hostSpec = new ContainerHostSpec();
        hostSpec.acceptCertificate = true;
        hostSpec.hostState = compute;

        Map<String, String> headers = Collections.singletonMap(OperationUtil.PROJECT_ADMIRAL_HEADER,
                "/projects/test");
        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.POST,
                getBaseUrl() + buildServiceUri(ClusterService.SELF_LINK),
                Utils.toJson(hostSpec), headers, null);

        if (HttpURLConnection.HTTP_OK != httpResponse.statusCode) {
            throw new IllegalArgumentException("Add cluster failed with status code: "
                    + httpResponse.statusCode);
        }


        return Utils.fromJson(httpResponse.responseBody, ClusterDto.class);
    }

    private void removeCluster(ClusterDto cluster) throws Exception {
        logger.info("---------- Request remove cluster. --------");
        if (cluster != null) {
            String body = sendRequest(HttpMethod.GET, cluster.documentSelfLink, null);
            if (body != null && !body.isEmpty()) {
                // cluster is found, remove it
                delete(cluster);
            } else {
                logger.info("Cluster not found. Skipping removal");
            }
        }
    }
}
