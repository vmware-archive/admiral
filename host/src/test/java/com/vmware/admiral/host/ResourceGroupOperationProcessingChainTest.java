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

import java.util.Arrays;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.util.ProjectUtil;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

public class ResourceGroupOperationProcessingChainTest extends BaseTestCase {

    private ResourceGroupState resourceGroup;

    @Before
    public void setUp() throws Throwable {

        // start services
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, true);

        // wait for needed services
        waitForServiceAvailability(ResourceGroupService.FACTORY_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);

        resourceGroup = createResourceGroup();
    }


    @Override
    protected void customizeChains(
            Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains) {
        super.customizeChains(chains);
        chains.put(ResourceGroupService.class, ResourceGroupOperationProcessingChain.class);
    }

    @Test
    public void testDeleteResourceGroupAssociatedWithPlacementShouldFail() throws Throwable {
        // Create placement
        ResourcePoolState pool = createResourcePool();
        createPlacement(pool.documentSelfLink);

        // try to delete the resource group. This should fail
        Operation delete = Operation
                .createDelete(UriUtils.buildUri(host, resourceGroup.documentSelfLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        try {
                            verifyExceptionMessage(e.getMessage(),
                                    ProjectUtil.PROJECT_IN_USE_MESSAGE);
                            host.completeIteration();
                        } catch (IllegalStateException ex) {
                            host.failIteration(ex);
                        }
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when deleting a resource group that is in use"));
                    }
                });
        host.testStart(1);
        host.send(delete);
        host.testWait();
    }


    private ResourceGroupState createResourceGroup() throws Throwable {
        ResourceGroupState resourceGroup = new ResourceGroupState();
        resourceGroup.name = "test-group-name";
        resourceGroup.id = "test-group-id";
        resourceGroup.documentSelfLink = ResourceGroupService.FACTORY_LINK + "/" + resourceGroup.id;

        return doPost(resourceGroup, ResourceGroupService.FACTORY_LINK);
    }

    private ResourcePoolState createResourcePool() throws Throwable {
        ResourcePoolState pool = new ResourcePoolState();
        pool.name = "pool";

        return doPost(pool, ResourcePoolService.FACTORY_LINK);
    }

    private GroupResourcePlacementState createPlacement(String resourcePoolLink) throws Throwable {
        GroupResourcePlacementState placement = new GroupResourcePlacementState();
        placement.name = "placement";
        placement.resourcePoolLink = resourcePoolLink;
        placement.tenantLinks = Arrays.asList(resourceGroup.documentSelfLink);

        return doPost(placement, GroupResourcePlacementService.FACTORY_LINK);
    }

    private void verifyExceptionMessage(String message, String expected) {
        if (!message.equals(expected)) {
            String errorMessage = String.format("Expected error '%s' but was '%s'", expected,
                    message);
            throw new IllegalStateException(errorMessage);
        }
    }
}
