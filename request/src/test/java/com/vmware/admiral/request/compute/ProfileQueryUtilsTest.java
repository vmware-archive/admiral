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
import com.vmware.admiral.compute.profile.ComputeProfileService;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.ProfileQueryUtils.ProfileEntry;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class ProfileQueryUtilsTest extends RequestBaseTest {

    private Map<String, ResourcePoolState> pools;
    private URI referer;

    @Override
    @Before
    public void setUp() throws Throwable {
        startServices(host);
        createEndpoint();
        waitForServiceAvailability(host, ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK);
        referer = UriUtils.buildUri(host, ProfileQueryUtilsTest.class.getSimpleName());
    }

    @Test
    public void testSuccessBehaviour() throws Throwable {
        pools = createResourcePools();
        createProfiles();

        TestContext ctx = testCreate(1);
        List<ProfileEntry> entries = new ArrayList<>();
        ProfileQueryUtils.queryProfiles(host, referer, pools.keySet(),
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
            assertNotNull(e.profileLinks);
            assertEquals(4, e.profileLinks.size());
        });
    }

    @Test
    public void testPoolWithNoEndpoint() throws Throwable {
        ResourcePoolState pool = doCreateResourcePool(null, null);
        Set<String> pools = new HashSet<>();
        pools.add(pool.documentSelfLink);

        TestContext ctx = testCreate(1);
        List<ProfileEntry> entries = new ArrayList<>();
        ProfileQueryUtils.queryProfiles(host, referer, pools, endpoint.documentSelfLink,
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
    public void testPoolWithNoProfileMapping() throws Throwable {
        String vsphereProfileLink = UriUtils.buildUriPath(ProfileService.FACTORY_LINK,
                EndpointType.vsphere.name());

        doDelete(UriUtils.buildUri(host, vsphereProfileLink), false);
        ProfileState state = getDocumentNoWait(ProfileState.class, vsphereProfileLink);
        assertNull(state);

        EndpointState endpoint = TestRequestStateFactory
                .createEndpoint(UUID.randomUUID().toString(), EndpointType.vsphere);
        endpoint = doPost(endpoint, EndpointAdapterService.SELF_LINK);

        ResourcePoolState pool = doCreateResourcePool(endpoint.documentSelfLink, null);
        Set<String> pools = new HashSet<>();
        pools.add(pool.documentSelfLink);

        TestContext ctx = testCreate(1);
        List<ProfileEntry> entries = new ArrayList<>();
        ProfileQueryUtils.queryProfiles(host, referer, pools, endpoint.documentSelfLink,
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
    public void testFilterProfilesByNetworkProfileConstraints() throws Throwable {
        pools = createResourcePools();
        Map<String, ProfileState> profiles = createProfiles();
        List<String> profileLinks = new ArrayList<>(1);
        ProfileState awsProfile = profiles.entrySet()
                .stream()
                .filter(e -> e.getValue().endpointType == EndpointType.aws.name())
                .findAny()
                .orElse(null).getValue();
        profileLinks.add(awsProfile.documentSelfLink);

        TestContext ctx = testCreate(1);
        List<ProfileEntry> entries = new ArrayList<>();
        ProfileQueryUtils.queryProfiles(host, referer, pools.keySet(),
                endpoint.documentSelfLink, null, profileLinks,
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
            assertNotNull(e.profileLinks);
            assertEquals(1, e.profileLinks.size());
            assertEquals(awsProfile.documentSelfLink, e.profileLinks.iterator().next());
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

    private Map<String, ProfileState> createProfiles() throws Throwable {
        Map<String, ProfileState> profiles = new HashMap<>();
        for (int i = 0; i < 2; i++) {
            ProfileState profile = doCreateProfileState(endpoint.documentSelfLink, null);
            profiles.put(profile.documentSelfLink, profile);
        }

        ProfileState awsProfile = doCreateProfileState(null, EndpointType.aws);
        profiles.put(awsProfile.documentSelfLink, awsProfile);

        ProfileState vsphereProfile = doCreateProfileState(null, EndpointType.vsphere);
        profiles.put(vsphereProfile.documentSelfLink, vsphereProfile);

        return profiles;
    }

    private ProfileState doCreateProfileState(String endpointLink,
            EndpointType endpointType) throws Throwable {
        ProfileState state = createProfileState(endpointLink, endpointType);
        return doPost(state, ProfileService.FACTORY_LINK);
    }

    private ProfileState createProfileState(String endpointLink,
            EndpointType endpointType) {
        ProfileState state = new ProfileState();
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
