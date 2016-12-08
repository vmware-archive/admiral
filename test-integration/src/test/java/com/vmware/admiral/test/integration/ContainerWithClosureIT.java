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
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.RequestBrokerService;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.TaskState;

public class ContainerWithClosureIT extends BaseProvisioningOnCoreOsIT {

    private static final String TEMPLATE_FILE = "Container_with_closure.yaml";
    private static final String CONTAINER_NAME_MASK = "kitematic";

    private static final String INPUT_NAME = "input_a";
    private static final String INPUT_VALUE = "value_a";

    private static ServiceClient serviceClient;

    private String compositeDescriptionLink;

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
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE);
    }

    @Test
    public void testProvision() throws Exception {
        doProvisionDockerContainerOnCoreOS(false, ContainerHostService.DockerAdapterType.API);
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return compositeDescriptionLink;
    }

    @Override
    protected void validateAfterStart(String resourceDescLink,
            RequestBrokerService.RequestBrokerState request)
            throws Exception {
        int expectedNumberOfResources = 3;
        int expectedClosureResult = 110;
        assertEquals("Unexpected number of resource links", 1, request.resourceLinks.size());

        String resourceLink = request.resourceLinks.iterator().next();
        CompositeComponent cc = getDocument(resourceLink, CompositeComponent.class);
        assertEquals("Unexpected number of component links", expectedNumberOfResources,
                cc.componentLinks.size());

        logger.info("------------- 1. Retrieving container states... -------------");
        List<ContainerState> provisionedContainers = fetchProvisionedResources(cc.componentLinks,
                CONTAINER_NAME_MASK);
        assertEquals("Unexpected number of container links", 2, provisionedContainers.size());

        ContainerState beforeClosureContainer = findProvisionedResource(provisionedContainers,
                "kitematicBeforeClosure");

        assertEquals(INPUT_NAME + "=" + INPUT_VALUE, beforeClosureContainer.env[0]);

        String closureLink = getResourceContaining(cc.componentLinks, "closures");
        assertNotNull(closureLink);

        logger.info("------------- 2. Retrieving closure state for %s. -------------", closureLink);
        Closure closureState = getDocument(closureLink, Closure.class);
        assertEquals(TaskState.TaskStage.FINISHED, closureState.state);
        assertEquals(expectedClosureResult, closureState.outputs.get("resultInt").getAsInt());
        assertEquals(expectedClosureResult, closureState.outputs.get("resultObj").getAsJsonObject().get("a").getAsInt
                ());

        ContainerState afterClosureContainer = findProvisionedResource(provisionedContainers,
                "kitematicAfterClosure");
        assertEquals("input_obj={\"a\":" + expectedClosureResult + "}", afterClosureContainer.env[0]);
        assertEquals(expectedClosureResult, Integer.parseInt(afterClosureContainer.customProperties.get("input_int")));

        waitForClosureContainerCleanUp(
                closureState.resourceLinks.iterator().next(),
                r -> r.statusCode == 404);
    }

    private ContainerState findProvisionedResource(List<ContainerState> provisionedResources,
            String name) {
        return provisionedResources.stream().filter(c -> c.names.get(0).contains(name)).findFirst()
                .get();
    }

    private List<ContainerState> fetchProvisionedResources(List<String> componentLinks,
            String containerName) {
        List<ContainerState> fetchedResources = new ArrayList<>();
        componentLinks.stream().filter(l -> l.contains(containerName)).forEach(s -> {
            try {
                logger.info("------- Retrieving container state for %s -------", s);
                fetchedResources.add(getDocument(s, ContainerState.class));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return fetchedResources;
    }

    protected static void waitForClosureContainerCleanUp(final String documentSelfLink,
            Predicate<SimpleHttpsClient.HttpResponse> predicate) throws Exception {

        String body = null;
        for (int i = 0; i < STATE_CHANGE_WAIT_POLLING_RETRY_COUNT; i++) {
            SimpleHttpsClient.HttpResponse response = SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.GET,
                    getBaseUrl() + buildServiceUri(documentSelfLink), null);
            if (predicate.test(response)) {
                return;
            }
            Thread.sleep(STATE_CHANGE_WAIT_POLLING_PERIOD_MILLIS);
        }

        throw new RuntimeException(String.format("Failed waiting for closure container clean-up!"));
    }
}
