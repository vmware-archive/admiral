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

package com.vmware.admiral.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType.DOCKER_CONTAINER;

import org.junit.Test;

public class ResourceTypeTest {

    @Test
    public void getName() {
        assertEquals(DOCKER_CONTAINER.toString(), ResourceType.CONTAINER_TYPE.getName());
        try {
            ResourceType.CONTAINER_LOAD_BALANCER_TYPE.getName();
            fail("CONTAINER_LOAD_BALANCER_TYPE should throw error");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }
    }

    @Test
    public void getContentType() {
        assertEquals("App.Container", ResourceType.CONTAINER_TYPE.getContentType());
        try {
            ResourceType.CONTAINER_LOAD_BALANCER_TYPE.getContentType();
            fail("CONTAINER_LOAD_BALANCER_TYPE should throw error");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }
    }

    @Test
    public void fromName() {
        ResourceType r = ResourceType.fromName("PKS_CLUSTER");
        assertSame(ResourceType.PKS_CLUSTER_TYPE, r);

        try {
            ResourceType.fromName("CONTAINER_LOAD_BALANCER");
            fail("CONTAINER_LOAD_BALANCER_TYPE should throw error");
        } catch (Exception ignored) {
        }
    }

    @Test
    public void fromContentType() {
        ResourceType r = ResourceType.fromContentType("App.Closure");
        assertSame(ResourceType.CLOSURE_TYPE, r);

        try {
            ResourceType.fromContentType("App.LoadBalancer");
            fail("CONTAINER_LOAD_BALANCER_TYPE should throw error");
        } catch (Exception ignored) {
        }
    }

    @Test
    public void getAllTypesAsString() {
        String all = ResourceType.getAllTypesAsString();
        assertEquals(-1, all.indexOf("CONTAINER_LOAD_BALANCER"));
    }

}