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

package com.vmware.admiral.compute.container;

import static com.vmware.admiral.compute.container.HostContainerListDataCollection.DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.ContainerListCallback;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.ContainerVersion;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionState;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.admiral.service.test.MockDockerContainerToHostService;
import com.vmware.admiral.service.test.MockDockerContainerToHostService.MockDockerContainerToHostState;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestRequestSender;

public class HostContainerListDataCollectionTest extends ComputeBaseTest {
    private static final String TEST_PREEXISTING_CONTAINER_ID = "preexisting-container";
    private static final String TEST_HOST_ID = "test-host-id-234:2376";
    private static final String COMPUTE_HOST_LINK = UriUtils.buildUriPath(
            ComputeService.FACTORY_LINK, TEST_HOST_ID);
    private ContainerListCallback containerListBody;
    private String systemContainerLink;
    private ComputeState computeState;
    private String image;

    @Before
    public void setUp() throws Throwable {
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerAdapterService.class)), new MockDockerAdapterService());
        host.startFactory(new MockDockerContainerToHostService());
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerHostAdapterService.class)), new MockDockerHostAdapterService());

        waitForServiceAvailability(ContainerHostDataCollectionService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);

        waitForServiceAvailability(MockDockerAdapterService.SELF_LINK);
        waitForServiceAvailability(MockDockerContainerToHostService.FACTORY_LINK);
        waitForServiceAvailability(MockDockerHostAdapterService.SELF_LINK);
        waitForServiceAvailability(DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK);

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
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(MockDockerHostAdapterService.DOCKER_INFO_STORAGE_DRIVER_PROP_NAME, "overlay");
        cs.tenantLinks = TENANT_LINKS;

        computeState = doPost(cs, ComputeService.FACTORY_LINK);

        systemContainerLink = SystemContainerDescriptions.getSystemContainerSelfLink(
                SystemContainerDescriptions.AGENT_CONTAINER_NAME, TEST_HOST_ID);
        image = String.format("%s:%s", SystemContainerDescriptions.AGENT_IMAGE_NAME,
                SystemContainerDescriptions.getAgentImageVersion());
    }

    @Test
    public void testPost() throws Throwable {
        TestRequestSender sender = host.getTestRequestSender();
        TestRequestSender.FailureResponse failureResponse = sender.sendAndWaitFailure(Operation
                .createPost(host, DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK));
        assertTrue(IllegalArgumentException.class.equals(failureResponse.failure.getClass()));

        failureResponse = sender.sendAndWaitFailure(Operation
                .createPost(host, DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK)
                .setBodyNoCloning(new HostContainerListDataCollectionState()));
        assertTrue(LocalizableValidationException.class.equals(failureResponse.failure.getClass()));
        assertTrue(failureResponse.failure.getMessage().startsWith("Only one instance"));

        HostContainerListDataCollectionState dc = new HostContainerListDataCollectionState();
        dc.documentSelfLink = DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK;
        Operation response = sender.sendAndWait(Operation
                .createPost(host, DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK)
                .setBodyNoCloning(dc));
        assertTrue(Operation.STATUS_CODE_OK == response.getStatusCode());
    }

    @Test
    public void testPut() throws Throwable {
        TestRequestSender sender = host.getTestRequestSender();
        Operation response = sender.sendAndWait(Operation
                .createPut(host, DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)
                .setBodyNoCloning(new HostContainerListDataCollectionState()));
        assertTrue(Operation.STATUS_CODE_OK == response.getStatusCode());

        TestRequestSender.FailureResponse failureResponse = sender.sendAndWaitFailure(Operation
                .createPut(host, DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK));
        assertTrue(IllegalArgumentException.class.equals(failureResponse.failure.getClass()));

        response = sender.sendAndWait(Operation
                .createPut(host, DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK)
                .setBodyNoCloning(new HostContainerListDataCollectionState()));
        assertTrue(Operation.STATUS_CODE_OK == response.getStatusCode());
    }

    @Test
    public void testPatch() throws Throwable {
        TestRequestSender sender = host.getTestRequestSender();
        Operation response = sender.sendAndWait(Operation
                .createPatch(host, DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK)
                .setBodyNoCloning(new ContainerListCallback()));
        assertTrue(Operation.STATUS_CODE_NOT_MODIFIED == response.getStatusCode());

        ContainerListCallback body = new ContainerListCallback();
        body.containerHostLink = "h1";

        response = sender.sendAndWait(Operation
                .createPatch(host, DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK)
                .setBodyNoCloning(body));
        assertTrue(Operation.STATUS_CODE_OK == response.getStatusCode());

        body = new ContainerListCallback();
        body.containerHostLink = "h1";

        response = sender.sendAndWait(Operation
                .createPatch(host, DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK)
                .setBodyNoCloning(body));
        assertTrue(Operation.STATUS_CODE_NOT_MODIFIED == response.getStatusCode());
    }

    @Test
    public void testProvisionSystemContainerWhenDoesNotExistsOnHost() throws Throwable {
        // add preexisting container to the adapter service
        addContainerToMockAdapter(COMPUTE_HOST_LINK, TEST_PREEXISTING_CONTAINER_ID,
                TEST_PREEXISTING_CONTAINER_ID, "TestName", computeState.tenantLinks);

        // run data collection on preexisting container
        startAndWaitHostContainerListDataCollection();

        ContainerState systemContainer = waitForContainer(systemContainerLink, image, null, null);
        assertNotNull("System container not created or can't be retrieved.", systemContainer);
        assertEquals(systemContainerLink, systemContainer.documentSelfLink);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_NAME,
                systemContainer.names.get(0));
        assertNotNull("System container not provisioned", systemContainer.id);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK,
                systemContainer.descriptionLink);
        assertEquals(Boolean.TRUE, systemContainer.system);
        assertEquals(TENANT_LINKS, systemContainer.tenantLinks);
    }

    @Test
    public void testProvisionSystemContainerWhenExistsOnHost() throws Throwable {
        String systemContainerId = extractId(systemContainerLink);

        // add system container to the adapter service because it already exists on host
        addContainerToMockAdapter(COMPUTE_HOST_LINK, systemContainerId,
                SystemContainerDescriptions.AGENT_CONTAINER_NAME, image, computeState.tenantLinks);

        // run data collection on preexisting system container
        startAndWaitHostContainerListDataCollection();

        ContainerState systemContainer = waitForContainer(systemContainerLink, image, null, null);
        assertNotNull("System container not created or can't be retrieved.", systemContainer);
        assertEquals(systemContainerLink, systemContainer.documentSelfLink);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_NAME,
                systemContainer.names.get(0));
        assertNotNull("System container not discovered", systemContainer.id);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK,
                systemContainer.descriptionLink);
        assertEquals(image, systemContainer.image);
        assertEquals(Boolean.TRUE, systemContainer.system);
        assertEquals(TENANT_LINKS, systemContainer.tenantLinks);
    }

    @Test
    public void testStateStuckInProvisioningWhenExistsOnHost() throws Throwable {
        testStateStuckInProvisioning(false);
    }

    @Test
    public void testStateStuckInProvisioningWhenDoesNotExistsOnHost() throws Throwable {
        testStateStuckInProvisioning(true);
    }

    @Test
    public void testProvisionSystemContainerWhenOlderVersionExistsOnHost() throws Throwable {
        String systemContainerId = extractId(systemContainerLink);
        // deploy an old version of the system container
        String oldImage = String.format("%s:%s", SystemContainerDescriptions.AGENT_IMAGE_NAME,
                "0.0.0");

        // add system container to the adapter service because it already exists on host
        addContainerToMockAdapter(COMPUTE_HOST_LINK, systemContainerId,
                SystemContainerDescriptions.AGENT_CONTAINER_NAME, oldImage, computeState.tenantLinks);

        // run data collection on preexisting system container with old version
        startAndWaitHostContainerListDataCollection();

        // wait for the system container with the updated image
        ContainerState systemContainer = waitForContainer(systemContainerLink, image, null, null);

        assertNotNull("System container not created or can't be retrieved.", systemContainer);
        assertEquals(systemContainerLink, systemContainer.documentSelfLink);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_NAME,
                systemContainer.names.get(0));
        assertNotNull("System container volumes should not be empty", systemContainer.volumes);
        assertNotNull("System container not recreated", systemContainer.id);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK,
                systemContainer.descriptionLink);
        assertEquals(image, systemContainer.image);
        assertEquals(Boolean.TRUE, systemContainer.system);
        assertEquals(TENANT_LINKS, systemContainer.tenantLinks);
    }

    // VBV-1023
    @Test
    public void testProvisionSystemContainerWhenVersionIsWrong() throws Throwable {
        String systemContainerId = extractId(systemContainerLink);
        // deploy an old version of the system container
        String oldImage = String.format("%s:%s", "test", "abcdefg");

        // add system container to the adapter service because it already exists on host
        addContainerToMockAdapter(COMPUTE_HOST_LINK, systemContainerId,
                SystemContainerDescriptions.AGENT_CONTAINER_NAME, oldImage, computeState.tenantLinks);

        // run data collection on preexisting system container with old version
        startAndWaitHostContainerListDataCollection();

        // wait for the system container with the updated image
        ContainerState systemContainer = waitForContainer(systemContainerLink, image, null, null);

        assertNotNull("System container not created or can't be retrieved.", systemContainer);
        assertEquals(systemContainerLink, systemContainer.documentSelfLink);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_NAME,
                systemContainer.names.get(0));
        assertNotNull("System container volumes should not be empty", systemContainer.volumes);
        assertNotNull("System container not recreated", systemContainer.id);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK,
                systemContainer.descriptionLink);
        assertEquals(image, systemContainer.image);
        assertEquals(Boolean.TRUE, systemContainer.system);
        assertEquals(TENANT_LINKS, systemContainer.tenantLinks);
    }

    @Test
    public void testMissingContainer() throws Throwable {
        ContainerState cs = new ContainerState();
        cs.id = UUID.randomUUID().toString();
        cs.names = new ArrayList<>(Collections.singletonList("name_" + cs.id));
        cs.parentLink = COMPUTE_HOST_LINK;
        cs.powerState = ContainerState.PowerState.RUNNING;

        cs = doPost(cs, ContainerFactoryService.SELF_LINK);

        // run data collection on preexisting system container with old version
        startAndWaitHostContainerListDataCollection();

        cs = getDocument(ContainerState.class, cs.documentSelfLink);
        assertEquals(PowerState.RETIRED, cs.powerState);
    }

    @Test
    public void testStoppedContainer() throws Throwable {
        String image = "image:ver";

        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.image = image;
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        ContainerState cs = new ContainerState();
        cs.id = UUID.randomUUID().toString();
        cs.names = new ArrayList<>(Collections.singletonList("name_" + cs.id));
        cs.parentLink = COMPUTE_HOST_LINK;
        cs.powerState = ContainerState.PowerState.RUNNING;
        cs.image = image;
        cs.adapterManagementReference = UriUtils.buildUri(ManagementUriParts.ADAPTER_DOCKER);
        cs.descriptionLink = containerDesc.documentSelfLink;

        cs = doPost(cs, ContainerFactoryService.SELF_LINK);

        // add system container to the adapter service because it already exists on host
        addContainerToMockAdapter(COMPUTE_HOST_LINK, cs.id, cs.names.get(0), image, PowerState.STOPPED, computeState.tenantLinks);

        // run data collection on preexisting system container with old version
        startAndWaitHostContainerListDataCollection();

        cs = getDocument(ContainerState.class, cs.documentSelfLink);
        assertEquals(PowerState.STOPPED, cs.powerState);
    }

    @Test
    public void testContainerVersion() throws Throwable {
        ContainerVersion cv22 = ContainerVersion.fromImageName("abc:2.2");
        ContainerVersion cv23 = ContainerVersion.fromImageName("abc:2.3");
        ContainerVersion cvLatest = ContainerVersion.fromImageName("abc:latest");

        assertTrue(cv22.hashCode() != cv23.hashCode());
        assertTrue(!cv23.equals(cvLatest));

        assertEquals(0, cvLatest.compareTo(cvLatest));
        assertEquals(0, cv22.compareTo(cv22));

        assertTrue(cv22.compareTo(cv23) < 0);
        assertTrue(cv23.compareTo(cv22) > 0);

        assertTrue(cv23.compareTo(cvLatest) < 0);
        assertTrue(cvLatest.compareTo(cv22) > 0);
    }

    private void testStateStuckInProvisioning(boolean isSystemContainerMissingOnHost)
            throws Throwable {
        String systemContainerId = extractId(systemContainerLink);

        addContainerToMockAdapter(COMPUTE_HOST_LINK, systemContainerId,
                SystemContainerDescriptions.AGENT_CONTAINER_NAME, image, computeState.tenantLinks);

        startAndWaitHostContainerListDataCollection();
        waitForContainer(systemContainerLink, image, PowerState.RUNNING,
                "System container state should be running after regular data collection.");

        ContainerState cs = new ContainerState();
        cs.powerState = PowerState.PROVISIONING;
        doPatch(cs, systemContainerLink);
        waitForContainer(systemContainerLink, image, PowerState.PROVISIONING,
                "System container state should be provisioning after patching it to provisioning.");

        startAndWaitHostContainerListDataCollection();
        waitForContainer(systemContainerLink, image, PowerState.RUNNING,
                "System container state should be running after the second data collection when the"
                        + " state was provisioning.");
    }

    private void startAndWaitHostContainerListDataCollection() throws Throwable {
        host.testStart(1);
        host.sendRequest(Operation
                .createPatch(UriUtils.buildUri(host,
                        DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK))
                .setBody(containerListBody)
                .setReferer(host.getUri())
                .setCompletion(host.getCompletion()));
        host.testWait();
        // Wait for data collection to finish
        waitForDataCollectionFinished();
    }

    private ContainerState waitForContainer(String containerLink, String image,
            PowerState expectedPowerState, String errorMessage) throws Throwable {
        ContainerState[] result = new ContainerState[1];
        AtomicBoolean cotinue = new AtomicBoolean();

        waitFor(errorMessage, () -> {
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
                        if (expectedPowerState == null
                                || expectedPowerState == r.getResult().powerState) {
                            cotinue.set(true);
                            result[0] = r.getResult();
                        }
                    }
                }
            });
            return cotinue.get();
        });
        return result[0];
    }

    private void waitForDataCollectionFinished() throws Throwable {
        AtomicBoolean cotinue = new AtomicBoolean();

        String dataCollectionLink = DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK;
        waitFor(() -> {
            ServiceDocumentQuery<HostContainerListDataCollectionState> query =
                    new ServiceDocumentQuery<>(host, HostContainerListDataCollectionState.class);
            query.queryDocument(
                    dataCollectionLink,
                    (r) -> {
                        if (r.hasException()) {
                            host.log("Exception while retrieving default host container list data"
                                    + " collection: "
                                    + (r.getException() instanceof CancellationException
                                        ? r.getException().getMessage()
                                        : Utils.toString(r.getException())));
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

    private void addContainerToMockAdapter(String hostLink, String containerId,
            String containerName, String containerImage, List<String> tenantLinks) throws Throwable {
        addContainerToMockAdapter(hostLink, containerId, containerName, containerImage,
                PowerState.UNKNOWN, tenantLinks);
    }

    private void addContainerToMockAdapter(String hostLink, String containerId,
            String containerName, String containerImage, PowerState powerState, List<String> tenantLinks) throws Throwable {
        MockDockerContainerToHostState mockContainerToHostState = new MockDockerContainerToHostState();
        mockContainerToHostState.documentSelfLink = UriUtils.buildUriPath(
                MockDockerContainerToHostService.FACTORY_LINK, UUID.randomUUID().toString());
        mockContainerToHostState.parentLink = hostLink;
        mockContainerToHostState.id = containerId;
        mockContainerToHostState.name = containerName;
        mockContainerToHostState.image = containerImage;
        mockContainerToHostState.powerState = powerState;
        mockContainerToHostState.tenantLinks = tenantLinks;

        host.sendRequest(Operation.createPost(host, MockDockerContainerToHostService.FACTORY_LINK)
                .setBody(mockContainerToHostState)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log("Cannot create mock container to host state. Error: %s",
                                e.getMessage());
                    }
                }));
        // wait until container to host is created in the mock adapter
        waitFor(() -> {
            getDocument(MockDockerContainerToHostState.class,
                    mockContainerToHostState.documentSelfLink);
            return true;
        });
    }
}
