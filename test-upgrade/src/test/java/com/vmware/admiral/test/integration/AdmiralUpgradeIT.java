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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.NodeHealthCheckService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AdmiralUpgradeIT extends BaseProvisioningOnCoreOsIT {
    private static final String TEMPLATE_FILE_BRANCH = "Admiral_0.9.1_release.yaml";
    private static final String TEMPLATE_FILE_MASTER = "Admiral_master.yaml";
    private static final String ADMIRAL_NAME = "admiral";

    private static ServiceClient serviceClient;

    private String compositeDescriptionLink;
    private String dockerHostSelfLink;
    private String credentialsSelfLink;
    private boolean dataInitialized;

    @BeforeClass
    public static void beforeClass() {
        serviceClient = ServiceClientFactory.createServiceClient(null);
    }

    @AfterClass
    public static void afterClass() {
        serviceClient.stop();
    }

    @Before
    public void setUp() throws Exception {
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE_BRANCH);
    }

    @Test
    public void testProvision() throws Exception {
        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API);
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE_MASTER);
        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API);
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return compositeDescriptionLink;
    }

    @Override
    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {
        assertEquals("Unexpected number of resource links", 1,
                request.resourceLinks.size());

        CompositeComponent cc = getDocument(request.resourceLinks.iterator().next(),
                CompositeComponent.class);

        String admiralContainerLink = cc.componentLinks.stream()
                .filter((l) -> l.contains(ADMIRAL_NAME))
                .collect(Collectors.toList()).get(0);
        waitForStatusCode(URI.create(getBaseUrl() + admiralContainerLink),
                Operation.STATUS_CODE_OK);
        ContainerState admiralContainer = getDocument(admiralContainerLink, ContainerState.class);
        if (!dataInitialized) {
            addContentToTheProvisionedAdmiral(admiralContainer);
        } else {
            validateContent(admiralContainer);
            removeData(admiralContainer);
        }
    }

    private void removeData(ContainerState admiralContainer) throws Exception {
        changeBaseURI(admiralContainer);
        delete(dockerHostSelfLink);
        delete(credentialsSelfLink);
        setBaseURI(null);
    }

    private void validateContent(ContainerState admiralContainer) throws Exception {
        changeBaseURI(admiralContainer);
        // wait for the admiral container to start
        URI uri = URI.create(getBaseUrl() + NodeHealthCheckService.SELF_LINK);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);
        uri = URI.create(getBaseUrl() + ManagementUriParts.CONTAINERS);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);
        uri = URI.create(getBaseUrl() + ManagementUriParts.CONTAINER_HOSTS);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);
        ComputeState dockerHost = getDocument(dockerHostSelfLink, ComputeState.class);
        Assert.assertTrue(dockerHost != null);
        AuthCredentialsServiceState credentials = getDocument(credentialsSelfLink,
                AuthCredentialsServiceState.class);
        Assert.assertTrue(credentials != null);
        setBaseURI(null);
    }

    private void changeBaseURI(ContainerState admiralContainer) throws Exception {
        String parent = admiralContainer.parentLink;
        ComputeState computeState = getDocument(parent, ComputeState.class);
        setBaseURI(String.format("http://%s:%s", computeState.address,
                admiralContainer.ports.get(0).hostPort));
    }

    private void addContentToTheProvisionedAdmiral(ContainerState admiralContainer)
            throws Exception {
        changeBaseURI(admiralContainer);
        // wait for the admiral container to start. In 0.9.1 health check service is not available
        Thread.sleep(20000);
        URI uri = URI.create(getBaseUrl() + ManagementUriParts.CONTAINERS);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);
        uri = URI.create(getBaseUrl() + ManagementUriParts.CONTAINER_HOSTS);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);

        setupCoreOsHost(DockerAdapterType.API, false);
        // create entities to check for after upgrade
        dockerHostSelfLink = getDockerHost().documentSelfLink;
        credentialsSelfLink = getDockerHostAuthCredentials().documentSelfLink;
        setBaseURI(null);
        dataInitialized = true;
    }
}
