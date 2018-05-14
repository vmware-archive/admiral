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

package com.vmware.admiral.compute.kubernetes.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.admiral.compute.kubernetes.entities.common.ObjectMeta;
import com.vmware.admiral.compute.kubernetes.service.GenericKubernetesEntityService.GenericKubernetesEntityState;
import com.vmware.xenon.common.FactoryService;

public class GenericKubernetesEntityServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(GenericKubernetesEntityService.FACTORY_LINK);
    }

    @Test
    public void testGenericKubernetesEntityServices() throws Throwable {
        verifyService(
                FactoryService.create(GenericKubernetesEntityService.class),
                GenericKubernetesEntityState.class,
                (prefix, index) -> {
                    GenericKubernetesEntityState entityState = new GenericKubernetesEntityState();
                    entityState.name = prefix + "name" + index;
                    entityState.entity = new BaseKubernetesObject();
                    entityState.entity.apiVersion = "v1";
                    entityState.entity.kind = "Pod";
                    entityState.entity.metadata = new ObjectMeta();
                    entityState.entity.metadata.name = entityState.name;

                    return entityState;
                },
                (prefix, serviceDocument) -> {
                    GenericKubernetesEntityState entityState = (GenericKubernetesEntityState) serviceDocument;
                    assertNotNull(entityState);
                    assertTrue(entityState.name.startsWith(prefix + "name"));
                    assertNotNull(entityState.entity);
                    assertEquals("v1", entityState.entity.apiVersion);
                    assertEquals("Pod", entityState.entity.kind);
                    assertNotNull(entityState.entity.metadata);
                    assertEquals(entityState.name, entityState.entity.metadata.name);
                });
    }
}
