/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.pks.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_EXISTS_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_QUERY_PARAM_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_UUID_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_ENDPOINT_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_ENDPOINT_QUERY_PARAM_NAME;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.pks.entities.PKSCluster;
import com.vmware.admiral.adapter.pks.test.MockPKSAdapterService;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.pks.PKSEndpointService;
import com.vmware.admiral.compute.pks.PKSEndpointService.Endpoint;
import com.vmware.admiral.host.HostInitKubernetesAdapterServiceConfig;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestRequestSender;

public class PKSClusterListServiceTest extends ComputeBaseTest {

    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        HostInitKubernetesAdapterServiceConfig.startServices(host, true);
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                PKSClusterListService.class)), new PKSClusterListService());

        waitForServiceAvailability(MockPKSAdapterService.SELF_LINK);
        waitForServiceAvailability(PKSClusterListService.SELF_LINK);
        waitForServiceAvailability(PKSEndpointService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);

        sender = host.getTestRequestSender();
    }

    @Test
    public void testListPKSClusters() {
        String endpointLink = createEndpoint().documentSelfLink;
        createComputeState(endpointLink, MockPKSAdapterService.CLUSTER1_UUID);
        List<PKSCluster> discoveredClusters = sendListRequest(endpointLink, null, false);

        assertNotNull(discoveredClusters);
        assertEquals(2, discoveredClusters.size());
        List<PKSCluster> markedAdded = discoveredClusters.stream()
                .filter(c -> c.parameters.containsKey(PKS_CLUSTER_EXISTS_PROP_NAME))
                .collect(Collectors.toList());

        assertEquals(1, markedAdded.size());
        assertEquals(MockPKSAdapterService.CLUSTER1_UUID, markedAdded.get(0).uuid);
    }

    @Test
    public void testGetPKSCluster() {
        String endpoint = createEndpoint().documentSelfLink;
        List<PKSCluster> clusters = sendListRequest(endpoint, "unit-test-create-success", false);

        assertNotNull(clusters);
        assertEquals(1, clusters.size());

        clusters = sendListRequest(endpoint, "non-existing-cluster", true);
        assertNull(clusters);
    }

    private List<PKSCluster> sendListRequest(String endpoint, String cluster, boolean expectFail) {
        String query = cluster == null
                ? UriUtils.buildUriQuery(PKS_ENDPOINT_QUERY_PARAM_NAME, endpoint)
                : UriUtils.buildUriQuery(PKS_ENDPOINT_QUERY_PARAM_NAME, endpoint,
                PKS_CLUSTER_QUERY_PARAM_NAME, cluster);
        URI serviceUri = UriUtils.buildUri(host, PKSClusterListService.SELF_LINK, query);

        Operation result = sender.sendAndWait(
                Collections.singletonList(Operation.createGet(serviceUri)), false).get(0);

        if (!expectFail) {
            return new ArrayList<>(Arrays.asList(result.getBody(PKSCluster[].class)));
        } else {
            if (result.getStatusCode() >= Operation.STATUS_CODE_BAD_REQUEST) {
                return null;
            }
        }
        fail("should have failed");
        return null;
    }

    private Endpoint createEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";

        Operation o = Operation
                .createPost(host, PKSEndpointService.FACTORY_LINK)
                .setBodyNoCloning(endpoint);
        Endpoint result = sender.sendAndWait(o, Endpoint.class);

        return result;
    }

    private ComputeState createComputeState(String endpointLink, String pksClusterUUID) {
        ComputeState pksHost = new ComputeState();
        pksHost.address = "hostname";
        pksHost.descriptionLink = "description";
        pksHost.customProperties = new HashMap<>();
        pksHost.customProperties.put(PKS_ENDPOINT_PROP_NAME, endpointLink);
        pksHost.customProperties.put(PKS_CLUSTER_UUID_PROP_NAME, pksClusterUUID);

        Operation createHostOp = Operation.createPost(host, ComputeService.FACTORY_LINK)
                .setBody(pksHost);
        pksHost = sender.sendAndWait(createHostOp, ComputeState.class);

        return pksHost;
    }
}
