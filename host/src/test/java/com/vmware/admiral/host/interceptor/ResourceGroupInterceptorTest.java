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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.util.ProjectUtil;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class ResourceGroupInterceptorTest extends BaseTestCase {

    @Before
    public void setUp() throws Throwable {

        // start services
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, true);

        // wait for needed services
        waitForServiceAvailability(ResourceGroupService.FACTORY_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);
    }


    @Override
    protected void registerInterceptors(OperationInterceptorRegistry registry) {
        ResourceGroupInterceptor.register(registry);
    }

    @Test
    public void testDeleteResourceGroupAssociatedWithPlacementShouldFail() throws Throwable {
        // create resource group
        ResourceGroupState resourceGroup = createResourceGroup();
        // Create placement
        ResourcePoolState pool = createResourcePool();
        createPlacement(pool.documentSelfLink, resourceGroup.documentSelfLink);

        // try to delete the resource group. This should fail
        Operation delete = Operation
                .createDelete(UriUtils.buildUri(host, resourceGroup.documentSelfLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        try {
                            String message = e instanceof CompletionException
                                    ? e.getCause().getMessage() : e.getMessage();
                            verifyExceptionMessage(message, ProjectUtil.PROJECT_IN_USE_MESSAGE);
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

    @Test
    public void testDeleteResourceGroupAssociatedWithComputeNetworkShouldFail() throws Throwable {
        // create resource group
        ResourceGroupState resourceGroup = createResourceGroup();
        // Create compute network
        createComputeNetwork(resourceGroup.documentSelfLink);

        // try to delete the resource group. This should fail
        Operation delete = Operation
                .createDelete(UriUtils.buildUri(host, resourceGroup.documentSelfLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        try {
                            String message = e instanceof CompletionException
                                    ? e.getCause().getMessage() : e.getMessage();
                            verifyExceptionMessage(message, "Resource Group is associated to 1 network");
                            host.completeIteration();
                        } catch (IllegalStateException ex) {
                            host.failIteration(ex);
                        }
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when deleting a resource group that is in use by a "
                                        + "network"));
                    }
                });
        host.testStart(1);
        host.send(delete);
        host.testWait();
    }

    @Test
    public void testDeleteResourceGroupNotAssociatedToAnyResourcesShouldSucceed() throws Throwable {
        // create resource group
        ResourceGroupState resourceGroup = createResourceGroup();
        // Create various resources that are not directly associated to the resource group
        createComputeNetwork("anything");
        createSecurityGroup("anything");
        createSubnet("anything");

        // try to delete the resource group. This should succeed.
        Operation delete = Operation
                .createDelete(UriUtils.buildUri(host, resourceGroup.documentSelfLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(new IllegalStateException(
                                "Should succeed deleting a resource group that is not in use"));
                    } else {
                        host.completeIteration();
                    }
                });
        host.testStart(1);
        host.send(delete);
        host.testWait();
    }

    @Test
    public void testDeleteResourceGroupAssociatedWithSecurityGroupShouldFail() throws Throwable {
        // create resource group
        ResourceGroupState resourceGroup = createResourceGroup();
        // Create security group
        createSecurityGroup(resourceGroup.documentSelfLink);

        // try to delete the resource group. This should fail
        Operation delete = Operation
                .createDelete(UriUtils.buildUri(host, resourceGroup.documentSelfLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        try {
                            String message = e instanceof CompletionException
                                    ? e.getCause().getMessage() : e.getMessage();
                            verifyExceptionMessage(message, "Resource Group is associated to 1 "
                                    + "security group");
                            host.completeIteration();
                        } catch (IllegalStateException ex) {
                            host.failIteration(ex);
                        }
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when deleting a resource group that is in use by a "
                                        + "security group"));
                    }
                });
        host.testStart(1);
        host.send(delete);
        host.testWait();
    }

    @Test
    public void testDeleteResourceGroupAssociatedWithSubnetsShouldFail() throws Throwable {
        // create resource group
        ResourceGroupState resourceGroup = createResourceGroup();
        // Create two subnets
        createSubnet(resourceGroup.documentSelfLink);
        createSubnet(resourceGroup.documentSelfLink);

        // try to delete the resource group. This should fail
        Operation delete = Operation
                .createDelete(UriUtils.buildUri(host, resourceGroup.documentSelfLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        try {
                            String message = e instanceof CompletionException
                                    ? e.getCause().getMessage() : e.getMessage();
                            verifyExceptionMessage(message, "Resource Group is associated to 2 "
                                    + "subnets");
                            host.completeIteration();
                        } catch (IllegalStateException ex) {
                            host.failIteration(ex);
                        }
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when deleting a resource group that is in use by a "
                                        + "subnet"));
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

    private GroupResourcePlacementState createPlacement(String resourcePoolLink,
            String resourceGroupLink) throws Throwable {
        GroupResourcePlacementState placement = new GroupResourcePlacementState();
        placement.name = "placement";
        placement.resourcePoolLink = resourcePoolLink;
        placement.tenantLinks = Arrays.asList(resourceGroupLink);

        return doPost(placement, GroupResourcePlacementService.FACTORY_LINK);
    }

    private ComputeNetwork createComputeNetwork(String resourceGroupLink) throws Throwable {
        ComputeNetwork cn = new ComputeNetwork();
        cn.name = UUID.randomUUID().toString();
        cn.descriptionLink = "description for " + cn.name;
        cn.groupLinks = new HashSet<>();
        cn.groupLinks.add(resourceGroupLink);

        return doPost(cn, ComputeNetworkService.FACTORY_LINK);
    }

    private SecurityGroupState createSecurityGroup(String resourceGroupLink) throws Throwable {
        SecurityGroupState sg = new SecurityGroupState();
        sg.name = UUID.randomUUID().toString();
        sg.instanceAdapterReference = URI.create("");
        sg.regionId = "us-east-1";
        sg.resourcePoolLink = "resource-pool";
        sg.egress = new ArrayList<>();
        sg.ingress = new ArrayList<>();
        sg.groupLinks = new HashSet<>();
        sg.groupLinks.add(resourceGroupLink);

        return doPost(sg, SecurityGroupService.FACTORY_LINK);
    }

    private SubnetState createSubnet(String resourceGroupLink) throws Throwable {
        SubnetState s = new SubnetState();
        s.name = UUID.randomUUID().toString();
        s.networkLink = "network link";
        s.subnetCIDR = "0.0.0.0/28";
        s.groupLinks = new HashSet<>();
        s.groupLinks.add(resourceGroupLink);

        return doPost(s, SubnetService.FACTORY_LINK);
    }

    private void verifyExceptionMessage(String message, String expected) {
        if (!message.equals(expected)) {
            String errorMessage = String.format("Expected error '%s' but was '%s'", expected,
                    message);
            throw new IllegalStateException(errorMessage);
        }
    }
}
