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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;


import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class ResourceGroupUtilsTest extends RequestBaseTest {
    private URI referer;

    @Override
    @Before
    public void setUp() throws Throwable {
        startServices(host);
        waitForServiceAvailability(host, ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK);
        referer = UriUtils.buildUri(host, ResourceGroupUtilsTest.class.getSimpleName());
    }

    @Test
    public void testCreateNewResourceGroup() throws Throwable {
        String contextId = UUID.randomUUID().toString();
        List<String> tenantLinks = TestRequestStateFactory.getTenantLinks();

        TestContext ctx = testCreate(1);
        AtomicReference<ResourceGroupState> resGroupRef = new AtomicReference<>();
        ResourceGroupUtils.createResourceGroup(host, referer, contextId,
                tenantLinks)
                .whenComplete((rg, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    resGroupRef.set(rg);
                    ctx.complete();
                });
        ctx.await();

        ResourceGroupState resourceGroup = resGroupRef.get();
        assertNotNull(resourceGroup);
        assertEquals(resourceGroup.name, contextId);
        assertEquals(resourceGroup.tenantLinks, tenantLinks);
        assertNotNull(resourceGroup.customProperties);
        assertEquals(resourceGroup.customProperties.get(ComputeProperties.RESOURCE_TYPE_KEY),
                ResourceGroupUtils.COMPUTE_DEPLOYMENT_TYPE_VALUE);
    }

    @Test
    public void testCreateExistingResourceGroup() throws Throwable {
        String contextId = UUID.randomUUID().toString();
        List<String> tenantLinks = TestRequestStateFactory.getTenantLinks();
        ResourceGroupState resGroup = createResourceGroup(contextId, tenantLinks);

        TestContext ctx = testCreate(1);
        AtomicReference<ResourceGroupState> resGroupRef = new AtomicReference<>();
        ResourceGroupUtils.createResourceGroup(host, referer, contextId,
                tenantLinks)
                .whenComplete((rg, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    resGroupRef.set(rg);
                    ctx.complete();
                });
        ctx.await();

        ResourceGroupState resourceGroup = resGroupRef.get();
        assertNotNull(resourceGroup);
        assertEquals(resourceGroup.documentSelfLink, resGroup.documentSelfLink);
        assertEquals(resourceGroup.name, contextId);
        assertEquals(resourceGroup.tenantLinks, tenantLinks);
        assertNotNull(resourceGroup.customProperties);
        assertEquals(resourceGroup.customProperties.get(ComputeProperties.RESOURCE_TYPE_KEY),
                ResourceGroupUtils.COMPUTE_DEPLOYMENT_TYPE_VALUE);
    }

    @Test
    public void testUpdateDeploymentResourceGroup() throws Throwable {
        String contextId = UUID.randomUUID().toString();
        List<String> tenantLinks = TestRequestStateFactory.getTenantLinks();

        TestContext ctx1 = testCreate(1);
        AtomicReference<ResourceGroupState> resGroupRef = new AtomicReference<>();
        ResourceGroupUtils.createResourceGroup(host, referer, contextId,
                tenantLinks)
                .whenComplete((rg, e) -> {
                    if (e != null) {
                        ctx1.fail(e);
                        return;
                    }
                    resGroupRef.set(rg);
                    ctx1.complete();
                });
        ctx1.await();

        ResourceGroupState resourceGroup = resGroupRef.get();
        assertNotNull(resourceGroup);
        assertEquals(resourceGroup.name, contextId);
        assertEquals(resourceGroup.tenantLinks, tenantLinks);
        assertNotNull(resourceGroup.customProperties);
        assertEquals(resourceGroup.customProperties.get(ComputeProperties.RESOURCE_TYPE_KEY),
                ResourceGroupUtils.COMPUTE_DEPLOYMENT_TYPE_VALUE);
        assertNull(resourceGroup.customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME));


        TestContext ctx2 = testCreate(1);
        ResourceGroupState resourceGroupState = new ResourceGroupState();
        resourceGroupState.customProperties = new HashMap<>();
        resourceGroupState.customProperties.put(
                ComputeProperties.ENDPOINT_LINK_PROP_NAME,
                "endpoint-link");

        Set<String> groupLinks = new HashSet<>();
        groupLinks.add(resourceGroup.documentSelfLink);
        groupLinks.add("some-random-group-link");
        ResourceGroupUtils.updateDeploymentResourceGroup(host, referer, resourceGroupState,
                groupLinks, tenantLinks)
                .whenComplete((rg, e) -> {
                    if (e != null) {
                        ctx2.fail(e);
                        return;
                    }
                    resGroupRef.set(rg);
                    ctx2.complete();
                });
        ctx2.await();

        resourceGroup = resGroupRef.get();
        assertNotNull(resourceGroup);
        assertEquals(resourceGroup.name, contextId);
        assertEquals(resourceGroup.tenantLinks, tenantLinks);
        assertNotNull(resourceGroup.customProperties);
        assertEquals(resourceGroup.customProperties.get(ComputeProperties.RESOURCE_TYPE_KEY),
                ResourceGroupUtils.COMPUTE_DEPLOYMENT_TYPE_VALUE);
        assertEquals(resourceGroup.customProperties.get(ComputeProperties
                .ENDPOINT_LINK_PROP_NAME), "endpoint-link");
    }
}
