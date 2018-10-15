/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection.HostVolumeListDataCollectionState;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection.VolumeListCallback;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState.PowerState;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.admiral.service.test.MockDockerVolumeAdapterService;
import com.vmware.admiral.service.test.MockDockerVolumeToHostService;
import com.vmware.admiral.service.test.MockDockerVolumeToHostService.MockDockerVolumeToHostState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.QueryTask;

public class HostVolumeListDataCollectionTest extends ComputeBaseTest {
    private static final String TEST_PREEXISTING_VOLUME_NAME = "preexisting-volume-name";
    private static final String TEST_HOST_ID = "test-host-id-234:2376";
    private static final String COMPUTE_HOST_LINK = UriUtils.buildUriPath(
            ComputeService.FACTORY_LINK, TEST_HOST_ID);
    private static final String LOCAL_DRIVER = "local";
    private static final String VMDK_DRIVER = "vmdk";
    private static final String LOCAL_SCOPE = "local";
    private static final String GLOBAL_SCOPE = "global";

    private VolumeListCallback volumeListCallback;

    @Before
    public void setUp() throws Throwable {
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerVolumeAdapterService.class)), new MockDockerVolumeAdapterService());
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerHostAdapterService.class)), new MockDockerHostAdapterService());
        host.startFactory(new MockDockerVolumeToHostService());

        waitForServiceAvailability(ContainerHostDataCollectionService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);

        waitForServiceAvailability(ContainerVolumeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ContainerVolumeService.FACTORY_LINK);

        waitForServiceAvailability(MockDockerVolumeAdapterService.SELF_LINK);
        waitForServiceAvailability(MockDockerVolumeToHostService.FACTORY_LINK);
        waitForServiceAvailability(MockDockerHostAdapterService.SELF_LINK);
        waitForServiceAvailability(
                HostVolumeListDataCollection.DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK);

        volumeListCallback = new VolumeListCallback();
        volumeListCallback.containerHostLink = COMPUTE_HOST_LINK;

        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc = doPost(computeDesc, ComputeDescriptionService.FACTORY_LINK);

        ComputeState cs = new ComputeState();
        cs.id = TEST_HOST_ID;
        cs.documentSelfLink = TEST_HOST_ID;
        cs.descriptionLink = computeDesc.documentSelfLink;
        cs.customProperties = new HashMap<>();

        doPost(cs, ComputeService.FACTORY_LINK);
    }

    @Test
    public void testPutState() throws Throwable {
        HostVolumeListDataCollectionState hostVolumeListDataCollection =
                getDocument(HostVolumeListDataCollectionState.class ,HostVolumeListDataCollection
                        .DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK);
        assertEquals(0, hostVolumeListDataCollection.containerHostLinks.size());

        HostVolumeListDataCollectionState hostVolumeListDataCollectionNew = new
                HostVolumeListDataCollectionState();
        hostVolumeListDataCollectionNew.documentSelfLink = hostVolumeListDataCollection
                .documentSelfLink;
        hostVolumeListDataCollectionNew.containerHostLinks = new HashMap<>();
        hostVolumeListDataCollectionNew.containerHostLinks.put(COMPUTE_HOST_LINK, 1L);
        doPut(hostVolumeListDataCollectionNew);
        hostVolumeListDataCollection =
                getDocument(HostVolumeListDataCollectionState.class ,HostVolumeListDataCollection
                        .DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK);
        assertEquals(1, hostVolumeListDataCollection.containerHostLinks.size());
        assertEquals(new Long(1), hostVolumeListDataCollection.containerHostLinks.get
                (COMPUTE_HOST_LINK));
    }

    @Test
    public void testPostStateToPut() throws Throwable {
        HostVolumeListDataCollectionState hostVolumeListDataCollection =
                getDocument(HostVolumeListDataCollectionState.class ,HostVolumeListDataCollection
                        .DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK);
        assertEquals(0, hostVolumeListDataCollection.containerHostLinks.size());

        HostVolumeListDataCollectionState hostVolumeListDataCollectionNew = new
                HostVolumeListDataCollectionState();
        hostVolumeListDataCollectionNew.documentSelfLink = hostVolumeListDataCollection
                .documentSelfLink;
        hostVolumeListDataCollectionNew.containerHostLinks = new HashMap<>();
        hostVolumeListDataCollectionNew.containerHostLinks.put(COMPUTE_HOST_LINK, 1L);

        //converted to put which should be ignored
        doPost(hostVolumeListDataCollectionNew, HostVolumeListDataCollection.FACTORY_LINK);
        hostVolumeListDataCollection =
                getDocument(HostVolumeListDataCollectionState.class ,HostVolumeListDataCollection
                        .DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK);
        assertEquals(0, hostVolumeListDataCollection.containerHostLinks.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPostStateWithoutBody() throws Throwable {
        doPost(null, HostVolumeListDataCollection.DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testPostStateNotDefaultSelfLink() throws Throwable {
        HostVolumeListDataCollectionState hostVolumeListDataCollection =
                getDocument(HostVolumeListDataCollectionState.class, HostVolumeListDataCollection
                        .DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK);
        assertEquals(0, hostVolumeListDataCollection.containerHostLinks.size());

        HostVolumeListDataCollectionState hostVolumeListDataCollectionNew = new
                HostVolumeListDataCollectionState();
        hostVolumeListDataCollectionNew.documentSelfLink = "test";
        hostVolumeListDataCollectionNew.containerHostLinks = new HashMap<>();
        hostVolumeListDataCollectionNew.containerHostLinks.put(COMPUTE_HOST_LINK, 1L);

        //converted to put which should be ignored
        try {
            doPost(hostVolumeListDataCollectionNew,
                    HostVolumeListDataCollection.DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK);
        } catch (LocalizableValidationException e) {
            if (e.getMessage().contains("Only one instance of list containers data collection can"
                    + " be started")) {
                throw e;
            }
        }
        fail("Should fail with: Only one instance of list containers data collection can be started");
    }

    @Test
    public void testPostStateShouldCompleteWithNoExceptions() throws Throwable {
        HostVolumeListDataCollectionState hostVolumeListDataCollectionNew = new
                HostVolumeListDataCollectionState();
        hostVolumeListDataCollectionNew.documentSelfLink = HostVolumeListDataCollection
                .DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK;
        hostVolumeListDataCollectionNew.containerHostLinks = new HashMap<>();
        hostVolumeListDataCollectionNew.containerHostLinks.put(COMPUTE_HOST_LINK, 1L);
        doPost(hostVolumeListDataCollectionNew,
                HostVolumeListDataCollection.DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK);
    }

    @Test
    public void testDiscoverExistingVolumeOnHost() throws Throwable {
        // add preexisting volume to the adapter service
        addVolumeToMockAdapter(COMPUTE_HOST_LINK, TEST_PREEXISTING_VOLUME_NAME, LOCAL_DRIVER, LOCAL_SCOPE);

        // run data collection on preexisting volume
        startAndWaitHostVolumeListDataCollection();

        List<ContainerVolumeState> volumeStates = getVolumeStates();
        assertEquals(1, volumeStates.size());
        ContainerVolumeState preexistingVolumeState = volumeStates.get(0);
        assertNotNull("Preexisting volume not created or can't be retrieved.", preexistingVolumeState);
        assertEquals(TEST_PREEXISTING_VOLUME_NAME, preexistingVolumeState.name);
        assertTrue(Boolean.TRUE.equals(preexistingVolumeState.external));
        assertTrue("Preexisting volume belongs to the host.",
                COMPUTE_HOST_LINK.equals(preexistingVolumeState.originatingHostLink));
    }

    @Test
    public void testProvisionedVolumeIsNotDiscovered() throws Throwable {
        // provision volume
        ContainerVolumeState containerVolumeCreated = createVolume(null, COMPUTE_HOST_LINK);
        @SuppressWarnings("unused")
        String reference = UriUtils.buildUri(host, UriUtils
                .buildUriPath(ContainerVolumeService.FACTORY_LINK, TEST_PREEXISTING_VOLUME_NAME))
                .toString();
        // run data collection on preexisting volume
        startAndWaitHostVolumeListDataCollection();
        List<ContainerVolumeState> volumeStates = getVolumeStates();
        assertEquals(volumeStates.size(), 1);
        ContainerVolumeState containerVolumeGet = volumeStates.get(0);
        assertNotNull("Preexisting volume not created or can't be retrieved.", containerVolumeGet);
        assertEquals(containerVolumeCreated.id, containerVolumeGet.id);
        assertEquals(containerVolumeCreated.name, containerVolumeGet.name);
        assertEquals(containerVolumeCreated.documentSelfLink , containerVolumeGet.documentSelfLink);
    }

    /**
     * This test checks if the power state of a volume state is changed properly.
     */
    @Test
    public void testUpdateVolumePowerState() throws Throwable {

        addVolumeToMockAdapter(COMPUTE_HOST_LINK, TEST_PREEXISTING_VOLUME_NAME, LOCAL_DRIVER,
                LOCAL_SCOPE);

        startAndWaitHostVolumeListDataCollection();

        List<ContainerVolumeState> volumeStates = getVolumeStates();
        assertEquals(1, volumeStates.size());
        assertEquals(PowerState.CONNECTED, volumeStates.get(0).powerState);
        ContainerVolumeState containerVolumeState = new ContainerVolumeState();
        containerVolumeState.powerState = PowerState.PROVISIONING;
        containerVolumeState.driver = LOCAL_DRIVER;
        doPatch(containerVolumeState, volumeStates.get(0).documentSelfLink);

        volumeStates = getVolumeStates();
        assertEquals(1, volumeStates.size());
        assertEquals(PowerState.PROVISIONING, volumeStates.get(0).powerState);

        startAndWaitHostVolumeListDataCollection();

        volumeStates = getVolumeStates();
        assertEquals(1, volumeStates.size());
        assertEquals(PowerState.CONNECTED, volumeStates.get(0).powerState);
    }

     /** This test simulate a situation when a host is added in two different projects and in one of
     * them a volume is created. The volume should not be discovered in the second project.
     */
    @Test
    public void testProvisionedVolumeIsNotDiscoveredInOtherProject() throws Throwable {
        String secondDockerHostId = "test-host-2:2376";
        String secondDockerHostSelfLink = UriUtils.buildUriPath(
                ComputeService.FACTORY_LINK, secondDockerHostId);

        ComputeDescription computeDesc2 = new ComputeDescription();
        computeDesc2 = doPost(computeDesc2, ComputeDescriptionService.FACTORY_LINK);

        ComputeState cs2 = new ComputeState();
        cs2.id = secondDockerHostId;
        cs2.documentSelfLink = secondDockerHostSelfLink;
        cs2.descriptionLink = computeDesc2.documentSelfLink;


        cs2 = doPost(cs2, ComputeService.FACTORY_LINK);
        ContainerVolumeState containerVolumeCreated2 = createVolume(null, cs2.documentSelfLink);


        addVolumeToMockAdapter(COMPUTE_HOST_LINK, containerVolumeCreated2.name, LOCAL_DRIVER,
                LOCAL_SCOPE);

        startAndWaitHostVolumeListDataCollection();

        List<ContainerVolumeState> volumeStates = getVolumeStates();
        assertEquals(1, volumeStates.size());
    }

    @Test
    public void testRemoveVolumesInRetiredState() throws Throwable {
        // create a volume state but don't add it to the mock adapter
        ContainerVolumeState volume = createVolume(null, COMPUTE_HOST_LINK);
        assertNull(volume._healthFailureCount);

        // 1st data collection
        startAndWaitHostVolumeListDataCollection();

        volume = getDocument(ContainerVolumeState.class, volume.documentSelfLink);
        assertEquals(volume._healthFailureCount, Integer.valueOf(1));
        assertEquals(PowerState.RETIRED, volume.powerState);
        assertTrue("expiration not set", volume.documentExpirationTimeMicros > 0);

        // 2nd data collection
        startAndWaitHostVolumeListDataCollection();

        volume = getDocument(ContainerVolumeState.class, volume.documentSelfLink);
        assertEquals(volume._healthFailureCount, Integer.valueOf(2));
        assertEquals(PowerState.RETIRED, volume.powerState);

        // 3rd data collection
        startAndWaitHostVolumeListDataCollection();
        List<ContainerVolumeState> volumeStates = getVolumeStates();
        assertEquals(0, volumeStates.size());
    }

    @Test
    public void testDiscoverSharedVolumes() throws Throwable {
        // 1. add a global volume to the volume adapter
        addVolumeToMockAdapter(COMPUTE_HOST_LINK, TEST_PREEXISTING_VOLUME_NAME, LOCAL_DRIVER,
                LOCAL_SCOPE);

        startAndWaitHostVolumeListDataCollection();

        List<ContainerVolumeState> volumeStates = getVolumeStates();
        assertEquals(1, volumeStates.size());
        ContainerVolumeState volume = volumeStates.get(0);
        assertEquals(TEST_PREEXISTING_VOLUME_NAME, volume.name);
        // driver and scope must not be assigned during volume data collection
        assertNull(VMDK_DRIVER, volume.driver);
        assertNull(GLOBAL_SCOPE, volume.scope);
        assertNotNull(volume.parentLinks);
        assertEquals(1, volume.parentLinks.size());
        assertEquals(COMPUTE_HOST_LINK, volume.parentLinks.get(0));

        // simulate volume inspection by assigning scope and driver
        volume.driver = VMDK_DRIVER;
        volume.scope = GLOBAL_SCOPE;
        doPatch(volume, volume.documentSelfLink);

        // 2. add a second instance of the same volume on another host
        String secondHostId = "test-host-id-999:2376";
        String secondHostLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK, secondHostId);
        addVolumeToMockAdapter(secondHostLink, TEST_PREEXISTING_VOLUME_NAME, VMDK_DRIVER,
                GLOBAL_SCOPE);

        volumeListCallback.containerHostLink = secondHostLink;
        startAndWaitHostVolumeListDataCollection();

        volumeStates = getVolumeStates();
        assertEquals(1, volumeStates.size());
        volume = volumeStates.get(0);
        assertEquals(TEST_PREEXISTING_VOLUME_NAME, volume.name);
        assertEquals(VMDK_DRIVER, volume.driver);
        assertEquals(GLOBAL_SCOPE, volume.scope);
        assertNotNull(volume.parentLinks);
        assertEquals(2, volume.parentLinks.size());
        assertThat(volume.parentLinks, hasItems(COMPUTE_HOST_LINK, secondHostLink));
    }

    private void startAndWaitHostVolumeListDataCollection() throws Throwable {
        host.testStart(1);
        host.sendRequest(Operation
                .createPatch(UriUtils.buildUri(host,
                        HostVolumeListDataCollection.DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK))
                .setBody(volumeListCallback)
                .setReferer(host.getUri())
                .setCompletion(host.getCompletion()));
        host.testWait();
        // Wait for data collection to finish
        waitForDataCollectionFinished();
    }

    private void waitForDataCollectionFinished() throws Throwable {
        AtomicBoolean cotinue = new AtomicBoolean();

        String dataCollectionLink = HostVolumeListDataCollection.DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK;
        waitFor(() -> {
            ServiceDocumentQuery<HostVolumeListDataCollectionState> query = new ServiceDocumentQuery<>(
                    host, HostVolumeListDataCollectionState.class);
            query.queryDocument(dataCollectionLink, (r) -> {
                if (r.hasException()) {
                    host.log(
                            "Exception while retrieving default host volume list data collection: "
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

    private List<ContainerVolumeState> getVolumeStates() throws Throwable {
        List<ContainerVolumeState> volumeStatesFound = new ArrayList<>();
        TestContext ctx = testCreate(1);
        ServiceDocumentQuery<ContainerVolumeState> query = new ServiceDocumentQuery<>(host,
                ContainerVolumeState.class);

        QueryTask queryTask = QueryUtil.buildPropertyQuery(ContainerVolumeState.class);
        QueryUtil.addExpandOption(queryTask);

        query.query(queryTask, (r) -> {
            if (r.hasException()) {
                host.log("Exception while retrieving ContainerVolumeState: "
                        + (r.getException() instanceof CancellationException
                                ? r.getException().getMessage()
                                : Utils.toString(r.getException())));
                ctx.fail(r.getException());
            } else if (r.hasResult()) {
                volumeStatesFound.add(r.getResult());
            } else {
                ctx.complete();
            }
        });
        ctx.await();
        return volumeStatesFound;
    }

    private ContainerVolumeState createVolume(String name, String hostLink)
            throws Throwable {
        ContainerVolumeState volumeState = new ContainerVolumeState();
        volumeState.id = UUID.randomUUID().toString();
        if (name == null) {
            volumeState.name = "name_" + volumeState.id;
        } else {
            volumeState.name = name;
        }

        volumeState.driver = LOCAL_DRIVER;
        volumeState.documentSelfLink = volumeState.name;
        volumeState.originatingHostLink = hostLink;
        volumeState.parentLinks = Arrays.asList(hostLink);
        volumeState = doPost(volumeState, ContainerVolumeService.FACTORY_LINK);
        return volumeState;
    }

    private void addVolumeToMockAdapter(String hostLink, String volumeName, String driver, String scope) throws Throwable {
        MockDockerVolumeToHostState mockVolumeToHostState = new MockDockerVolumeToHostState();
        mockVolumeToHostState.documentSelfLink = UriUtils.buildUriPath(
                MockDockerVolumeToHostService.FACTORY_LINK, UUID.randomUUID().toString());
        mockVolumeToHostState.name = volumeName;
        mockVolumeToHostState.hostLink = hostLink;
        mockVolumeToHostState.driver = driver;
        mockVolumeToHostState.scope = scope;
        host.sendRequest(Operation.createPost(host, MockDockerVolumeToHostService.FACTORY_LINK)
                .setBody(mockVolumeToHostState)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log("Cannot create mock volume to host state. Error: %s", e.getMessage());
                    }
                }));
        // wait until volume to host is created in the mock adapter
        waitFor(() -> {
            getDocument(MockDockerVolumeToHostState.class, mockVolumeToHostState.documentSelfLink);
            return true;
        });
    }
}
