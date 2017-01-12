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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.DeploymentPolicyService.DeploymentPolicy;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class DeploymentPolicyServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(DeploymentPolicyService.FACTORY_LINK);
    }

    @Test
    public void testDeploymentPolicyServices() throws Throwable {
        verifyService(
                FactoryService.create(DeploymentPolicyService.class),
                DeploymentPolicy.class,
                (prefix, index) -> {
                    return createDeploymentPolicy();
                },
                (prefix, serviceDocument) -> {
                    DeploymentPolicy deploymentPolicy = (DeploymentPolicy) serviceDocument;
                    assertEquals(deploymentPolicy.name, "policy");
                    assertEquals(deploymentPolicy.description, "test policy");
                    assertNotNull(deploymentPolicy.documentSelfLink);
                });
    }

    @Test
    public void testUpdate() throws Throwable {
        DeploymentPolicy policy = createDeploymentPolicy();
        policy = doPost(policy, DeploymentPolicyService.FACTORY_LINK);

        policy.name = "new name";

        DeploymentPolicy updatedPolicy = doPut(policy);

        assertEquals(policy.name, updatedPolicy.name);
    }

    @Test
    public void testPatch() throws Throwable {
        DeploymentPolicy policy = createDeploymentPolicy();
        policy = doPost(policy, DeploymentPolicyService.FACTORY_LINK);

        policy.name = "new name";
        policy.description = "updated desc";

        doOperation(policy, UriUtils.buildUri(host, policy.documentSelfLink), false, Action.PATCH);

        DeploymentPolicy updatedPolicy = getDocument(DeploymentPolicy.class, policy.documentSelfLink);

        assertEquals(policy.name, updatedPolicy.name);
        assertEquals(policy.description, updatedPolicy.description);
    }

    @Test
    public void testDelete() throws Throwable {
        ComputeDescription computeDescription =
                doPost(new ComputeDescription(), ComputeDescriptionService.FACTORY_LINK);
        DeploymentPolicy deploymentPolicy =
                doPost(createDeploymentPolicy(), DeploymentPolicyService.FACTORY_LINK);

        ResourcePoolState resourcePool = new ResourcePoolState();
        resourcePool.name = "test-resource-pool";
        resourcePool = doPost(resourcePool, ResourcePoolService.FACTORY_LINK);

        ComputeState compute = new ComputeState();
        compute.customProperties = new HashMap<>();
        compute.customProperties.put(ContainerHostService.CUSTOM_PROPERTY_DEPLOYMENT_POLICY,
                deploymentPolicy.documentSelfLink);
        compute.descriptionLink = computeDescription.documentSelfLink;
        compute = doPost(compute, ComputeService.FACTORY_LINK);

        GroupResourcePlacementState resourcePlacement = new GroupResourcePlacementState();
        resourcePlacement.deploymentPolicyLink = deploymentPolicy.documentSelfLink;
        resourcePlacement.maxNumberInstances = 1;
        resourcePlacement.name = "test-group-resource-placement";
        resourcePlacement.resourcePoolLink = resourcePool.documentSelfLink;
        resourcePlacement = doPost(resourcePlacement, GroupResourcePlacementService.FACTORY_LINK);

        try {
            doDelete(UriUtils.buildUri(host, deploymentPolicy.documentSelfLink), true);
            fail("expect validation error during deletion");
        } catch (LocalizableValidationException e) {
            assertEquals("Deployment Policy is in use", e.getMessage());
        }
        doDelete(UriUtils.buildUri(host, compute.documentSelfLink), false);
        doDelete(UriUtils.buildUri(host, computeDescription.documentSelfLink), false);
        try {
            doDelete(UriUtils.buildUri(host, deploymentPolicy.documentSelfLink), true);
            fail("expect validation error during deletion");
        } catch (LocalizableValidationException e) {
            assertEquals("Deployment Policy is in use", e.getMessage());
        }
        doDelete(UriUtils.buildUri(host, resourcePlacement.documentSelfLink), false);
        doDelete(UriUtils.buildUri(host, resourcePool.documentSelfLink), false);
        doDelete(UriUtils.buildUri(host, deploymentPolicy.documentSelfLink), false);
    }

    private DeploymentPolicy createDeploymentPolicy() {
        DeploymentPolicy policy = new DeploymentPolicy();
        policy.name = "policy";
        policy.description = "test policy";
        return policy;
    }
}
