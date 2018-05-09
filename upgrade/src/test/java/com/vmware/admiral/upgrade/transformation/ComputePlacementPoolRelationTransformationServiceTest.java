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

package com.vmware.admiral.upgrade.transformation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.cluster.ClusterUtils;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.upgrade.UpgradeBaseTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

public class ComputePlacementPoolRelationTransformationServiceTest extends UpgradeBaseTest {
    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ElasticPlacementZoneConfigurationService.SELF_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK);
        waitForServiceAvailability(ComputePlacementPoolRelationTransformationService.SELF_LINK);
    }

    @Test
    public void testNoPlacements() throws Throwable {
        doDelete(UriUtils.buildUri(host,
                GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK),
                false);
        List<String> links = getDocumentLinksOfType(GroupResourcePlacementState.class);
        Assert.assertTrue(links.isEmpty());
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ComputePlacementPoolRelationTransformationService.SELF_LINK), false,
                Service.Action.POST);
    }

    @Test
    public void testNoHosts() throws Throwable {
        List<String> links = getDocumentLinksOfType(GroupResourcePlacementState.class);
        Assert.assertFalse(links.isEmpty());
        List<String> hostLinks = getDocumentLinksOfType(ComputeState.class);
        Assert.assertTrue(hostLinks.isEmpty());
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host,
                        ComputePlacementPoolRelationTransformationService.SELF_LINK),
                false,
                Service.Action.POST);
    }

    @Test
    public void testDefaultPlacementDefaultPoolOneHost() throws Throwable {
        List<String> links = getDocumentLinksOfType(ResourcePoolState.class);
        Assert.assertTrue(links.size() == 1);
        ComputeState compute = createComputeState("host1",
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);
        compute = doPost(compute, ComputeService.FACTORY_LINK);
        Assert.assertTrue(compute.tagLinks == null);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ComputePlacementPoolRelationTransformationService.SELF_LINK), false,
                Service.Action.POST);
        compute = getDocument(ComputeState.class, compute.documentSelfLink);
        // check that a tag is added to the compute
        Assert.assertTrue(compute.tagLinks != null);
        Assert.assertTrue(compute.tagLinks.size() == 1);
        Assert.assertTrue(compute.tenantLinks.size() == 1);
        Assert.assertTrue(compute.tenantLinks.get(0).equals(ProjectService.DEFAULT_PROJECT_LINK));
        // Check that the pool has the default project
        ElasticPlacementZoneConfigurationState pool = getDocument(
                ElasticPlacementZoneConfigurationState.class,
                ElasticPlacementZoneConfigurationService.SELF_LINK
                        + links.get(0));
        Assert.assertTrue(pool.epzState.tagLinksToMatch.size() == 1);
        Assert.assertTrue(pool.epzState.tagLinksToMatch.containsAll(compute.tagLinks));
        GroupResourcePlacementState placement = getDocument(GroupResourcePlacementState.class,
                GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK);
        Assert.assertTrue(placement.tenantLinks.size() == 1);
        Assert.assertTrue(placement.tenantLinks.get(0).equals(ProjectService.DEFAULT_PROJECT_LINK));

        host.testStart(1);
        doPost(compute, ResourcePoolTransformationService.SELF_LINK);
        DeferredResult<List<ComputeState>> hostsWithinPlacementZone = ClusterUtils
                .getHostsWithinPlacementZone(pool.epzState.resourcePoolLink,
                        ProjectService.DEFAULT_PROJECT_LINK, host);
        hostsWithinPlacementZone.whenComplete((computeStates, ex) -> {
            if (ex != null) {
                host.failIteration(ex);
                return;
            } else if (computeStates.size() == 1) {
                host.completeIteration();
            } else {
                host.failIteration(new IllegalStateException("Incorrect number of hosts found"));
            }
        });
        host.testWait();
    }

    @Test
    public void testDefaultPlacementDefaultPoolTwoHost() throws Throwable {
        List<String> links = getDocumentLinksOfType(ResourcePoolState.class);
        Assert.assertTrue(links.size() == 1);
        ComputeState compute1 = createComputeState("host1",
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);
        compute1 = doPost(compute1, ComputeService.FACTORY_LINK);
        ComputeState compute2 = createComputeState("host2",
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);
        compute2 = doPost(compute2, ComputeService.FACTORY_LINK);
        Assert.assertTrue(compute1.tagLinks == null);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ComputePlacementPoolRelationTransformationService.SELF_LINK), false,
                Service.Action.POST);
        compute1 = getDocument(ComputeState.class, compute1.documentSelfLink);
        compute2 = getDocument(ComputeState.class, compute2.documentSelfLink);
        // check that a tag is added to the hosts
        Assert.assertTrue(compute1.tagLinks != null);
        Assert.assertTrue(compute1.tagLinks.size() == 1);
        Assert.assertTrue(compute1.tenantLinks.size() == 1);
        Assert.assertTrue(compute1.tenantLinks.get(0).equals(ProjectService.DEFAULT_PROJECT_LINK));

        Assert.assertTrue(compute2.tagLinks != null);
        Assert.assertTrue(compute2.tagLinks.size() == 1);
        Assert.assertTrue(compute2.tenantLinks.size() == 1);
        Assert.assertTrue(compute2.tenantLinks.get(0).equals(ProjectService.DEFAULT_PROJECT_LINK));
        Assert.assertTrue(compute2.tagLinks.equals(compute1.tagLinks));

        // Check that the pool has the default project
        ElasticPlacementZoneConfigurationState pool = getDocument(
                ElasticPlacementZoneConfigurationState.class,
                ElasticPlacementZoneConfigurationService.SELF_LINK
                        + links.get(0));
        Assert.assertTrue(pool.epzState.tagLinksToMatch.size() == 1);
        Assert.assertTrue(pool.epzState.tagLinksToMatch.containsAll(compute1.tagLinks));

        // Check that the placement has the default project
        GroupResourcePlacementState placement = getDocument(GroupResourcePlacementState.class,
                GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK);
        Assert.assertTrue(placement.tenantLinks.size() == 1);
        Assert.assertTrue(placement.tenantLinks.get(0).equals(ProjectService.DEFAULT_PROJECT_LINK));
    }

    @Test
    public void testTwoPlacementsDefaultPoolTwoHost() throws Throwable {
        List<String> links = getDocumentLinksOfType(ResourcePoolState.class);
        Assert.assertTrue(links.size() == 1);
        ComputeState compute1 = createComputeState("host1",
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);
        compute1 = doPost(compute1, ComputeService.FACTORY_LINK);
        ComputeState compute2 = createComputeState("host2",
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);
        compute2.tenantLinks = new ArrayList<>();
        compute2.tenantLinks.add("test");
        compute2.tagLinks = new HashSet<>();
        compute2.tagLinks.add("testTag");
        compute2 = doPost(compute2, ComputeService.FACTORY_LINK);
        Assert.assertTrue(compute1.tagLinks == null);

        GroupResourcePlacementState placement = new GroupResourcePlacementState();
        placement.name = "placement";
        placement.resourcePoolLink = GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK;
        placement.tenantLinks = new ArrayList<>();
        placement.tenantLinks.add("test");
        placement = doPost(placement, GroupResourcePlacementService.FACTORY_LINK);

        // Do the transformation
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ComputePlacementPoolRelationTransformationService.SELF_LINK), false,
                Service.Action.POST);

        compute1 = getDocument(ComputeState.class, compute1.documentSelfLink);
        compute2 = getDocument(ComputeState.class, compute2.documentSelfLink);

        // check that a tag is added to the hosts
        Assert.assertTrue(compute1.tagLinks != null);
        Assert.assertTrue(compute1.tagLinks.size() == 2);
        Assert.assertTrue(compute1.tenantLinks.size() == 2);
        Assert.assertTrue(compute1.tenantLinks.contains(ProjectService.DEFAULT_PROJECT_LINK));
        Assert.assertTrue(compute1.tenantLinks.contains("test"));

        Assert.assertTrue(compute2.tagLinks != null);
        // tag links should be 3 because there was one before the transformation
        Assert.assertTrue(compute2.tagLinks.size() == 3);
        Assert.assertTrue(compute2.tenantLinks.size() == 2);
        Assert.assertTrue(compute2.tenantLinks.contains(ProjectService.DEFAULT_PROJECT_LINK));
        Assert.assertTrue(compute2.tenantLinks.contains("test"));

        // Check that the pool has the default project
        ElasticPlacementZoneConfigurationState pool = getDocument(
                ElasticPlacementZoneConfigurationState.class,
                ElasticPlacementZoneConfigurationService.SELF_LINK
                        + links.get(0));
        Assert.assertTrue(pool.epzState.tagLinksToMatch.size() == 2);
        Assert.assertTrue(pool.epzState.tagLinksToMatch.containsAll(compute1.tagLinks));

        // Check that the placement has the default project
        GroupResourcePlacementState defaultPlacement = getDocument(
                GroupResourcePlacementState.class,
                GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK);
        Assert.assertTrue(defaultPlacement.tenantLinks.size() == 1);
        Assert.assertTrue(
                defaultPlacement.tenantLinks.get(0).equals(ProjectService.DEFAULT_PROJECT_LINK));

        placement = getDocument(GroupResourcePlacementState.class, placement.documentSelfLink);
        Assert.assertTrue(placement.tenantLinks.size() == 1);
        Assert.assertTrue(placement.tenantLinks.get(0).equals("test"));
    }

    @Test
    public void testMultiplePlacementsMultiplePoolsMultipleHosts() throws Throwable {
        ResourcePoolState pool = createResourcePool();

        GroupResourcePlacementState placement1 = new GroupResourcePlacementState();
        placement1.name = "placement";
        placement1.resourcePoolLink = pool.documentSelfLink;
        placement1.tenantLinks = new ArrayList<>();
        placement1.tenantLinks.add("test");
        placement1 = doPost(placement1, GroupResourcePlacementService.FACTORY_LINK);

        GroupResourcePlacementState placement2 = new GroupResourcePlacementState();
        placement2.name = "placement2";
        placement2.resourcePoolLink = pool.documentSelfLink;
        placement2.tenantLinks = new ArrayList<>();
        placement2.tenantLinks.add("test");
        placement2 = doPost(placement2, GroupResourcePlacementService.FACTORY_LINK);

        List<String> links = getDocumentLinksOfType(ResourcePoolState.class);
        Assert.assertTrue(links.size() == 2);

        ComputeState compute1Pool1 = createComputeState("host1Pool1", pool.documentSelfLink);
        compute1Pool1 = doPost(compute1Pool1, ComputeService.FACTORY_LINK);
        ComputeState compute2Pool1 = createComputeState("host2Pool1", pool.documentSelfLink);
        compute2Pool1 = doPost(compute2Pool1, ComputeService.FACTORY_LINK);

        ComputeState compute1DefaultPool = createComputeState("host1-defaultPool",
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);
        compute1DefaultPool = doPost(compute1DefaultPool, ComputeService.FACTORY_LINK);

        // Do the transformation
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ComputePlacementPoolRelationTransformationService.SELF_LINK), false,
                Service.Action.POST);

        compute1Pool1 = getDocument(ComputeState.class, compute1Pool1.documentSelfLink);
        compute2Pool1 = getDocument(ComputeState.class, compute2Pool1.documentSelfLink);
        compute1DefaultPool = getDocument(ComputeState.class, compute1DefaultPool.documentSelfLink);

        // check that a tag is added to the hosts
        Assert.assertTrue(compute1Pool1.tagLinks != null);
        Assert.assertTrue(compute1Pool1.tagLinks.size() == 2);
        Assert.assertTrue(compute1Pool1.tenantLinks.size() == 1);
        Assert.assertTrue(compute1Pool1.tenantLinks.contains("test"));

        Assert.assertTrue(compute2Pool1.tagLinks != null);
        // tag links should be 3 because there was one before the transformation
        Assert.assertTrue(compute2Pool1.tagLinks.size() == 2);
        Assert.assertTrue(compute2Pool1.tenantLinks.size() == 1);
        Assert.assertTrue(
                compute2Pool1.tenantLinks.equals(compute1Pool1.tenantLinks));

        Assert.assertTrue(compute1DefaultPool.tagLinks != null);
        Assert.assertTrue(compute1DefaultPool.tagLinks.size() == 1);
        Assert.assertTrue(compute1DefaultPool.tenantLinks.size() == 1);
        Assert.assertTrue(
                compute1DefaultPool.tenantLinks.contains(ProjectService.DEFAULT_PROJECT_LINK));

        // Check that the pool has the default project
        ElasticPlacementZoneConfigurationState poolEPZ = getDocument(
                ElasticPlacementZoneConfigurationState.class,
                ElasticPlacementZoneConfigurationService.SELF_LINK
                        + pool.documentSelfLink);
        Assert.assertTrue(poolEPZ.epzState.tagLinksToMatch.size() == 2);
        Assert.assertTrue(poolEPZ.epzState.tagLinksToMatch.containsAll(compute1Pool1.tagLinks));
        Assert.assertTrue(poolEPZ.epzState.tagLinksToMatch.containsAll(compute2Pool1.tagLinks));

        ElasticPlacementZoneConfigurationState defaultPoolEPZ = getDocument(
                ElasticPlacementZoneConfigurationState.class,
                ElasticPlacementZoneConfigurationService.SELF_LINK +
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);
        Assert.assertTrue(defaultPoolEPZ.epzState.tagLinksToMatch.size() == 1);
        Assert.assertTrue(
                defaultPoolEPZ.epzState.tagLinksToMatch.containsAll(compute1DefaultPool.tagLinks));

        // Check that the placement has the default project
        GroupResourcePlacementState defaultPlacement = getDocument(
                GroupResourcePlacementState.class,
                GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK);
        Assert.assertTrue(defaultPlacement.tenantLinks.size() == 1);
        Assert.assertTrue(
                defaultPlacement.tenantLinks.get(0).equals(ProjectService.DEFAULT_PROJECT_LINK));

        placement1 = getDocument(GroupResourcePlacementState.class, placement1.documentSelfLink);
        Assert.assertTrue(placement1.tenantLinks.size() == 1);
        Assert.assertTrue(placement1.tenantLinks.get(0).equals("test"));
    }

    @Test
    public void testGetComputeStatesForEPZ() throws Throwable {
        List<String> links = getDocumentLinksOfType(ResourcePoolState.class);
        Assert.assertTrue(links.size() == 1);
        ComputeState compute = createComputeState("host1",
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);
        compute = doPost(compute, ComputeService.FACTORY_LINK);
        Assert.assertTrue(compute.tagLinks == null);
        doPost(compute, ComputePlacementPoolRelationTransformationService.SELF_LINK);
        doPost(compute, ResourcePoolTransformationService.SELF_LINK);

        host.testStart(1);
        DeferredResult<List<ComputeState>> hostsWithinPlacementZone = ClusterUtils
                .getHostsWithinPlacementZone(
                        GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK,
                        ProjectService.DEFAULT_PROJECT_LINK, host);
        hostsWithinPlacementZone.whenComplete((computeStates, ex) -> {
            if (ex != null) {
                host.failIteration(ex);
                return;
            } else if (computeStates.size() == 1) {
                host.completeIteration();
            } else {
                host.failIteration(new IllegalStateException("Incorrect number of hosts found"));
            }
        });
        host.testWait();
    }

    private ComputeState createComputeState(String hostId, String pool) {
        ComputeState cs = new ComputeState();
        cs.id = hostId;
        cs.descriptionLink = "test";
        cs.documentSelfLink = cs.id;
        cs.address = "test-address";
        cs.resourcePoolLink = pool;
        return cs;
    }

    private ResourcePoolState createResourcePool() throws Throwable {
        ResourcePoolState pool = new ResourcePoolState();
        pool.name = "pool";
        return doPost(pool, ResourcePoolService.FACTORY_LINK);
    }
}
