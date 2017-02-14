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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.endpoint.EndpointAdapterService;
import com.vmware.admiral.compute.env.ComputeProfileService;
import com.vmware.admiral.compute.env.EnvironmentService;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentState;
import com.vmware.admiral.compute.env.NetworkProfileService;
import com.vmware.admiral.compute.env.StorageProfileService;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.EnvironmentQueryUtils.EnvEntry;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class EnvironmentQueryUtilsTest extends RequestBaseTest {

    private Map<String, ResourcePoolState> pools;
    private URI referer;

    @Override
    @Before
    public void setUp() throws Throwable {
        startServices(host);
        createEndpoint();
        waitForServiceAvailability(host, ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK);
        referer = UriUtils.buildUri(host, EnvironmentQueryUtilsTest.class.getSimpleName());
    }

    @Test
    public void testSuccessBehaviour() throws Throwable {
        pools = createResourcePools();
        createEnvironments();

        TestContext ctx = testCreate(1);
        List<EnvEntry> entries = new ArrayList<>();
        EnvironmentQueryUtils.queryEnvironments(host, referer, pools.keySet(),
                endpoint.documentSelfLink, null, null,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    entries.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(entries.isEmpty());
        assertEquals(2, entries.size());
        entries.forEach(e -> {
            assertEquals(endpoint.documentSelfLink, e.endpoint.documentSelfLink);
            assertEquals(EndpointType.aws.name(), e.endpoint.endpointType);
            assertNotNull(e.envLinks);
            assertEquals(4, e.envLinks.size());
        });
    }

    @Test
    public void testPoolWithNoEndpoint() throws Throwable {
        ResourcePoolState pool = doCreateResourcePool(null, null);
        Set<String> pools = new HashSet<>();
        pools.add(pool.documentSelfLink);

        TestContext ctx = testCreate(1);
        List<EnvEntry> entries = new ArrayList<>();
        EnvironmentQueryUtils.queryEnvironments(host, referer, pools, endpoint.documentSelfLink,
                null, null,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    entries.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertTrue(entries.isEmpty());
    }

    @Test
    public void testPoolWithNoEnvMapping() throws Throwable {
        String vsphereEnvLink = UriUtils.buildUriPath(EnvironmentService.FACTORY_LINK,
                EndpointType.vsphere.name());

        doDelete(UriUtils.buildUri(host, vsphereEnvLink), false);
        EnvironmentState state = getDocumentNoWait(EnvironmentState.class, vsphereEnvLink);
        assertNull(state);

        EndpointState endpoint = TestRequestStateFactory
                .createEndpoint(UUID.randomUUID().toString(), EndpointType.vsphere);
        endpoint = doPost(endpoint, EndpointAdapterService.SELF_LINK);

        ResourcePoolState pool = doCreateResourcePool(endpoint.documentSelfLink, null);
        Set<String> pools = new HashSet<>();
        pools.add(pool.documentSelfLink);

        TestContext ctx = testCreate(1);
        List<EnvEntry> entries = new ArrayList<>();
        EnvironmentQueryUtils.queryEnvironments(host, referer, pools, endpoint.documentSelfLink,
                null, null,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    entries.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertTrue(entries.isEmpty());
    }

    @Test
    public void testFilterEnvByNetworkEnvConstraints() throws Throwable {
        pools = createResourcePools();
        Map<String, EnvironmentState> envs = createEnvironments();
        List<String> environmentLinks = new ArrayList<>(1);
        EnvironmentState awsEnv = envs.entrySet()
                .stream()
                .filter(e -> e.getValue().endpointType == EndpointType.aws.name())
                .findAny()
                .orElse(null).getValue();
        environmentLinks.add(awsEnv.documentSelfLink);

        TestContext ctx = testCreate(1);
        List<EnvEntry> entries = new ArrayList<>();
        EnvironmentQueryUtils.queryEnvironments(host, referer, pools.keySet(),
                endpoint.documentSelfLink, null, environmentLinks,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    entries.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(entries.isEmpty());
        assertEquals(2, entries.size());
        entries.forEach(e -> {
            assertEquals(endpoint.documentSelfLink, e.endpoint.documentSelfLink);
            assertEquals(EndpointType.aws.name(), e.endpoint.endpointType);
            assertNotNull(e.envLinks);
            assertEquals(1, e.envLinks.size());
            assertEquals(awsEnv.documentSelfLink, e.envLinks.iterator().next());
        });
    }

    private Map<String, ResourcePoolState> createResourcePools() throws Throwable {
        Map<String, ResourcePoolState> pools = new HashMap<>();
        for (int i = 0; i < 2; i++) {
            ResourcePoolState pool = doCreateResourcePool(endpoint.documentSelfLink, null);
            pools.put(pool.documentSelfLink, pool);
        }
        ResourcePoolState pool = doCreateResourcePool(null, null);
        pools.put(pool.documentSelfLink, pool);
        return pools;
    }

    private ResourcePoolState doCreateResourcePool(String endpointLink, List<String> tenantLinks)
            throws Throwable {
        ResourcePoolState pool = TestRequestStateFactory
                .createResourcePool(UUID.randomUUID().toString(), endpointLink);
        pool.tenantLinks = tenantLinks;
        pool = doPost(pool, ResourcePoolService.FACTORY_LINK);
        return pool;
    }

    private Map<String, EnvironmentState> createEnvironments() throws Throwable {
        Map<String, EnvironmentState> envs = new HashMap<>();
        for (int i = 0; i < 2; i++) {
            EnvironmentState env = doCreateEnvironmentState(endpoint.documentSelfLink, null);
            envs.put(env.documentSelfLink, env);
        }

        EnvironmentState awsEnv = doCreateEnvironmentState(null, EndpointType.aws);
        envs.put(awsEnv.documentSelfLink, awsEnv);

        EnvironmentState vsphereEnv = doCreateEnvironmentState(null, EndpointType.vsphere);
        envs.put(vsphereEnv.documentSelfLink, vsphereEnv);

        return envs;
    }

    private EnvironmentState doCreateEnvironmentState(String endpointLink,
            EndpointType endpointType) throws Throwable {
        EnvironmentState state = createEnvironmentState(endpointLink, endpointType);
        return doPost(state, EnvironmentService.FACTORY_LINK);
    }

    private EnvironmentState createEnvironmentState(String endpointLink,
            EndpointType endpointType) {
        EnvironmentState state = new EnvironmentState();
        state.name = UUID.randomUUID().toString();
        state.computeProfileLink = UriUtils.buildUriPath(ComputeProfileService.FACTORY_LINK,
                state.name);
        state.networkProfileLink = UriUtils.buildUriPath(NetworkProfileService.FACTORY_LINK,
                state.name);
        state.storageProfileLink = UriUtils.buildUriPath(StorageProfileService.FACTORY_LINK,
                state.name);
        if (endpointLink != null) {
            state.endpointLink = endpointLink;
        } else if (endpointType != null) {
            state.endpointType = endpointType.name();
        }
        return state;
    }
}
