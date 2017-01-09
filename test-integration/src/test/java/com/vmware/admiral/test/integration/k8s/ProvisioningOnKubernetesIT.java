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

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.admiral.test.integration.BaseIntegrationSupportIT;
import com.vmware.admiral.test.integration.data.IntegratonTestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Class for test that adds a k8s host
 */
public class ProvisioningOnKubernetesIT extends BaseIntegrationSupportIT {

    protected ComputeState kubernetesHostCompute;

    private AuthCredentialsServiceState kubernetesHostAuthCredentials;

    @Before
    public void Wait() throws InterruptedException {
        Thread.sleep(1000);
    }

    @After
    public void provisioningTearDown() throws Exception {
        // remove the host
        if (kubernetesHostCompute != null) {
            removeHost(kubernetesHostCompute);
        }
    }

    @Test
    @Ignore
    public void testAddKubernetesHost() throws Exception {
        logger.info("********************************************************************");
        logger.info("-------  Setup: Add CentOS VM as KubernetesHost ComputeState -------");
        logger.info("********************************************************************");
        logger.info(
                "---------- 1. Setup auth credentials for the CentOS VM (Container Host). "
                        + "--------");
        kubernetesHostAuthCredentials = IntegratonTestStateFactory.createAuthCredentials(true);
        kubernetesHostAuthCredentials.type = AuthCredentialsType.PublicKey.name();

        kubernetesHostAuthCredentials.privateKey = IntegratonTestStateFactory
                .getFileContent(getTestRequiredProp("kubernetes.client.key.file"));
        kubernetesHostAuthCredentials.publicKey = IntegratonTestStateFactory
                .getFileContent(getTestRequiredProp("kubernetes.client.cert.file"));

        assertNotEquals("", kubernetesHostAuthCredentials.publicKey);
        assertNotEquals("", kubernetesHostAuthCredentials.privateKey);

        kubernetesHostAuthCredentials = postDocument(AuthCredentialsService.FACTORY_LINK,
                kubernetesHostAuthCredentials);

        assertNotNull("Failed to create host credentials", kubernetesHostAuthCredentials);

        String credLink = kubernetesHostAuthCredentials.documentSelfLink;

        logger.info("---------- 2. Create Kubernetes Host ComputeState for CoreOS VM. --------");
        kubernetesHostCompute = createKubernetesHost(getTestRequiredProp("kubernetes.host.address"),  credLink);

        assertNotNull(kubernetesHostCompute);
    }

    @Test
    @Ignore
    public void testAddKubernetesHostWithInvalidNamespace() throws Exception {
        logger.info("********************************************************************");
        logger.info("-------  Setup: Add CentOS VM as KubernetesHost ComputeState -------");
        logger.info("********************************************************************");
        logger.info(
                "---------- 1. Setup auth credentials for the CentOS VM (Container Host). "
                        + "--------");
        kubernetesHostAuthCredentials = IntegratonTestStateFactory.createAuthCredentials(true);
        kubernetesHostAuthCredentials.type = AuthCredentialsType.PublicKey.name();

        kubernetesHostAuthCredentials.privateKey = IntegratonTestStateFactory
                .getFileContent(getTestRequiredProp("kubernetes.client.key.file"));
        kubernetesHostAuthCredentials.publicKey = IntegratonTestStateFactory
                .getFileContent(getTestRequiredProp("kubernetes.client.cert.file"));

        assertNotEquals("", kubernetesHostAuthCredentials.publicKey);
        assertNotEquals("", kubernetesHostAuthCredentials.privateKey);

        kubernetesHostAuthCredentials = postDocument(AuthCredentialsService.FACTORY_LINK,
                kubernetesHostAuthCredentials);

        assertNotNull("Failed to create host credentials", kubernetesHostAuthCredentials);

        String credLink = kubernetesHostAuthCredentials.documentSelfLink;

        logger.info("---------- 2. Create Kubernetes Host ComputeState for CoreOS VM. --------");

        ComputeState compute = IntegratonTestStateFactory.createKubernetesComputeHost();
        compute.address = getTestRequiredProp("kubernetes.host.address");
        compute.customProperties.put(KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME,
                "Invalid");

        // link credentials to host
        compute.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME, credLink);

        try {
            kubernetesHostCompute = addHost(compute);
        } catch (IllegalArgumentException ignored) {

        } catch (Throwable t) {
            assertTrue(false);
        }
        assertTrue(kubernetesHostCompute == null);
    }

    private static ComputeState createKubernetesHost(String address, String credLink) throws Exception {
        ComputeState compute = IntegratonTestStateFactory.createKubernetesComputeHost();
        compute.address = address;

        // link credentials to host
        compute.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME, credLink);
        return addHost(compute);
    }
}
