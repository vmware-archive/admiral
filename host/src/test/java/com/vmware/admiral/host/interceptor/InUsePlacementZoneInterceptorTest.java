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

package com.vmware.admiral.host.interceptor;

import static org.junit.Assert.assertNotNull;

import java.util.concurrent.CompletionException;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for the {@link InUsePlacementZoneInterceptor} class.
 */
public class InUsePlacementZoneInterceptorTest extends BaseTestCase {

    private static final String DEFAULT_TEST_PLACEMENT_ZONE_NAME = "default-test-placement-zone";

    private ResourcePoolState defaultPlacementZone;

    @Before
    public void setUp() throws Throwable {

        // start services
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, true);

        // wait for needed services
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);

        this.defaultPlacementZone = createPlacementZone(DEFAULT_TEST_PLACEMENT_ZONE_NAME);
    }

    @Override
    protected void registerInterceptors(OperationInterceptorRegistry registry) {
        InUsePlacementZoneInterceptor.register(registry);
    }

    @Test
    public void testDeleteResourcePoolInUseShouldFail() throws Throwable {
        // Create and add a host
        createComputeState(defaultPlacementZone);

        // try to delete the placement zone. This should fail
        Operation delete = Operation
                .createDelete(UriUtils.buildUri(host, defaultPlacementZone.documentSelfLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        try {
                            verifyExceptionMessage(e, InUsePlacementZoneInterceptor.PLACEMENT_ZONE_IN_USE_MESSAGE);
                            host.completeIteration();
                        } catch (IllegalStateException ex) {
                            host.failIteration(ex);
                        }
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when deleting a placement zone that is in use"));
                    }
                });
        host.testStart(1);
        host.send(delete);
        host.testWait();
    }

    private ResourcePoolState createResourcePoolState(String placementZoneName) {
        assertNotNull(placementZoneName);

        ResourcePoolState placementZone = new ResourcePoolState();
        placementZone.id = placementZoneName;
        placementZone.name = placementZoneName;
        placementZone.documentSelfLink = ResourcePoolService.FACTORY_LINK + "/" + placementZone.id;

        return placementZone;
    }

    private ResourcePoolState createPlacementZone(String placementZoneName)
            throws Throwable {
        ResourcePoolState resourcePoolState = createResourcePoolState(placementZoneName);
        return createPlacementZone(resourcePoolState);
    }

    private ResourcePoolState createPlacementZone(ResourcePoolState resourcePoolState)
            throws Throwable {
        return doPost(resourcePoolState, ResourcePoolService.FACTORY_LINK);
    }

    private ComputeState createComputeState(ResourcePoolState placementZone) throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.address = "no-address";
        computeState.descriptionLink = "no-description-link";
        if (placementZone != null) {
            computeState.resourcePoolLink = placementZone.documentSelfLink;
        } else {
            computeState.resourcePoolLink = defaultPlacementZone.documentSelfLink;
        }

        return doPost(computeState, ComputeService.FACTORY_LINK);
    }

    private void verifyExceptionMessage(Throwable e, String expected) {
        String message = e instanceof CompletionException ? e.getCause().getMessage() : e.getMessage();
        if (!message.equals(expected)) {
            String errorMessage = String.format("Expected error '%s' but was '%s'", expected,
                    message);
            throw new IllegalStateException(errorMessage);
        }
    }
}
