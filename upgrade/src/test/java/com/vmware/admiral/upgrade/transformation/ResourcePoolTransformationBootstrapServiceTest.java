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

package com.vmware.admiral.upgrade.transformation;

import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.upgrade.UpgradeBaseTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState.ResourcePoolProperty;
import com.vmware.xenon.services.common.QueryTask.Query;

public class ResourcePoolTransformationBootstrapServiceTest extends UpgradeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
    }

    @Test
    public void testResourcePoolQueryShouldBeUpdatedSinglePool() throws Throwable {
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.properties = EnumSet.of(ResourcePoolProperty.ELASTIC);
        poolState.name = "test-pool";
        Query vmGuest = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.VM_GUEST.name()).build();
        Query kind = Query.Builder.create().addKindFieldClause(ComputeState.class).build();

        poolState.query = Query.Builder.create().addClauses(vmGuest, kind).build();

        poolState = doPost(poolState, ResourcePoolService.FACTORY_LINK);

        String poolSelfLink = poolState.documentSelfLink;
        poolState = getDocument(ResourcePoolState.class, poolSelfLink);
        assertTrue(poolState.query.booleanClauses.size() == 2);

        host.registerForServiceAvailability(
                ResourcePoolTransformationBootstrapService.startTask(host),
                true,
                ResourcePoolTransformationBootstrapService.FACTORY_LINK);

        waitFor(() -> {
            ResourcePoolState state = getDocument(ResourcePoolState.class, poolSelfLink);
            return state.query.booleanClauses.size() == 1
                    && state.query.booleanClauses.get(0).booleanClauses.get(0).term.propertyName
                            .equals("documentKind");
        });
    }

    @Test
    public void testResourcePoolQueryShouldBeUpdatedMultiplePools()
            throws Throwable {
        ResourcePoolState firstPool = new ResourcePoolState();
        firstPool.properties = EnumSet.of(ResourcePoolProperty.ELASTIC);
        firstPool.name = "test-pool";
        Query vmGuest = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.VM_GUEST.name()).build();
        Query kind = Query.Builder.create().addKindFieldClause(ComputeState.class).build();

        firstPool.query = Query.Builder.create().addClauses(vmGuest, kind).build();

        firstPool = doPost(firstPool, ResourcePoolService.FACTORY_LINK);

        String firstSelfLink = firstPool.documentSelfLink;
        firstPool = getDocument(ResourcePoolState.class, firstSelfLink);
        assertTrue(firstPool.query.booleanClauses.size() == 2);

        ResourcePoolState secondPool = new ResourcePoolState();
        secondPool.name = "test-pool2";

        secondPool = doPost(secondPool, ResourcePoolService.FACTORY_LINK);

        String secondSelfLink = secondPool.documentSelfLink;
        secondPool = getDocument(ResourcePoolState.class, secondSelfLink);
        assertTrue(secondPool.query.booleanClauses.size() == 2);

        host.registerForServiceAvailability(
                ResourcePoolTransformationBootstrapService.startTask(host),
                true,
                ResourcePoolTransformationBootstrapService.FACTORY_LINK);

        waitFor(() -> {
            ResourcePoolState state = getDocument(ResourcePoolState.class, firstSelfLink);
            return state.query.booleanClauses.size() == 1
                    && state.query.booleanClauses.get(0).booleanClauses.get(0).term.propertyName
                            .equals("documentKind");
        });

        // non elastic pool query should not be changed
        waitFor(() -> {
            ResourcePoolState state = getDocument(ResourcePoolState.class, secondSelfLink);
            return state.query.booleanClauses.size() == 2;
        });
    }
}