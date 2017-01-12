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

package com.vmware.admiral.host;

import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.PlacementZoneConstants;
import com.vmware.admiral.compute.PlacementZoneConstants.PlacementZoneType;
import com.vmware.admiral.host.ResourcePoolOperationProcessingChain;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

public class ResourcePoolOperationProcessingChainTest extends BaseTestCase {

    private static final String TAG_LINKS_MUST_BE_EMPTY_MESSAGE = String
            .format(AssertUtil.PROPERTY_MUST_BE_EMPTY_MESSAGE_FORMAT, "tagLinks");
    private static final String DEFAULT_TEST_PLACEMENT_ZONE_NAME = "default-test-placement-zone";
    private static final String PLACEMENT_ZONE_TAG_LINK = "test-tag-link";

    private ResourcePoolState defaultPlacementZone;

    @Before
    public void setUp() throws Throwable {

        // start services
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, true);

        // wait for needed services
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);

        defaultPlacementZone = createPlacementZone(DEFAULT_TEST_PLACEMENT_ZONE_NAME, false);
    }


    @Override
    protected void customizeChains(
            Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains) {
        super.customizeChains(chains);
        chains.put(ResourcePoolService.class, ResourcePoolOperationProcessingChain.class);
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
                            verifyExceptionMessage(e.getMessage(),
                                    ResourcePoolOperationProcessingChain.PLACEMENT_ZONE_IN_USE_MESSAGE);
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

    @Test
    public void testCreateDockerPlacementZoneWithTagsShouldPass() throws Throwable {
        ResourcePoolState resourcePoolState = createResourcePoolState(
                "docker-placement-zone-with-tags", false);
        addTags(resourcePoolState);
        ResourcePoolState placementZone = createPlacementZone(resourcePoolState);
        assertNotNull("Created placement zone cannot be null", placementZone);
    }

    @Test
    public void testCreateSchedulerPlacementZoneWithTagsShouldFail() throws Throwable {
        ResourcePoolState resourcePoolState = createResourcePoolState(
                "scheduler-placement-zone-with-tags", true);
        addTags(resourcePoolState);
        try {
            createPlacementZone(resourcePoolState);
            Assert.fail("Should fail to create a scheduler placement zone with tags");
        } catch (LocalizableValidationException ex) {
            verifyExceptionMessage(ex.getMessage(), TAG_LINKS_MUST_BE_EMPTY_MESSAGE);
        }
    }

    @Test
    public void testPutDockerPlacementZoneWithTagsShouldPass() throws Throwable {
        // First create a docker placement zone.
        ResourcePoolState createdPlacementZone = createPlacementZone("docker-placement-zone",
                false);
        assertNotNull(createdPlacementZone);

        // Now update the created state with tags and PUT it. This should pass
        addTags(createdPlacementZone);
        ResourcePoolState putState = doPut(createdPlacementZone);
        assertNotNull(putState);
    }

    @Test
    public void testPutSchedulerPlacementZoneWithTagsShouldFail() throws Throwable {
        // First create a scheduler placement zone.
        ResourcePoolState createdPlacementZone = createPlacementZone("scheduler-placement-zone",
                true);
        assertNotNull(createdPlacementZone);

        // Now update the created state with tags and PUT it. This should fail
        addTags(createdPlacementZone);
        try {
            doPut(createdPlacementZone);
            Assert.fail("PUT should fail for scheduler placement zone with tags");
        } catch (LocalizableValidationException ex) {
            verifyExceptionMessage(ex.getMessage(), TAG_LINKS_MUST_BE_EMPTY_MESSAGE);
        }
    }

    @Test
    public void testUpdateDockerPlacementZoneWithTagsShouldPass() throws Throwable {
        // First create a scheduler placement zone.
        ResourcePoolState createdPlacementZone = createPlacementZone("docker-placement-zone",
                false);
        assertNotNull(createdPlacementZone);

        // Now create a PATCH with tags. This should pass
        ResourcePoolState patchState = new ResourcePoolState();
        addTags(patchState);
        ResourcePoolState patchedState = doPatch(patchState, createdPlacementZone.documentSelfLink);
        assertNotNull(patchedState);
    }

    @Test
    public void testUpdateSchedulerPlacementZoneWithTagsShouldFail() throws Throwable {
        // First create a scheduler placement zone.
        ResourcePoolState createdPlacementZone = createPlacementZone("docker-placement-zone", true);
        assertNotNull(createdPlacementZone);

        // Now create a PATCH with tags. This should fail
        ResourcePoolState patchState = new ResourcePoolState();
        addTags(patchState);
        try {
            doPatch(patchState, createdPlacementZone.documentSelfLink);
            Assert.fail("PATCH should fail to set tags for scheduler placement zone");
        } catch (LocalizableValidationException ex) {
            verifyExceptionMessage(ex.getMessage(), TAG_LINKS_MUST_BE_EMPTY_MESSAGE);
        }
    }

    @Test
    public void testUpdateDockerPZToSchedulerPZWithTagsShouldFail() throws Throwable {
        // First create a scheduler placement zone.
        ResourcePoolState createdPlacementZone = createPlacementZone(
                "docker-placement-zone-with-tags", false);
        assertNotNull(createdPlacementZone);

        // Now create a PATCH with tags. This should fail
        ResourcePoolState patchState = new ResourcePoolState();
        markSchedulerPlacementZone(patchState);
        addTags(patchState);
        try {
            doPatch(patchState, createdPlacementZone.documentSelfLink);
            Assert.fail("PATCH should fail to set tags for scheduler placement zone");
        } catch (LocalizableValidationException ex) {
            verifyExceptionMessage(ex.getMessage(), TAG_LINKS_MUST_BE_EMPTY_MESSAGE);
        }
    }

    @Test
    public void testUpdateEmptyDockerPZToSchedulerPZShouldPass() throws Throwable {
        // First create a docker placement zone.
        ResourcePoolState createdPlacementZone = createPlacementZone("docker-placement-zone", false);
        assertNotNull(createdPlacementZone);

        // Now create a PATCH that updates its type to scheduler. This should pass
        ResourcePoolState patchState = new ResourcePoolState();
        markSchedulerPlacementZone(patchState);
        ResourcePoolState patchedState = doPatch(patchState, createdPlacementZone.documentSelfLink);
        assertNotNull(patchedState);
    }

    @Test
    public void testUpdateEmptySchedulerPZToDockerPZShouldPass() throws Throwable {
        // First create a docker placement zone.
        ResourcePoolState createdPlacementZone = createPlacementZone("scheduler-placement-zone", true);
        assertNotNull(createdPlacementZone);

        // Now create a PATCH that updates its type to docker. This should pass
        ResourcePoolState patchState = new ResourcePoolState();
        markDockerPlacementZone(patchState);
        ResourcePoolState patchedState = doPatch(patchState, createdPlacementZone.documentSelfLink);
        assertNotNull(patchedState);
    }

    @Test
    public void testUpdateDockerPZInUseToSchedulerPZShouldFail() throws Throwable {
        // First create a docker placement zone.
        ResourcePoolState createdPlacementZone = createPlacementZone("docker-placement-zone",
                false);
        assertNotNull(createdPlacementZone);

        // Now create a compute state that uses this placement zone
        createComputeState(createdPlacementZone);

        // Now create a PATCH that updates the type of the zone to scheduler. This should fail
        ResourcePoolState patchState = new ResourcePoolState();
        markSchedulerPlacementZone(patchState);
        try {
            doPatch(patchState, createdPlacementZone.documentSelfLink);
            Assert.fail(
                    "PATCH should fail to update the type of a used "
                    + "docker placement zone to a scheduler zone");
        } catch (IllegalStateException ex) {
            verifyExceptionMessage(ex.getMessage(),
                    ResourcePoolOperationProcessingChain.NON_SCHEDULER_HOST_IN_PLACEMENT_ZONE_MESSAGE);
        }
    }

    @Test
    public void testUpdateSchedulerPZInUseToDockerPZShouldFail() throws Throwable {
        // First create a docker placement zone.
        ResourcePoolState createdPlacementZone = createPlacementZone("scheduler-placement-zone",
                true);
        assertNotNull(createdPlacementZone);

        // Now create a compute state that uses this placement zone
        createComputeState(createdPlacementZone, true);

        // Now create a PATCH that updates the type of the zone to docker. This should fail
        ResourcePoolState patchState = new ResourcePoolState();
        markDockerPlacementZone(patchState);
        try {
            doPatch(patchState, createdPlacementZone.documentSelfLink);
            Assert.fail(
                    "PATCH should fail to update the type of a used "
                    + "scheduler placement zone to a docker zone");
        } catch (IllegalStateException ex) {
            verifyExceptionMessage(ex.getMessage(),
                    ResourcePoolOperationProcessingChain.SCHEDULER_HOSTS_IN_PLACEMENT_ZONE_MESSAGE);
        }
    }

    @Test
    public void testUpdateDockerPZInUseByMultipleVicHostsToSchedulerPZShouldPass() throws Throwable {
        // First create a docker placement zone.
        ResourcePoolState createdPlacementZone = createPlacementZone("docker-placement-zone",
                false);
        assertNotNull(createdPlacementZone);

        // Now create 2 VIC hosts that use this placement zone. This can be achieved if the VIC
        // hosts were initially declared as docker but were later on marked as VIC
        createComputeState(createdPlacementZone);
        createComputeState(createdPlacementZone);

        // Now create a PATCH that updates the type of the zone to scheduler. This should fail
        // because there will be multiple container in the placement zone (even if they are all
        // schedulers)
        ResourcePoolState patchState = new ResourcePoolState();
        markSchedulerPlacementZone(patchState);
        try {
            doPatch(patchState, createdPlacementZone.documentSelfLink);
            Assert.fail(
                    "PATCH should fail to update the type of a docker placement "
                    + "zone to a scheduler zone when the placement zone is in use by multiple scheduler hosts");
        } catch (IllegalStateException ex) {
            verifyExceptionMessage(ex.getMessage(),
                    ResourcePoolOperationProcessingChain.MULTIPLE_HOSTS_IN_PLACEMENT_ZONE_MESSAGE);
        }
    }

    private void addTags(ResourcePoolState resourcePoolState) {
        if (resourcePoolState.tagLinks != null && !resourcePoolState.tagLinks.isEmpty()) {
            return;
        }

        if (resourcePoolState.tagLinks == null) {
            resourcePoolState.tagLinks = new HashSet<>();
        }

        resourcePoolState.tagLinks.add(PLACEMENT_ZONE_TAG_LINK);
    }

    private void markSchedulerPlacementZone(ResourcePoolState resourcePool) {
        if (resourcePool.customProperties == null) {
            resourcePool.customProperties = new HashMap<>();
        }
        resourcePool.customProperties.put(
                PlacementZoneConstants.PLACEMENT_ZONE_TYPE_CUSTOM_PROP_NAME,
                PlacementZoneType.SCHEDULER.toString());
    }

    private void markDockerPlacementZone(ResourcePoolState resourcePool) {
        if (resourcePool.customProperties == null) {
            resourcePool.customProperties = new HashMap<>();
        }
        resourcePool.customProperties.put(
                PlacementZoneConstants.PLACEMENT_ZONE_TYPE_CUSTOM_PROP_NAME,
                PlacementZoneType.DOCKER.toString());
    }

    private ResourcePoolState createResourcePoolState(String placementZoneName,
            boolean isSchedulerZone) {
        assertNotNull(placementZoneName);


        ResourcePoolState placementZone = new ResourcePoolState();
        placementZone.id = placementZoneName;
        placementZone.name = placementZoneName;
        placementZone.documentSelfLink = ResourcePoolService.FACTORY_LINK + "/" + placementZone.id;

        if (isSchedulerZone) {
            markSchedulerPlacementZone(placementZone);
        } else {
            markDockerPlacementZone(placementZone);
        }

        return placementZone;
    }

    private ResourcePoolState createPlacementZone(String placementZoneName, boolean isSchedulerZone)
            throws Throwable {
        ResourcePoolState resourcePoolState = createResourcePoolState(placementZoneName,
                isSchedulerZone);
        return createPlacementZone(resourcePoolState);
    }

    private ResourcePoolState createPlacementZone(ResourcePoolState resourcePoolState)
            throws Throwable {
        return doPost(resourcePoolState, ResourcePoolService.FACTORY_LINK);
    }

    private ComputeState createComputeState(ResourcePoolState placementZone) throws Throwable {
        return createComputeState(placementZone, false);
    }

    private ComputeState createComputeState(ResourcePoolState placementZone, boolean isScheduler)
            throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.address = "no-address";
        computeState.descriptionLink = "no-description-link";
        if (placementZone != null) {
            computeState.resourcePoolLink = placementZone.documentSelfLink;
        } else {
            computeState.resourcePoolLink = defaultPlacementZone.documentSelfLink;
        }
        computeState.customProperties = new HashMap<>();
        if (isScheduler) {
            computeState.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                    ContainerHostType.VIC.toString());
        } else {
            computeState.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                    ContainerHostType.DOCKER.toString());
        }

        return doPost(computeState, ComputeService.FACTORY_LINK);
    }

    private void verifyExceptionMessage(String message, String expected) {
        if (!message.equals(expected)) {
            String errorMessage = String.format("Expected error '%s' but was '%s'", expected,
                    message);
            throw new IllegalStateException(errorMessage);
        }
    }
}
