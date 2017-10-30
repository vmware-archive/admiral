/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.HostNetworkListDataCollectionState;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.NetworkListCallback;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.admiral.service.test.MockDockerNetworkAdapterService;
import com.vmware.admiral.service.test.MockDockerNetworkToHostService;
import com.vmware.admiral.service.test.MockDockerNetworkToHostService.MockDockerNetworkToHostState;
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

public class HostNetworkListDataCollectionTest extends ComputeBaseTest {
    private static final String TEST_PREEXISTING_NETWORK_ID = "01234567789";
    private static final String TEST_PREEXISTING_NETWORK_NAME = "preexisting-network-name";
    private static final String TEST_HOST_ID = "test-host-id-234:2376";
    private static final String COMPUTE_HOST_LINK = UriUtils.buildUriPath(
            ComputeService.FACTORY_LINK, TEST_HOST_ID);
    private NetworkListCallback networkListCallback;

    @Before
    public void setUp() throws Throwable {
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerNetworkAdapterService.class)), new MockDockerNetworkAdapterService());
        host.startFactory(new MockDockerNetworkToHostService());
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerHostAdapterService.class)), new MockDockerHostAdapterService());

        waitForServiceAvailability(ContainerHostDataCollectionService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);

        waitForServiceAvailability(ContainerNetworkDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ContainerNetworkService.FACTORY_LINK);

        waitForServiceAvailability(MockDockerNetworkAdapterService.SELF_LINK);
        waitForServiceAvailability(MockDockerNetworkToHostService.FACTORY_LINK);
        waitForServiceAvailability(MockDockerHostAdapterService.SELF_LINK);
        waitForServiceAvailability(
                HostNetworkListDataCollection.DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK);

        networkListCallback = new NetworkListCallback();
        networkListCallback.containerHostLink = COMPUTE_HOST_LINK;
    }

    private void createDockerHost(ContainerHostType hostType) throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc = doPost(computeDesc, ComputeDescriptionService.FACTORY_LINK);

        ComputeState cs = new ComputeState();
        cs.id = TEST_HOST_ID;
        cs.documentSelfLink = TEST_HOST_ID;
        cs.descriptionLink = computeDesc.documentSelfLink;
        cs.customProperties = new HashMap<>();

        if (hostType == ContainerHostType.VCH) {
            cs.customProperties.put(ContainerHostUtil.PROPERTY_NAME_DRIVER,
                    ContainerHostUtil.VMWARE_VIC_DRIVER1);
        }

        doPost(cs, ComputeService.FACTORY_LINK);
    }

    @Test
    public void testDiscoverExistingNetworkOnHost() throws Throwable {
        createDockerHost(null);

        // add preexisting network to the adapter service
        addNetworkToMockAdapter(COMPUTE_HOST_LINK, TEST_PREEXISTING_NETWORK_ID,
                TEST_PREEXISTING_NETWORK_NAME);

        // run data collection on preexisting network
        startAndWaitHostNetworkListDataCollection();

        // ContainerNetworkState preexistingNetwork = waitForNetwork(preexistingNetworkLink);
        List<ContainerNetworkState> networkStates = getNetworkStates();
        assertEquals(1, networkStates.size());
        ContainerNetworkState preexistingNetworkState = networkStates.get(0);
        assertNotNull("Preexisting network not created or can't be retrieved.",
                preexistingNetworkState);
        assertEquals(TEST_PREEXISTING_NETWORK_ID, preexistingNetworkState.id);
        assertEquals(TEST_PREEXISTING_NETWORK_NAME, preexistingNetworkState.name);
        assertEquals(UriUtils.buildUriPath(ContainerNetworkService.FACTORY_LINK,
                TEST_PREEXISTING_NETWORK_ID), preexistingNetworkState.documentSelfLink);
        assertTrue(preexistingNetworkState.external);
        assertTrue((preexistingNetworkState.customProperties == null)
                || !Boolean.parseBoolean(preexistingNetworkState.customProperties
                        .get(ContainerNetworkDescription.CUSTOM_PROPERTY_NETWORK_RANGE_FORMAT_ALLOWED)));
        assertTrue("Preexisting network belongs to the host.",
                preexistingNetworkState.parentLinks.contains(COMPUTE_HOST_LINK));
    }

    @Test
    public void testDiscoverExistingNetworkOnVchHost() throws Throwable {
        createDockerHost(ContainerHostType.VCH);

        // add preexisting network to the adapter service
        addNetworkToMockAdapter(COMPUTE_HOST_LINK, TEST_PREEXISTING_NETWORK_ID,
                TEST_PREEXISTING_NETWORK_NAME);

        // run data collection on preexisting network
        startAndWaitHostNetworkListDataCollection();

        // ContainerNetworkState preexistingNetwork = waitForNetwork(preexistingNetworkLink);
        List<ContainerNetworkState> networkStates = getNetworkStates();
        assertEquals(1, networkStates.size());
        ContainerNetworkState preexistingNetworkState = networkStates.get(0);
        assertNotNull("Preexisting network not created or can't be retrieved.",
                preexistingNetworkState);
        assertEquals(TEST_PREEXISTING_NETWORK_ID, preexistingNetworkState.id);
        assertEquals(TEST_PREEXISTING_NETWORK_NAME, preexistingNetworkState.name);
        assertEquals(UriUtils.buildUriPath(ContainerNetworkService.FACTORY_LINK,
                TEST_PREEXISTING_NETWORK_ID), preexistingNetworkState.documentSelfLink);
        assertTrue(preexistingNetworkState.external);
        assertTrue(Boolean.parseBoolean(preexistingNetworkState.customProperties
                .get(ContainerNetworkDescription.CUSTOM_PROPERTY_NETWORK_RANGE_FORMAT_ALLOWED)));
        assertTrue("Preexisting network belongs to the host.",
                preexistingNetworkState.parentLinks.contains(COMPUTE_HOST_LINK));
    }

    @Test
    public void testProvisionedContainerIsNotDiscovered() throws Throwable {
        createDockerHost(null);

        // provision network
        ContainerNetworkState containerNetworkCreated = createNetwork(null);
        addNetworkToMockAdapter(COMPUTE_HOST_LINK, containerNetworkCreated.id,
                containerNetworkCreated.name);
        // run data collection on preexisting network
        startAndWaitHostNetworkListDataCollection();
        List<ContainerNetworkState> networkStates = getNetworkStates();
        assertEquals(1, networkStates.size());
        ContainerNetworkState containerNetworkGet = networkStates.get(0);
        assertNotNull("Preexisting network not created or can't be retrieved.",
                containerNetworkGet);
        assertEquals(containerNetworkCreated.id, containerNetworkGet.id);
        assertEquals(containerNetworkCreated.name, containerNetworkGet.name);
        assertEquals(containerNetworkCreated.documentSelfLink,
                containerNetworkGet.documentSelfLink);
    }

    @Test
    public void testDiscoveredAndCreatedNetworksWithSameNames() throws Throwable {
        createDockerHost(null);

        getNetworkStates();
        // add preexisting network to the adapter service
        addNetworkToMockAdapter(COMPUTE_HOST_LINK, TEST_PREEXISTING_NETWORK_ID,
                TEST_PREEXISTING_NETWORK_NAME);
        // provision network
        ContainerNetworkState containerNetworkCreated = createNetwork(
                TEST_PREEXISTING_NETWORK_NAME);
        addNetworkToMockAdapter(COMPUTE_HOST_LINK, containerNetworkCreated.id,
                containerNetworkCreated.name);
        // run data collection on preexisting network
        startAndWaitHostNetworkListDataCollection();
        List<ContainerNetworkState> networkStates = getNetworkStates();
        assertEquals(2, networkStates.size());
        assertEquals(networkStates.get(0).name, networkStates.get(1).name);
        assertTrue(networkStates.get(0).name != networkStates.get(1).name);
        assertTrue(networkStates.get(0).documentSelfLink != networkStates.get(1).documentSelfLink);
    }

    private void startAndWaitHostNetworkListDataCollection() throws Throwable {
        host.testStart(1);
        host.sendRequest(Operation
                .createPatch(UriUtils.buildUri(host,
                        HostNetworkListDataCollection.DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK))
                .setBody(networkListCallback)
                .setReferer(host.getUri())
                .setCompletion(host.getCompletion()));
        host.testWait();
        // Wait for data collection to finish
        waitForDataCollectionFinished();
    }

    private List<ContainerNetworkState> getNetworkStates() throws Throwable {
        List<ContainerNetworkState> networkStatesFound = new ArrayList<>();
        TestContext ctx = testCreate(1);
        ServiceDocumentQuery<ContainerNetworkState> query = new ServiceDocumentQuery<>(host,
                ContainerNetworkState.class);

        QueryTask queryTask = QueryUtil.buildPropertyQuery(ContainerNetworkState.class);
        QueryUtil.addExpandOption(queryTask);

        query.query(queryTask, (r) -> {
            if (r.hasException()) {
                host.log("Exception while retrieving ContainerNetworkState: "
                        + (r.getException() instanceof CancellationException
                                ? r.getException().getMessage()
                                : Utils.toString(r.getException())));
                ctx.fail(r.getException());
            } else if (r.hasResult()) {
                networkStatesFound.add(r.getResult());
            } else {
                ctx.complete();
            }
        });
        ctx.await();
        return networkStatesFound;
    }

    private void waitForDataCollectionFinished() throws Throwable {
        AtomicBoolean cotinue = new AtomicBoolean();

        String dataCollectionLink = HostNetworkListDataCollection.DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK;
        waitFor(() -> {
            ServiceDocumentQuery<HostNetworkListDataCollectionState> query = new ServiceDocumentQuery<>(
                    host, HostNetworkListDataCollectionState.class);
            query.queryDocument(dataCollectionLink, (r) -> {
                if (r.hasException()) {
                    host.log(
                            "Exception while retrieving default host network list data collection: "
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

    private ContainerNetworkState createNetwork(String name)
            throws Throwable {
        ContainerNetworkState networkState = new ContainerNetworkState();
        networkState.id = UUID.randomUUID().toString();
        if (name == null) {
            networkState.name = "name_" + networkState.id;
        } else {
            networkState.name = name;
        }
        networkState = doPost(networkState, ContainerNetworkService.FACTORY_LINK);
        return networkState;
    }

    private void addNetworkToMockAdapter(String hostLink, String networkId, String networkNames)
            throws Throwable {
        MockDockerNetworkToHostState mockNetworkToHostState = new MockDockerNetworkToHostState();
        mockNetworkToHostState.documentSelfLink = UriUtils.buildUriPath(
                MockDockerNetworkToHostService.FACTORY_LINK, UUID.randomUUID().toString());
        mockNetworkToHostState.hostLink = hostLink;
        mockNetworkToHostState.id = networkId;
        mockNetworkToHostState.name = networkNames;
        host.sendRequest(Operation.createPost(host, MockDockerNetworkToHostService.FACTORY_LINK)
                .setBody(mockNetworkToHostState)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log("Cannot create mock network to host state. Error: %s",
                                e.getMessage());
                    }
                }));
        // wait until network to host is created in the mock adapter
        waitFor(() -> {
            getDocument(MockDockerNetworkToHostState.class,
                    mockNetworkToHostState.documentSelfLink);
            return true;
        });
    }
}
