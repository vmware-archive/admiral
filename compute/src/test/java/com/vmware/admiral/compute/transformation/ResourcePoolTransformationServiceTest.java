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

package com.vmware.admiral.compute.transformation;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;

public class ResourcePoolTransformationServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolTransformationService.SELF_LINK);
        waitForServiceAvailability(ElasticPlacementZoneConfigurationService.SELF_LINK);

    }

    @Test
    public void testPlacementsBolongToDifferentPools() throws Throwable {
        ResourcePoolState pool1 = createResourcePool();
        GroupResourcePlacementState placement1 = new GroupResourcePlacementState();
        placement1.name = "placement";
        placement1.resourcePoolLink = pool1.documentSelfLink;
        placement1 = doPost(placement1, GroupResourcePlacementService.FACTORY_LINK);

        ResourcePoolState pool2 = createResourcePool();
        GroupResourcePlacementState placement2 = new GroupResourcePlacementState();
        placement2.name = "placement2";
        placement2.resourcePoolLink = pool2.documentSelfLink;
        placement2 = doPost(placement2, GroupResourcePlacementService.FACTORY_LINK);

        doPost(placement2, ResourcePoolTransformationService.SELF_LINK);
        // Verify that the pools are not changed
        Assert.assertTrue(getDocument(GroupResourcePlacementState.class,
                placement1.documentSelfLink).resourcePoolLink.equals(pool1.documentSelfLink));
        Assert.assertTrue(getDocument(GroupResourcePlacementState.class,
                placement2.documentSelfLink).resourcePoolLink.equals(pool2.documentSelfLink));
    }

    @Test
    public void testPlacementsBolongToOnePool() throws Throwable {
        ResourcePoolState pool1 = createResourcePool();
        GroupResourcePlacementState placement1 = new GroupResourcePlacementState();
        placement1.name = "placement";
        placement1.resourcePoolLink = pool1.documentSelfLink;
        placement1 = doPost(placement1, GroupResourcePlacementService.FACTORY_LINK);

        GroupResourcePlacementState placement2 = new GroupResourcePlacementState();
        placement2.name = "placement2";
        placement2.resourcePoolLink = pool1.documentSelfLink;
        placement2 = doPost(placement2, GroupResourcePlacementService.FACTORY_LINK);

        GroupResourcePlacementState placement3 = new GroupResourcePlacementState();
        placement3.name = "placement3";
        placement3.resourcePoolLink = pool1.documentSelfLink;
        placement3 = doPost(placement3, GroupResourcePlacementService.FACTORY_LINK);

        doPost(placement2, ResourcePoolTransformationService.SELF_LINK);
        // Verify that every placement point to a different pool
        placement1 = getDocument(GroupResourcePlacementState.class, placement1.documentSelfLink);
        placement2 = getDocument(GroupResourcePlacementState.class, placement2.documentSelfLink);
        placement3 = getDocument(GroupResourcePlacementState.class, placement3.documentSelfLink);
        Assert.assertFalse(placement1.resourcePoolLink.equals(placement2.resourcePoolLink));
        Assert.assertFalse(placement1.resourcePoolLink.equals(placement3.resourcePoolLink));
        Assert.assertFalse(placement2.resourcePoolLink.equals(placement3.resourcePoolLink));
    }

    private ResourcePoolState createResourcePool() throws Throwable {
        ResourcePoolState pool = new ResourcePoolState();
        pool.name = "pool";

        return doPost(pool, ResourcePoolService.FACTORY_LINK);
    }

}
