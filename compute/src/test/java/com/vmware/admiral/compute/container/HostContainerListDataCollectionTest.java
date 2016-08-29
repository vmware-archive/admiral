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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.ContainerListCallback;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionFactoryService;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionState;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class HostContainerListDataCollectionTest extends ComputeBaseTest {
    private static final String TEST_PREEXISTING_CONTAINER_ID = "preexisting-container";
    private static final String TEST_HOST_ID = "test-host-id-234:2376";
    private static final String COMPUTE_HOST_LINK = UriUtils.buildUriPath(
            ComputeService.FACTORY_LINK, TEST_HOST_ID);
    private ContainerListCallback containerListBody;
    private String systemContainerLink;
    private String image;

    @Before
    public void setUp() throws Throwable {
        host.startService(Operation.createPost(UriUtilsExtended.buildUri(host,
                MockDockerAdapterService.class)), new MockDockerAdapterService());
        host.startService(Operation.createPost(UriUtilsExtended.buildUri(host,
                MockDockerHostAdapterService.class)), new MockDockerHostAdapterService());

        waitForServiceAvailability(ContainerHostDataCollectionService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);

        waitForServiceAvailability(MockDockerAdapterService.SELF_LINK);
        waitForServiceAvailability(MockDockerHostAdapterService.SELF_LINK);
        waitForServiceAvailability(HostContainerListDataCollectionFactoryService.DEFAULT_HOST_CONAINER_LIST_DATA_COLLECTION_LINK);

        containerListBody = new ContainerListCallback();
        containerListBody.containerHostLink = COMPUTE_HOST_LINK;

        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc = doPost(computeDesc, ComputeDescriptionService.FACTORY_LINK);

        //System container creation is disabled during test.
        //Enable it for this concrete test.
        DeploymentProfileConfig.getInstance().setTest(false);

        ComputeState cs = new ComputeState();
        cs.id = TEST_HOST_ID;
        cs.documentSelfLink = TEST_HOST_ID;
        cs.descriptionLink = computeDesc.documentSelfLink;
        cs.customProperties = new HashMap<String, String>();

        doPost(cs, ComputeService.FACTORY_LINK);

        systemContainerLink = SystemContainerDescriptions.getSystemContainerSelfLink(
                SystemContainerDescriptions.AGENT_CONTAINER_NAME, TEST_HOST_ID);
        image = String.format("%s:%s", SystemContainerDescriptions.AGENT_IMAGE_NAME,
                SystemContainerDescriptions.AGENT_IMAGE_VERSION);
    }

    @After
    public void tearDown() throws Throwable {
        //System container creation is disabled during test.
        //Disabled it back after this test.
        DeploymentProfileConfig.getInstance().setTest(true);

        MockDockerAdapterService.resetContainers();
    }

    @Test
    public void testProvisionSystemContainerWhenDoesNotExistsOnHost() throws Throwable {
        // add preexisting container to the adapter service
        MockDockerAdapterService.addContainerId(TEST_HOST_ID, TEST_PREEXISTING_CONTAINER_ID,
                TEST_PREEXISTING_CONTAINER_ID);
        MockDockerAdapterService.addContainerNames(TEST_HOST_ID, TEST_PREEXISTING_CONTAINER_ID,
                "TestName");

        // run data collection on preexisting container
        startAndWaitHostContainerListDataCollection();

        ContainerState systemContainer = waitForContainer(systemContainerLink, image);
        assertNotNull("System container not created or can't be retrieved.", systemContainer);
        assertEquals(systemContainerLink, systemContainer.documentSelfLink);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_NAME,
                systemContainer.names.get(0));
        assertNotNull("System container not provisioned", systemContainer.id);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK,
                systemContainer.descriptionLink);
        assertEquals(Boolean.TRUE, systemContainer.system);
    }

    @Test
    public void testProvisionSystemContainerWhenExistsOnHost() throws Throwable {
        String systemContainerId = extractId(systemContainerLink);
        String systemContainerRef = UriUtils.buildUri(host, systemContainerLink).toString();

        // add system container to the adapter service because it already exists on host
        MockDockerAdapterService.addContainerId(TEST_HOST_ID, systemContainerId,
                systemContainerRef);
        MockDockerAdapterService.addContainerNames(TEST_HOST_ID, systemContainerId,
                SystemContainerDescriptions.AGENT_CONTAINER_NAME);
        MockDockerAdapterService.addContainerImage(TEST_HOST_ID, systemContainerId, image);

        // run data collection on preexisting system container
        startAndWaitHostContainerListDataCollection();

        ContainerState systemContainer = waitForContainer(systemContainerLink, image);
        assertNotNull("System container not created or can't be retrieved.", systemContainer);
        assertEquals(systemContainerLink, systemContainer.documentSelfLink);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_NAME,
                systemContainer.names.get(0));
        assertNotNull("System container not discovered", systemContainer.id);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK,
                systemContainer.descriptionLink);
        assertEquals(image, systemContainer.image);
        assertEquals(Boolean.TRUE, systemContainer.system);
    }

    @Test
    public void testProvisionSystemContainerWhenOlderVersionExistsOnHost() throws Throwable {
        String systemContainerId = extractId(systemContainerLink);
        String systemContainerRef = UriUtils.buildUri(host, systemContainerLink).toString();
        // deploy an old version of the system container
        String oldImage = String.format("%s:%s", SystemContainerDescriptions.AGENT_IMAGE_NAME,
                "0.0.0");

        // add system container to the adapter service because it already exists on host
        MockDockerAdapterService.addContainerId(TEST_HOST_ID, systemContainerId,
                systemContainerRef);
        MockDockerAdapterService.addContainerNames(TEST_HOST_ID, systemContainerId,
                SystemContainerDescriptions.AGENT_CONTAINER_NAME);
        MockDockerAdapterService.addContainerImage(TEST_HOST_ID, systemContainerId, oldImage);

        // run data collection on preexisting system container with old version
        startAndWaitHostContainerListDataCollection();

        // wait for the system container with the updated image
        ContainerState systemContainer = waitForContainer(systemContainerLink, image);

        assertNotNull("System container not created or can't be retrieved.", systemContainer);
        assertEquals(systemContainerLink, systemContainer.documentSelfLink);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_NAME,
                systemContainer.names.get(0));
        assertNotNull("System container not recreated", systemContainer.id);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK,
                systemContainer.descriptionLink);
        assertEquals(image, systemContainer.image);
        assertEquals(Boolean.TRUE, systemContainer.system);
    }

    private void startAndWaitHostContainerListDataCollection() throws Throwable {
        host.testStart(1);
        host.sendRequest(Operation
                .createPatch(
                        UriUtilsExtended
                                .buildUri(
                                        host,
                                        HostContainerListDataCollectionFactoryService.DEFAULT_HOST_CONAINER_LIST_DATA_COLLECTION_LINK))
                .setBody(containerListBody)
                .setReferer(host.getUri())
                .setCompletion(host.getCompletion()));
        host.testWait();
        // Wait for data collection to finish
        waitForDataCollectionFinished();
    }

    private ContainerState waitForContainer(String containerLink, String image) throws Throwable {
        ContainerState[] result = new ContainerState[1];
        AtomicBoolean cotinue = new AtomicBoolean();

        waitFor(() -> {
            ServiceDocumentQuery<ContainerState> query = new ServiceDocumentQuery<>(host,
                    ContainerState.class);
            query.queryDocument(containerLink, (r) -> {
                if (r.hasException()) {
                    host.log("Exception while retrieving ContainerState: "
                            + (r.getException() instanceof CancellationException ? r.getException()
                                    .getMessage() : Utils.toString(r.getException())));
                    cotinue.set(true);
                } else if (r.hasResult()) {
                    // wait until container is ready with the expected image
                    if (r.getResult().id != null && image.equals(r.getResult().image)) {
                        cotinue.set(true);
                        result[0] = r.getResult();
                    }
                }
            });
            return cotinue.get();
        });
        return result[0];
    }

    private void waitForDataCollectionFinished() throws Throwable {
        AtomicBoolean cotinue = new AtomicBoolean();

        String dataCollectionLink = HostContainerListDataCollectionFactoryService.DEFAULT_HOST_CONAINER_LIST_DATA_COLLECTION_LINK;
        waitFor(() -> {
            ServiceDocumentQuery<HostContainerListDataCollectionState> query = new ServiceDocumentQuery<>(
                    host, HostContainerListDataCollectionState.class);
            query.queryDocument(
                    dataCollectionLink,
                    (r) -> {
                        if (r.hasException()) {
                            host.log("Exception while retrieving default host container list data collection: "
                                    + (r.getException() instanceof CancellationException ? r
                                            .getException()
                                            .getMessage() : Utils.toString(r.getException())));
                            cotinue.set(true);
                        } else if (r.hasResult()) {
                            if (r.getResult().containerHostLinks.isEmpty()) {
                                cotinue.set(true);
                            }
                        }
                    });
            return cotinue.get();
        });
    }
}
