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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection.HostVolumeListDataCollectionFactoryService;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection.HostVolumeListDataCollectionState;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection.VolumeListCallback;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState.PowerState;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.admiral.service.test.MockDockerVolumeAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
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

    private VolumeListCallback volumeListCallback;
    private List<String> volumesForDeletion;

    @Before
    public void setUp() throws Throwable {
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerVolumeAdapterService.class)), new MockDockerVolumeAdapterService());
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerHostAdapterService.class)), new MockDockerHostAdapterService());

        waitForServiceAvailability(ContainerHostDataCollectionService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);

        waitForServiceAvailability(ContainerVolumeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ContainerVolumeService.FACTORY_LINK);

        waitForServiceAvailability(MockDockerVolumeAdapterService.SELF_LINK);
        waitForServiceAvailability(MockDockerHostAdapterService.SELF_LINK);
        waitForServiceAvailability(
                HostVolumeListDataCollectionFactoryService.DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK);

        volumeListCallback = new VolumeListCallback();
        volumeListCallback.containerHostLink = COMPUTE_HOST_LINK;

        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc = doPost(computeDesc, ComputeDescriptionService.FACTORY_LINK);

        ComputeState cs = new ComputeState();
        cs.id = TEST_HOST_ID;
        cs.documentSelfLink = TEST_HOST_ID;
        cs.descriptionLink = computeDesc.documentSelfLink;
        cs.customProperties = new HashMap<String, String>();

        doPost(cs, ComputeService.FACTORY_LINK);

        volumesForDeletion = new ArrayList<>();
    }

    @After
    public void tearDown() throws Throwable {
        for (String selfLink : volumesForDeletion) {
            delete(selfLink);
        }
        MockDockerVolumeAdapterService.resetVolumes();
    }

    @Test
    public void testDiscoverExistingVolumeOnHost() throws Throwable {
        // add preexisting volume to the adapter service
        String reference = UriUtils.buildUri(host, UriUtils
                .buildUriPath(ContainerVolumeService.FACTORY_LINK, TEST_PREEXISTING_VOLUME_NAME))
                .toString();
        addVolumeToMockAdapter(TEST_HOST_ID, reference, TEST_PREEXISTING_VOLUME_NAME);

        // run data collection on preexisting volume
        startAndWaitHostVolumeListDataCollection();

        List<ContainerVolumeState> volumeStates = getVolumeStates();
        assertEquals(volumeStates.size(), 1);
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

    @Test
    public void testRemoveVolumesInRetiredState() throws Throwable {
        // create a volume state but don't add it to the mock adapter
        ContainerVolumeState volume = createVolume(null, COMPUTE_HOST_LINK);
        assertNull(volume._healthFailureCount);

        // 1st data collection
        startAndWaitHostVolumeListDataCollection();

        volume = getDocument(ContainerVolumeState.class, volume.documentSelfLink);
        assertEquals(new Integer(1), volume._healthFailureCount);
        assertEquals(PowerState.RETIRED, volume.powerState);

        // 2nd data collection
        startAndWaitHostVolumeListDataCollection();

        volume = getDocument(ContainerVolumeState.class, volume.documentSelfLink);
        assertEquals(new Integer(2), volume._healthFailureCount);
        assertEquals(PowerState.RETIRED, volume.powerState);

        // 3rd data collection
        startAndWaitHostVolumeListDataCollection();
        List<ContainerVolumeState> volumeStates = getVolumeStates();
        assertEquals(0, volumeStates.size());
    }

    private void startAndWaitHostVolumeListDataCollection() throws Throwable {
        host.testStart(1);
        host.sendRequest(Operation
                .createPatch(UriUtils.buildUri(host,
                        HostVolumeListDataCollectionFactoryService.DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK))
                .setBody(volumeListCallback)
                .setReferer(host.getUri())
                .setCompletion(host.getCompletion()));
        host.testWait();
        // Wait for data collection to finish
        waitForDataCollectionFinished();
    }

    private void waitForDataCollectionFinished() throws Throwable {
        AtomicBoolean cotinue = new AtomicBoolean();

        String dataCollectionLink = HostVolumeListDataCollectionFactoryService.DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK;
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
        volumeState.originatingHostLink = hostLink;
        volumeState = doPost(volumeState, ContainerVolumeService.FACTORY_LINK);
        volumesForDeletion.add(volumeState.documentSelfLink);
        return volumeState;
    }

    private void addVolumeToMockAdapter(String hostId, String reference, String volumeName) {
        MockDockerVolumeAdapterService.addVolumeName(hostId, reference, volumeName);
    }
}
