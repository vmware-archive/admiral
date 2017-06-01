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

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;


import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.util.TestRequestStateFactory;
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
    }
}
