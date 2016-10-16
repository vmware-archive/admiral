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
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.HostNetworkListDataCollectionFactoryService;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.HostNetworkListDataCollectionState;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.NetworkListCallback;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.admiral.service.test.MockDockerNetworkAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class HostNetworkListDataCollectionTest extends ComputeBaseTest {
    private static final String TEST_PREEXISTING_NETWORK_ID = "preexisting-network";
    private static final String TEST_PREEXISTING_NETWORK_NAME = "preexisting-network-name";
    private static final String TEST_HOST_ID = "test-host-id-234:2376";
    private static final String COMPUTE_HOST_LINK = UriUtils.buildUriPath(
            ComputeService.FACTORY_LINK, TEST_HOST_ID);
    private NetworkListCallback networkListCallback;

    @Before
    public void setUp() throws Throwable {
        host.startService(Operation.createPost(UriUtilsExtended.buildUri(host,
                MockDockerNetworkAdapterService.class)), new MockDockerNetworkAdapterService());
        host.startService(Operation.createPost(UriUtilsExtended.buildUri(host,
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
        waitForServiceAvailability(MockDockerHostAdapterService.SELF_LINK);
        waitForServiceAvailability(
                HostNetworkListDataCollectionFactoryService.DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK);

        networkListCallback = new NetworkListCallback();
        networkListCallback.containerHostLink = COMPUTE_HOST_LINK;

        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc = doPost(computeDesc, ComputeDescriptionService.FACTORY_LINK);

        ComputeState cs = new ComputeState();
        cs.id = TEST_HOST_ID;
        cs.documentSelfLink = TEST_HOST_ID;
        cs.descriptionLink = computeDesc.documentSelfLink;
        cs.customProperties = new HashMap<String, String>();

        doPost(cs, ComputeService.FACTORY_LINK);
    }

    @After
    public void tearDown() throws Throwable {
        MockDockerNetworkAdapterService.resetNetworks();
    }

    @Test
    public void testDiscoverExistingNetworkOnHost() throws Throwable {
        // add preexisting network to the adapter service
        MockDockerNetworkAdapterService.addNetworkId(TEST_HOST_ID, TEST_PREEXISTING_NETWORK_ID,
                TEST_PREEXISTING_NETWORK_ID);
        MockDockerNetworkAdapterService.addNetworkNames(TEST_HOST_ID, TEST_PREEXISTING_NETWORK_ID,
                TEST_PREEXISTING_NETWORK_NAME);

        // run data collection on preexisting network
        startAndWaitHostNetworkListDataCollection();

        // ContainerNetworkState preexistingNetwork = waitForNetwork(preexistingNetworkLink);
        ContainerNetworkState preexistingNetwork = waitForNetworkById(TEST_PREEXISTING_NETWORK_ID);
        assertNotNull("Preexisting network not created or can't be retrieved.", preexistingNetwork);
        assertEquals(TEST_PREEXISTING_NETWORK_ID, preexistingNetwork.id);
        assertEquals(TEST_PREEXISTING_NETWORK_NAME, preexistingNetwork.name);
        assertEquals(UriUtils.buildUriPath(ContainerNetworkService.FACTORY_LINK,
                TEST_PREEXISTING_NETWORK_NAME), preexistingNetwork.documentSelfLink);
        assertTrue(Boolean.TRUE.equals(preexistingNetwork.external));

        assertTrue("Preexisting network belongs to the host.",
                preexistingNetwork.parentLinks.contains(COMPUTE_HOST_LINK));
    }

    private void startAndWaitHostNetworkListDataCollection() throws Throwable {
        host.testStart(1);
        host.sendRequest(Operation
                .createPatch(UriUtilsExtended.buildUri(host,
                        HostNetworkListDataCollectionFactoryService.DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK))
                .setBody(networkListCallback)
                .setReferer(host.getUri())
                .setCompletion(host.getCompletion()));
        host.testWait();
        // Wait for data collection to finish
        waitForDataCollectionFinished();
    }

    private ContainerNetworkState waitForNetworkById(String networkId) throws Throwable {
        ContainerNetworkState[] result = new ContainerNetworkState[1];
        AtomicBoolean cotinue = new AtomicBoolean();

        waitFor(() -> {
            ServiceDocumentQuery<ContainerNetworkState> query = new ServiceDocumentQuery<>(host,
                    ContainerNetworkState.class);

            QueryTask queryTask = QueryUtil.buildPropertyQuery(ContainerNetworkState.class,
                    ContainerNetworkState.FIELD_NAME_ID, networkId);
            QueryUtil.addExpandOption(queryTask);

            query.query(queryTask, (r) -> {
                if (r.hasException()) {
                    host.log("Exception while retrieving ContainerNetworkState: "
                            + (r.getException() instanceof CancellationException
                                    ? r.getException().getMessage()
                                    : Utils.toString(r.getException())));
                    cotinue.set(true);
                } else if (r.hasResult()) {
                    // wait until network is ready
                    if (networkId.equals(r.getResult().id)) {
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

        String dataCollectionLink = HostNetworkListDataCollectionFactoryService.DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK;
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
}
