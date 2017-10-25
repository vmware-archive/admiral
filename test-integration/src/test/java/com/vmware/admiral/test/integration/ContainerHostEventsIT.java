/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.adapter.docker.service.DockerHostAdapterService;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.ConfigurationService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;

public class ContainerHostEventsIT extends BaseProvisioningOnCoreOsIT {
    private static final String TEMPLATE_FILE = "alpine.yaml";

    private String compositeDescriptionLink;

    @BeforeClass
    public static void beforeClass() throws Exception {
        serviceClient = ServiceClientFactory.createServiceClient(null);

        // enable host events subscription
        ConfigurationService.ConfigurationState config = new ConfigurationService.ConfigurationState();
        config.key = ConfigurationUtil.ALLOW_HOST_EVENTS_SUBSCRIPTIONS;
        config.value = "true";
        config.documentSelfLink = config.key;

        postDocument(ConfigurationService.ConfigurationFactoryService.SELF_LINK, config);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        serviceClient = ServiceClientFactory.createServiceClient(null);

        // disable host events subscription
        ConfigurationService.ConfigurationState config = new ConfigurationService.ConfigurationState();
        config.key = ConfigurationUtil.ALLOW_HOST_EVENTS_SUBSCRIPTIONS;
        config.value = "false";
        config.documentSelfLink = config.key;

        postDocument(ConfigurationService.ConfigurationFactoryService.SELF_LINK, config);
    }

    @Before
    public void setUp() throws Exception {
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE);
    }

    @After
    public void cleanUp() throws Exception {
        final long timoutInMillis = 20000; // 20sec
        long startTime = System.currentTimeMillis();

        // currently there is no way for notifying when the host is subscribed/unsubscribed for events
        unsubscribeHostForEvents(dockerHostCompute);

        waitFor(t -> {
            return System.currentTimeMillis() - startTime > timoutInMillis;
        });
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage, RegistryType registryType)
            throws Exception {

        return compositeDescriptionLink;
    }

    @Test
    public void testContainerDies() throws Exception {
        setupCoreOsHost(ContainerHostService.DockerAdapterType.API, false);

        RequestBrokerState rbState = requestContainer(compositeDescriptionLink);

        String compositeComponentLink = rbState.resourceLinks.iterator().next();
        CompositeComponent cc = getDocument(compositeComponentLink, CompositeComponent.class);
        String containerSelfLink = cc.componentLinks.iterator().next();
        ContainerState container = getDocument(containerSelfLink, ContainerState.class);

        assertNotNull(container);
        assertEquals(ContainerState.PowerState.RUNNING, container.powerState);

        final long timoutInMillis = 10000; // 10sec
        long startTime = System.currentTimeMillis();

        // the provisioned template contains "sleep" command which will make the container to exit
        // and based on the event, the power state will be changed to STOPPED
        waitFor(t -> {
            if (System.currentTimeMillis() - startTime > timoutInMillis) {
                fail(String.format("After %s the container is still not in %s state.", timoutInMillis, ContainerState.PowerState.STOPPED));
                return true;
            }

            try {
                ContainerState containerState = getDocument(containerSelfLink, ContainerState.class);
                return ContainerState.PowerState.STOPPED.equals(containerState.powerState);
            } catch (Exception e) {
                fail(String.format("Unable to retrieve container: %s", e.getMessage()));
                return true;
            }
        });
    }

    private void unsubscribeHostForEvents(ComputeState cs) throws InterruptedException, ExecutionException, TimeoutException {
        URI resourceReferenceUri = URI
                .create(getBaseUrl() + buildServiceUri(cs.documentSelfLink));

        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = ContainerHostOperationType.EVENTS_UNSUBSCRIBE.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = resourceReferenceUri;
        URI adapterUri = URI
                .create(getBaseUrl() + buildServiceUri(DockerHostAdapterService.SELF_LINK));
        sendRequest(serviceClient, Operation.createPatch(adapterUri)
                .setBodyNoCloning(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to subscribe for host events: %s", ex.getMessage());
                        return;
                    }
                }));
    }
}