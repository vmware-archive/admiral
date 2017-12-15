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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.adapter.docker.service.DockerHostAdapterService;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.ConfigurationService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;

public class ContainerHostEventsIT extends BaseProvisioningOnCoreOsIT {
    private static final String TEMPLATE_FILE_WITH_SLEEP_COMMAND = "alpine_with_sleep_command.yaml";
    private static final String TEMPLATE_FILE = "alpine.yaml";

    private String compositeDescriptionLink;

    @BeforeClass
    public static void beforeClass() throws Throwable {
        serviceClient = ServiceClientFactory.createServiceClient(null);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        serviceClient = ServiceClientFactory.createServiceClient(null);
    }

    @Before
    public void setUp() throws Exception {
        DeploymentProfileConfig.getInstance().setTest(true);

        // enable host events subscription
        ConfigurationService.ConfigurationState config = new ConfigurationService.ConfigurationState();
        config.key = ConfigurationUtil.ALLOW_HOST_EVENTS_SUBSCRIPTIONS;
        config.value = Boolean.TRUE.toString();
        config.documentSelfLink = config.key;

        config = postDocument(ConfigurationService.ConfigurationFactoryService.SELF_LINK, config);
        assertEquals(Boolean.TRUE.toString(), config.value);

        // set throw IO exception in order to simulate this kind of exception
        ConfigurationService.ConfigurationState configState = new ConfigurationService.ConfigurationState();
        configState.key = ConfigurationUtil.THROW_IO_EXCEPTION;
        configState.value = Boolean.TRUE.toString();
        configState.documentSelfLink = configState.key;
        configState = postDocument(ConfigurationService.ConfigurationFactoryService.SELF_LINK, configState);
        assertEquals(Boolean.TRUE.toString(), configState.value);
    }

    @After
    public void cleanUp() throws Exception {
        DeploymentProfileConfig.getInstance().setTest(false);

        final long timoutInMillis = 20000; // 20sec
        long startTime = System.currentTimeMillis();

        // currently there is no way for notifying when the host is subscribed/unsubscribed for events
        unsubscribeHostForEvents(dockerHostCompute);

        waitFor(t -> {
            logger.info("Waiting for host unsubscription %s",
                    TimeUnit.MILLISECONDS.toSeconds(timoutInMillis - (System.currentTimeMillis() - startTime)));
            return System.currentTimeMillis() - startTime > timoutInMillis;
        });

        // disable host events subscription
        ConfigurationService.ConfigurationState config = new ConfigurationService.ConfigurationState();
        config.key = ConfigurationUtil.ALLOW_HOST_EVENTS_SUBSCRIPTIONS;
        config.value = Boolean.FALSE.toString();
        config.documentSelfLink = config.key;

        config = postDocument(ConfigurationService.ConfigurationFactoryService.SELF_LINK, config);
        assertEquals(Boolean.FALSE.toString(), config.value);

        // disable the simulation of the IOException
        ConfigurationService.ConfigurationState configState = new ConfigurationService.ConfigurationState();
        configState.key = ConfigurationUtil.THROW_IO_EXCEPTION;
        configState.value = Boolean.FALSE.toString();
        configState.documentSelfLink = configState.key;
        configState = postDocument(ConfigurationService.ConfigurationFactoryService.SELF_LINK, configState);
        assertEquals(Boolean.FALSE.toString(), configState.value);
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage, RegistryType registryType)
            throws Exception {

        return compositeDescriptionLink;
    }

    @Test
    public void testContainerDies() throws Exception {
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE_WITH_SLEEP_COMMAND);

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

    @Test
    public void testHostDies() throws Exception {
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE);

        setupCoreOsHost(ContainerHostService.DockerAdapterType.API, false);

        ComputeState cs = getDockerHost();
        assertEquals(ComputeService.PowerState.ON, cs.powerState);

        // provision container
        RequestBrokerState rbState = requestContainer(compositeDescriptionLink);

        String compositeComponentLink = rbState.resourceLinks.iterator().next();
        CompositeComponent cc = getDocument(compositeComponentLink, CompositeComponent.class);
        String containerSelfLink = cc.componentLinks.iterator().next();
        ContainerState container = getDocument(containerSelfLink, ContainerState.class);

        assertNotNull(container);
        assertEquals(ContainerState.PowerState.RUNNING, container.powerState);

        final long timoutInMillis = 25000; // 25sec
        long startTime = System.currentTimeMillis();

        // wait for compute to be in UNKNOWN state
        waitFor(t -> {
            if (System.currentTimeMillis() - startTime > timoutInMillis) {
                fail(String.format("After %s the compute is still not in %s state.", timoutInMillis, ComputeService.PowerState.UNKNOWN));
                return true;
            }

            try {
                ComputeState computeState = getDocument(cs.documentSelfLink, ComputeState.class);
                logger.info("Waiting for compute power state to be %s. Current state %s", ComputeService.PowerState.UNKNOWN, computeState.powerState);
                return ComputeService.PowerState.UNKNOWN.equals(computeState.powerState);
            } catch (Exception e) {
                fail(String.format("Unable to retrieve compute: %s", e.getMessage()));
                return true;
            }
        });

        long startTimeContainers = System.currentTimeMillis();

        // wait for containers to be in UNKNOWN state
        waitFor(t -> {
            if (System.currentTimeMillis() - startTimeContainers > timoutInMillis) {
                fail(String.format("After %s the container is still not in %s state.", timoutInMillis, ContainerState.PowerState.UNKNOWN));
                return true;
            }

            try {
                ContainerState containerState = getDocument(containerSelfLink, ContainerState.class);
                logger.info("Waiting for container power state to be %s. Current state is %s", ContainerState.PowerState.UNKNOWN, containerState.powerState);
                return ContainerState.PowerState.UNKNOWN.equals(containerState.powerState);
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