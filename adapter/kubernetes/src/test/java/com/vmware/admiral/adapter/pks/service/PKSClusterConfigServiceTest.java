/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.pks.service;

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_PLAN_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_UUID_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_ENDPOINT_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_MASTER_HOST_FIELD;
import static com.vmware.admiral.common.util.OperationUtil.PROJECT_ADMIRAL_HEADER;
import static com.vmware.admiral.compute.ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.pks.entities.PKSCluster;
import com.vmware.admiral.adapter.pks.service.PKSClusterConfigService.AddClusterRequest;
import com.vmware.admiral.adapter.pks.test.MockPKSAdapterService;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.pks.PKSEndpointFactoryService;
import com.vmware.admiral.compute.pks.PKSEndpointService.Endpoint;
import com.vmware.admiral.host.HostInitKubernetesAdapterServiceConfig;
import com.vmware.admiral.service.test.MockKubernetesHostAdapterService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.TestRequestSender.FailureResponse;
import com.vmware.xenon.services.common.AuthCredentialsService;

public class PKSClusterConfigServiceTest extends ComputeBaseTest {

    private static final String CLUSTER_NAME = "cluster1";
    private static final String CLUSTER_UUID = UUID.randomUUID().toString();
    private static final String CLUSTER_HOSTNAME = "test-cluster";
    private static final String CLUSTER_PORT = "8443";
    private static final String PLAN_NAME = "small";
    private static final String PROJECT_LINK = "/projects/test";

    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        HostInitKubernetesAdapterServiceConfig.startServices(host, true);
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                PKSClusterConfigService.class)), new PKSClusterConfigService());
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockKubernetesHostAdapterService.class)), new MockKubernetesHostAdapterService());

        waitForServiceAvailability(ClusterService.SELF_LINK);
        waitForServiceAvailability(ContainerHostService.SELF_LINK);
        waitForServiceAvailability(MockPKSAdapterService.SELF_LINK);
        waitForServiceAvailability(MockKubernetesHostAdapterService.SELF_LINK);
        waitForServiceAvailability(AuthCredentialsService.FACTORY_LINK);
        waitForServiceAvailability(PKSClusterConfigService.SELF_LINK);

        sender = host.getTestRequestSender();
    }

    @Test
    public void testAddPKSCluster() {
        String endpointLink = createEndpoint().documentSelfLink;

        AddClusterRequest request = new AddClusterRequest();
        request.endpointLink = endpointLink;
        request.cluster = createPKSClusterObject();

        Operation o = Operation
                .createPost(host, PKSClusterConfigService.SELF_LINK)
                .addRequestHeader(PROJECT_ADMIRAL_HEADER, PROJECT_LINK)
                .setBodyNoCloning(request);
        ClusterDto pksCluster = sender.sendAndWait(o, ClusterDto.class);

        assertNotNull(pksCluster);
        assertEquals(CLUSTER_NAME, pksCluster.name);
        assertEquals(String.format("https://%s:%s", CLUSTER_HOSTNAME, CLUSTER_PORT),
                pksCluster.address);

        ComputeState pksHost = pksCluster.nodes.values().iterator().next();
        assertNotNull(pksHost);
        assertNotNull(pksHost.customProperties.get(HOST_AUTH_CREDENTIALS_PROP_NAME));
        assertThat(Arrays.asList(PROJECT_LINK), is(pksHost.tenantLinks));
        assertEquals(endpointLink, pksHost.customProperties.get(PKS_ENDPOINT_PROP_NAME));
        assertEquals(PLAN_NAME, pksHost.customProperties.get(PKS_CLUSTER_PLAN_NAME_PROP_NAME));
        assertEquals(CLUSTER_UUID, pksHost.customProperties.get(PKS_CLUSTER_UUID_PROP_NAME));
    }

    @Test
    public void testAddPKSClusterFail() {
        String endpointLink = createEndpoint().documentSelfLink;

        AddClusterRequest request = new AddClusterRequest();
        request.endpointLink = endpointLink;

        Operation o = Operation
                .createPost(host, PKSClusterConfigService.SELF_LINK)
                .setBodyNoCloning(request);
        FailureResponse failureResponse = sender.sendAndWaitFailure(o);

        assertNotNull(failureResponse);
        assertNotNull(failureResponse.failure);
    }

    @Test
    public void testAddClusterRequestGetExternalAddress() {
        AddClusterRequest request = new AddClusterRequest();
        request.cluster = new PKSCluster();

        String s = request.getExternalAddress();
        assertNull(s);

        request.cluster.parameters = new HashMap<>();
        s = request.getExternalAddress();
        assertNull(s);

        request.cluster.parameters.put(PKS_MASTER_HOST_FIELD, "host");
        s = request.getExternalAddress();
        assertNotNull(s);
    }

    private Endpoint createEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";

        Operation o = Operation
                .createPost(host, PKSEndpointFactoryService.SELF_LINK)
                .setBodyNoCloning(endpoint);
        Endpoint result = sender.sendAndWait(o, Endpoint.class);

        return result;
    }

    private PKSCluster createPKSClusterObject() {
        PKSCluster cluster = new PKSCluster();
        cluster.name = CLUSTER_NAME;
        cluster.uuid = CLUSTER_UUID;
        cluster.planName = PLAN_NAME;
        cluster.parameters = new HashMap<>();
        cluster.parameters.put("kubernetes_master_host", CLUSTER_HOSTNAME);
        cluster.parameters.put("kubernetes_master_port", CLUSTER_PORT);

        return cluster;
    }

}
