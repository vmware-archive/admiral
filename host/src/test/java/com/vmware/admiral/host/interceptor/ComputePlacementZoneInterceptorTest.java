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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CompletionException;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.compute.PlacementZoneConstants;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStateMapUpdateRequest;

/**
 * Tests for the {@link ComputePlacementZoneInterceptor} class.
 */
public class ComputePlacementZoneInterceptorTest extends BaseTestCase {

    private static final String PLACEMENT_ZONE_NAME = "pz-name";

    @Before
    public void setUp() throws Throwable {

        // start services
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, true);

        // wait for needed services
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
    }

    @Override
    protected void registerInterceptors(OperationInterceptorRegistry registry) {
        ComputePlacementZoneInterceptor.register(registry);
    }

    @Test
    public void testSuccessfulCreation() throws Throwable {
        ResourcePoolState rp = createComputePlacementZone(PLACEMENT_ZONE_NAME,
                "/endpoint/link");
        ResourcePoolState returnedState = doPost(rp, ResourcePoolService.FACTORY_LINK);
        assertNotNull(returnedState);
    }

    @Test
    public void testSuccessfulPatch() throws Throwable {
        ResourcePoolState rp = createComputePlacementZone(PLACEMENT_ZONE_NAME,
                "/endpoint/link");
        ResourcePoolState returnedState = doPost(rp, ResourcePoolService.FACTORY_LINK);

        ResourcePoolState patchBody = new ResourcePoolState();
        patchBody.customProperties = new HashMap<>();
        patchBody.customProperties.put(ComputeProperties.ENDPOINT_LINK_PROP_NAME, "/ednpoint/link2");
        returnedState = doPatch(patchBody, returnedState.documentSelfLink);
        assertEquals("/ednpoint/link2",
                returnedState.customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME));
    }

    @Test
    public void testCreatePzWithoutEndpoint() throws Throwable {
        ResourcePoolState rp = createComputePlacementZone(PLACEMENT_ZONE_NAME, null);
        Operation createOp = Operation
                .createPost(this.host, ResourcePoolService.FACTORY_LINK)
                .setBody(rp)
                .setCompletion(this::errorVerificationCompletion);
        host.testStart(1);
        host.send(createOp);
        host.testWait();
    }

    @Test
    public void testPatchCleanEndpoint() throws Throwable {
        ResourcePoolState rp = createComputePlacementZone(PLACEMENT_ZONE_NAME,
                "/endpoint/link");
        ResourcePoolState returnedState = doPost(rp, ResourcePoolService.FACTORY_LINK);

        ServiceStateMapUpdateRequest patchBody = ServiceStateMapUpdateRequest.create(
                null,
                Collections.singletonMap(
                        ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        Collections.singletonList(
                                (Object) ComputeProperties.ENDPOINT_LINK_PROP_NAME)));

        Operation patchOp = Operation
                .createPatch(this.host, returnedState.documentSelfLink)
                .setBody(patchBody)
                .setCompletion(this::errorVerificationCompletion);
        host.testStart(1);
        host.send(patchOp);
        host.testWait();
    }

    @Test
    public void testPutCleanEndpoint() throws Throwable {
        ResourcePoolState rp = createComputePlacementZone(PLACEMENT_ZONE_NAME,
                "/endpoint/link");
        ResourcePoolState returnedState = doPost(rp, ResourcePoolService.FACTORY_LINK);

        returnedState.customProperties.remove(ComputeProperties.ENDPOINT_LINK_PROP_NAME);

        Operation patchOp = Operation
                .createPut(this.host, returnedState.documentSelfLink)
                .setBody(returnedState)
                .setCompletion(this::errorVerificationCompletion);
        host.testStart(1);
        host.send(patchOp);
        host.testWait();
    }

    private ResourcePoolState createComputePlacementZone(String placementZoneName,
            String endpointLink) throws Throwable {
        assertNotNull(placementZoneName);

        ResourcePoolState placementZone = new ResourcePoolState();
        placementZone.id = placementZoneName;
        placementZone.name = placementZoneName;
        placementZone.documentSelfLink = ResourcePoolService.FACTORY_LINK + "/" + placementZone.id;
        placementZone.customProperties = new HashMap<>();
        placementZone.customProperties.put(PlacementZoneConstants.RESOURCE_TYPE_CUSTOM_PROP_NAME,
                ResourceType.COMPUTE_TYPE.getName());
        if (endpointLink != null) {
            placementZone.customProperties.put(ComputeProperties.ENDPOINT_LINK_PROP_NAME,
                    endpointLink);
        }

        return placementZone;
    }

    private void errorVerificationCompletion(Operation o, Throwable e) {
        if (e != null) {
            try {
                verifyExceptionMessage(e,
                        ComputePlacementZoneInterceptor.ENDPOINT_REQUIRED_FOR_PLACEMENT_ZONE_MESSAGE);
                host.completeIteration();
            } catch (IllegalStateException ex) {
                host.failIteration(ex);
            }
        } else {
            host.failIteration(new IllegalStateException("Expected a failure"));
        }
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
