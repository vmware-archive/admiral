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

package com.vmware.admiral.host;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.service.common.NodeMigrationService;
import com.vmware.admiral.service.common.NodeMigrationService.MigrationRequest;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.MigrationTaskService;

public class MigrationIT extends RequestBaseTest {
    private static final String DEAFULT_NODE_GROUP = "/core/node-groups/default";

    private static final int DOCKER_HOST_COUNT = 5;

    private VerificationHost targetHost;
    private List<String> computeStates = new ArrayList<String>();
    private String resourcePoolStateLink;
    private String epzStateLink;

    @Override
    @Before
    public void setUp() throws Throwable {
        MockDockerAdapterService.resetContainers();
        startServices(host);
        host.waitForServiceAvailable(ComputeService.FACTORY_LINK, ResourcePoolService.FACTORY_LINK,
                ElasticPlacementZoneConfigurationService.SELF_LINK,
                ElasticPlacementZoneService.FACTORY_LINK);

        targetHost = createHost();
        startServices(targetHost);
        targetHost.waitForServiceAvailable(ComputeService.FACTORY_LINK,
                NodeMigrationService.SELF_LINK, ResourcePoolService.FACTORY_LINK);
        targetHost.addPrivilegedService(NodeMigrationService.class);

        setUpDockerHostAuthentication();
        // setup Docker Host:
        createResourcePool();
        createMultipleDockerHosts();
        createResourcePoolAndEPZ();
    }

    protected void createMultipleDockerHosts() throws Throwable {
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        synchronized (super.initializationLock) {
            for (int i = 0; i < DOCKER_HOST_COUNT; i++) {
                ComputeState state = createDockerHost(dockerHostDesc, resourcePool, true);
                computeStates.add(state.documentSelfLink);
            }
        }
    }

    protected void createResourcePoolAndEPZ() throws Throwable {
        ElasticPlacementZoneConfigurationState state = new ElasticPlacementZoneConfigurationState();
        state.resourcePoolState = new ResourcePoolState();
        state.epzState = new ElasticPlacementZoneState();

        state.resourcePoolState.name = "rp-1";
        state.epzState.tagLinksToMatch = new HashSet<>(Arrays.asList("tag1", "tag2"));

        state = doOperation(state,
                UriUtils.buildUri(host, ElasticPlacementZoneConfigurationService.SELF_LINK),
                ElasticPlacementZoneConfigurationState.class, false, Action.POST);
        Assert.assertNotNull(state.resourcePoolState);
        Assert.assertNotNull(state.resourcePoolState.documentSelfLink);
        resourcePoolStateLink = state.resourcePoolState.documentSelfLink;
        Assert.assertNotNull(state.epzState);
        Assert.assertNotNull(state.epzState.documentSelfLink);
        epzStateLink = state.epzState.documentSelfLink;
    }

    @Test
    public void testMigration() throws Throwable {
        startMigrationTaskService(targetHost);

        // do the migration
        MigrationRequest request = new MigrationRequest();
        request.sourceNodeGroup = host.getPublicUriAsString() + DEAFULT_NODE_GROUP;
        this.targetHost.testStart(1);
        Operation post = Operation.createPost(UriUtils.buildUri(targetHost.getUri(),
                NodeMigrationService.SELF_LINK));
        post.setBody(request);
        post.setCompletion((o, e) -> {
            if (e != null) {
                this.targetHost.failIteration(e);
                return;
            }
            this.targetHost.completeIteration();
        });
        this.targetHost.send(post);
        this.targetHost.testWait();

        verifyComputeStatesExist();
        // EPZ depends on RP to be migrated, so verify these states are copied
        verifyResourcePoolExists();
        verifyElasticPlacementZoneExists();
    }

    @Test
    public void testMigrationTaskFailure() throws Throwable {
        startMigrationTaskService(targetHost);

        // stop compute service in order to produce migration task failure
        ComputeService computeService = new ComputeService();
        computeService.setSelfLink(ComputeService.FACTORY_LINK);
        targetHost.stopService(computeService);

        MigrationRequest request = new MigrationRequest();
        request.sourceNodeGroup = host.getPublicUriAsString() + DEAFULT_NODE_GROUP;
        this.targetHost.testStart(1);
        Operation post = Operation
                .createPost(UriUtils.buildUri(targetHost.getUri(), NodeMigrationService.SELF_LINK));
        post.setBody(request);
        post.setCompletion((o, e) -> {
            if (e != null) {
                // failure is expected!
                assertEquals("One or more migration tasks failed", e.getMessage());
                this.targetHost.completeIteration();
                return;
            }

            this.targetHost.failIteration(new Exception("expected failure but got success"));
        });
        this.targetHost.send(post);
        this.targetHost.testWait();
    }

    @Test
    public void testMigrationServiceNotAvailable() {
        // Do not start migration service on target host

        MigrationRequest request = new MigrationRequest();
        request.sourceNodeGroup = host.getPublicUriAsString() + DEAFULT_NODE_GROUP;
        this.targetHost.testStart(1);
        Operation post = Operation
                .createPost(UriUtils.buildUri(targetHost.getUri(), NodeMigrationService.SELF_LINK));
        post.setBody(request);
        post.setCompletion((o, e) -> {
            if (e != null) {
                // failure is expected!
                assertEquals("Failure when calling migration task", e.getMessage());
                this.targetHost.completeIteration();
                return;
            }

            this.targetHost.failIteration(new Exception("expected failure but got success"));
        });
        this.targetHost.send(post);
        this.targetHost.testWait();
    }

    private void startMigrationTaskService(VerificationHost host) throws Throwable {
        URI u = UriUtils.buildUri(host, MigrationTaskService.FACTORY_LINK);
        Operation post = Operation.createPost(u);
        host.startService(post, MigrationTaskService.createFactory());
        host.waitForServiceAvailable(MigrationTaskService.FACTORY_LINK);
    }

    private void verifyComputeStatesExist() {
        for (String selfLink : computeStates) {
            this.targetHost.testStart(1);
            Operation get = Operation.createGet(UriUtils.buildUri(targetHost.getUri(),
                    selfLink));
            get.setCompletion((o, e) -> {
                if (e != null) {
                    targetHost.failIteration(e);
                    return;
                }
                ComputeState body = o.getBody(ComputeState.class);
                Assert.assertTrue(body != null);
                this.targetHost.completeIteration();
            });
            this.targetHost.send(get);
            this.targetHost.testWait();
        }
    }

    private void verifyResourcePoolExists() {
        this.targetHost.testStart(1);
        Operation get = Operation.createGet(UriUtils.buildUri(targetHost.getUri(),
                resourcePoolStateLink));
        get.setCompletion((o, e) -> {
            if (e != null) {
                targetHost.failIteration(e);
                return;
            }
            ResourcePoolState body = o.getBody(ResourcePoolState.class);
            Assert.assertTrue(body != null);
            this.targetHost.completeIteration();
        });
        this.targetHost.send(get);
        this.targetHost.testWait();
    }

    private void verifyElasticPlacementZoneExists() {
        this.targetHost.testStart(1);
        Operation get = Operation.createGet(UriUtils.buildUri(targetHost.getUri(),
                epzStateLink));
        get.setCompletion((o, e) -> {
            if (e != null) {
                targetHost.failIteration(e);
                return;
            }
            ElasticPlacementZoneState body = o.getBody(ElasticPlacementZoneState.class);
            Assert.assertTrue(body != null);
            this.targetHost.completeIteration();
        });
        this.targetHost.send(get);
        this.targetHost.testWait();
    }
}
