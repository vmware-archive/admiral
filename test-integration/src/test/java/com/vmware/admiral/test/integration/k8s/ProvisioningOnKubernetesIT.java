/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.PlacementZoneConstants;
import com.vmware.admiral.compute.PlacementZoneConstants.PlacementZoneType;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.test.integration.BaseIntegrationSupportIT;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.data.IntegratonTestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Class for test that adds a k8s host
 */
@Ignore("ignore for vRA 7.3.1")
@RunWith(Parameterized.class)
public class ProvisioningOnKubernetesIT extends BaseIntegrationSupportIT {

    protected ComputeState kubernetesHostCompute;

    private AuthCredentialsServiceState kubernetesHostAuthCredentials;
    private ResourcePoolState placementZone;
    private final AuthCredentialsType credentialsTypeToUse;

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        // "PublicKey" should be supported as well, but the caching in the remote service client, disallows adding the same host twice with different authentication type.
        return Arrays.asList(new Object[][] {
                { "Password" }
        });
    }

    public ProvisioningOnKubernetesIT(String credentialsType) {
        credentialsTypeToUse = AuthCredentialsType.valueOf(credentialsType);
    }

    @After
    public void provisioningTearDown() throws Exception {
        // remove the host
        if (kubernetesHostCompute != null) {
            removeHost(kubernetesHostCompute);
        }

        if (kubernetesHostAuthCredentials != null) {
            delete(kubernetesHostAuthCredentials);
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
        logger.info(
                "---------- 1. Setup auth credentials for K8S. " + "--------");
        kubernetesHostAuthCredentials = createAuthCredentials(credentialsTypeToUse);
        kubernetesHostAuthCredentials = postDocument(AuthCredentialsService.FACTORY_LINK,
                kubernetesHostAuthCredentials);

        assertNotNull("Failed to create host credentials", kubernetesHostAuthCredentials);

        String credLink = kubernetesHostAuthCredentials.documentSelfLink;

        assertTrue(listContainers().isEmpty());

        logger.info("---------- 2. Creating Placement Zone for K8S. --------");
        placementZone = createSchedulerPlacementZone();

        logger.info("---------- 3. Create Kubernetes Host ComputeState for K8S. --------");
        kubernetesHostCompute = createKubernetesHost(getTestRequiredProp("kubernetes.host.address"),
                credLink, placementZone.documentSelfLink);

        assertNotNull(kubernetesHostCompute);

        //        TODO: update with check for specific entities
        //        logger.info("---------- 4. Waiting for data collected container. --------");

        //        waitFor((v) -> {
        //            return !listContainers().isEmpty();
        //        });
        //
        //        assertFalse(listContainers().isEmpty());
        //
        //        waitFor((v) -> {
        //            ComputeState compute;
        //            try {
        //                compute = getDocument(kubernetesHostCompute.documentSelfLink, ComputeState.class);
        //                int containers = Integer.valueOf(compute.customProperties.get("__Containers"));
        //                return containers > 0;
        //            } catch (Exception e) {
        //                return false;
        //            }
        //        });
        //
        //        ComputeState compute = getDocument(kubernetesHostCompute.documentSelfLink,
        //                ComputeState.class);
        //        int containers = Integer.valueOf(compute.customProperties.get("__Containers"));
        //        assertTrue(containers > 0);
    }

    private List<ContainerState> listContainers() {
        String body;
        try {
            body = sendRequest(HttpMethod.GET, ManagementUriParts.CONTAINERS + "?expand", null);
        } catch (Exception e) {
            return Collections.emptyList();
        }
        ServiceDocumentQueryResult result = Utils.fromJson(body, ServiceDocumentQueryResult.class);

        if (result.documents != null) {
            return result.documents.values().stream()
                    .map((d) -> Utils.fromJson(d, ContainerState.class))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @Test
    public void testAddKubernetesHostWithInvalidNamespace() throws Exception {
        logger.info("********************************************************************");
        logger.info("-------  Setup: Add CentOS VM as KubernetesHost ComputeState -------");
        logger.info("********************************************************************");
        logger.info(
                "---------- 1. Setup auth credentials for K8S. " + "--------");
        kubernetesHostAuthCredentials = createAuthCredentials(credentialsTypeToUse);
        kubernetesHostAuthCredentials = postDocument(AuthCredentialsService.FACTORY_LINK,
                kubernetesHostAuthCredentials);

        assertNotNull("Failed to create host credentials", kubernetesHostAuthCredentials);

        String credLink = kubernetesHostAuthCredentials.documentSelfLink;

        logger.info("---------- 2. Create Kubernetes Host ComputeState for K8S. --------");

        ComputeState compute = IntegratonTestStateFactory.createKubernetesComputeHost();
        compute.address = getTestRequiredProp("kubernetes.host.address");
        compute.customProperties.put(KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME,
                "Invalid");

        // link credentials to host
        compute.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME, credLink);

        try {
            kubernetesHostCompute = addHost(compute);
            fail("Add host with invalid namespace was supposed to fail");
        } catch (Throwable t) {
        }
        assertNull(kubernetesHostCompute);
    }

    private static AuthCredentialsServiceState createAuthCredentials(
            AuthCredentialsType credentialsTypeToUse) {
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
        return authCredentials;
    }

    private static ComputeState createKubernetesHost(String address, String credLink,
            String placementZoneLink)
            throws Exception {
        ComputeState compute = IntegratonTestStateFactory.createKubernetesComputeHost();
        compute.address = address;
        compute.resourcePoolLink = placementZoneLink;

        // link credentials to host
        compute.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME, credLink);
        compute.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.KUBERNETES.name());
        return addHost(compute);
    }

    private ResourcePoolState createSchedulerPlacementZone() throws Throwable {

        ResourcePoolState placementZone = TestRequestStateFactory.createResourcePool();
        placementZone.id = "k8s-pzone";
        placementZone.name = placementZone.id;
        placementZone.documentSelfLink = ResourcePoolService.FACTORY_LINK + "/" + placementZone.id;

        placementZone.customProperties = new HashMap<>();
        placementZone.customProperties.put(
                PlacementZoneConstants.PLACEMENT_ZONE_TYPE_CUSTOM_PROP_NAME,
                PlacementZoneType.SCHEDULER.toString());

        return postDocument(ResourcePoolService.FACTORY_LINK, placementZone);
    }
}
