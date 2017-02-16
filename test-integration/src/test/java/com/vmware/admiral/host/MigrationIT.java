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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.service.common.NodeMigrationService;
import com.vmware.admiral.service.common.NodeMigrationService.MigrationRequest;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.MigrationTaskService;

public class MigrationIT extends RequestBaseTest {
    private static final String DEAFULT_NODE_GROUP = "/core/node-groups/default";

    private static final int DOCKER_HOST_COUNT = 5;

    private VerificationHost targetHost;
    private List<String> computeStates = new ArrayList<String>();

    @Override
    @Before
    public void setUp() throws Throwable {
        MockDockerAdapterService.resetContainers();
        startServices(host);
        host.waitForServiceAvailable(ComputeService.FACTORY_LINK, ResourcePoolService.FACTORY_LINK);
        targetHost = createHost();
        startServices(targetHost);
        targetHost.waitForServiceAvailable(ComputeService.FACTORY_LINK,
                NodeMigrationService.SELF_LINK, ResourcePoolService.FACTORY_LINK);
        targetHost.waitForServiceAvailable(ComputeService.FACTORY_LINK,
                NodeMigrationService.SELF_LINK, ResourcePoolService.FACTORY_LINK);
        startMigrationService(targetHost);

        setUpDockerHostAuthentication();
        // setup Docker Host:
        createResourcePool();
        createMultipleDockerHosts();
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

    @Test
    public void testMigration() throws Throwable {
        NodeMigrationService state = new NodeMigrationService();
        Set<String> services = new HashSet<>();
        services.add(ComputeService.FACTORY_LINK);
        state.services = services;

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

    private void startMigrationService(VerificationHost host) throws Throwable {
        URI u = UriUtils.buildUri(host, MigrationTaskService.FACTORY_LINK);
        Operation post = Operation.createPost(u);
        host.startService(post, MigrationTaskService.createFactory());
        host.waitForServiceAvailable(MigrationTaskService.FACTORY_LINK);
    }

}
