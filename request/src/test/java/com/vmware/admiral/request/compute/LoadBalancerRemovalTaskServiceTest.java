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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.LoadBalancerRemovalTaskService.LoadBalancerRemovalTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSLoadBalancerService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
import com.vmware.photon.controller.model.resources.LoadBalancerService;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisionLoadBalancerTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

/**
 * Tests for the {@link LoadBalancerRemovalTaskService} class.
 */
public class LoadBalancerRemovalTaskServiceTest extends RequestBaseTest {

    private LoadBalancerDescription loadBalancerDesc;
    private LoadBalancerState loadBalancerState;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();

        // setup Load Balancer description
        createLoadBalancerDescription(UUID.randomUUID().toString());
        ComputeState compute = createVmComputeWithRandomComputeDescription(true,
                ComputeType.VM_GUEST);
        SubnetState subnet = createSubnetState(null);
        createLoadBalancerState(loadBalancerDesc.documentSelfLink, compute.documentSelfLink,
                subnet.documentSelfLink);
    }

    @Test
    public void testRemovalTaskServiceLifeCycle() throws Throwable {
        LoadBalancerRemovalTaskState removalTask = createLoadBalancerRemovalTask(
                loadBalancerState.documentSelfLink);

        remove(removalTask, false);

        LoadBalancerState document = getDocumentNoWait(LoadBalancerState.class,
                loadBalancerState.documentSelfLink);
        assertNull(document);
    }

    @Test
    public void testRemovalTaskServiceWithStoppedLBTaskService() throws Throwable {
        LoadBalancerRemovalTaskState removalTask = createLoadBalancerRemovalTask(
                loadBalancerState.documentSelfLink);

        stopService(ProvisionLoadBalancerTaskService.FACTORY_LINK);
        remove(removalTask, true);

        LoadBalancerState document = getDocument(LoadBalancerState.class,
                loadBalancerState.documentSelfLink);
        assertNotNull(document);
    }

    @Test
    public void testRemovalWithAlreadyRemovedInstances() throws Throwable {
        for (String computeLink : loadBalancerState.computeLinks) {
            delete(computeLink);
        }
        LoadBalancerRemovalTaskState removalTask = createLoadBalancerRemovalTask(
                loadBalancerState.documentSelfLink);

        remove(removalTask, false);

        LoadBalancerState document = getDocumentNoWait(LoadBalancerState.class,
                loadBalancerState.documentSelfLink);
        assertNull(document);
    }

    private void stopService(String link) {
        TestContext ctx = testCreate(1);
        Operation deleteOp = Operation.createDelete(UriUtils.buildUri(host, link))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_INDEX_UPDATE)
                .setReplicationDisabled(true).setCompletion(ctx.getCompletion())
                .setReferer(host.getUri());
        host.send(deleteOp);
        ctx.await();
    }

    private LoadBalancerRemovalTaskState createLoadBalancerRemovalTask(String loadBalancerLink) {
        LoadBalancerRemovalTaskState removalTask = new LoadBalancerRemovalTaskState();
        removalTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        removalTask.customProperties = new HashMap<>();
        removalTask.resourceLinks = new HashSet<>();
        removalTask.resourceLinks.add(loadBalancerLink);
        return removalTask;
    }

    private LoadBalancerRemovalTaskState remove(LoadBalancerRemovalTaskState removalTask,
            boolean shouldFail) throws Throwable {
        removalTask = startRemovalTask(removalTask);
        host.log("Start removal test: " + removalTask.documentSelfLink);

        removalTask = shouldFail ?
                waitForTaskError(removalTask.documentSelfLink, LoadBalancerRemovalTaskState.class) :
                waitForTaskSuccess(removalTask.documentSelfLink,
                        LoadBalancerRemovalTaskState.class);

        return removalTask;
    }

    private LoadBalancerRemovalTaskState startRemovalTask(LoadBalancerRemovalTaskState removalTask)
            throws Throwable {
        LoadBalancerRemovalTaskState outRemovalTask = doPost(removalTask,
                LoadBalancerRemovalTaskService.FACTORY_LINK);
        assertNotNull(outRemovalTask);
        return outRemovalTask;
    }

    private LoadBalancerDescription createLoadBalancerDescription(String name) throws Throwable {
        synchronized (initializationLock) {
            if (loadBalancerDesc == null) {
                LoadBalancerDescription desc = TestRequestStateFactory
                        .createLoadBalancerDescription(name);
                desc.documentSelfLink = UUID.randomUUID().toString();
                desc.computeDescriptionLink =
                        ComputeDescriptionService.FACTORY_LINK + "/dummy-compute-link";

                loadBalancerDesc = doPost(desc, LoadBalancerDescriptionService.FACTORY_LINK);
                assertNotNull(loadBalancerDesc);
            }
            return loadBalancerDesc;
        }
    }

    private LoadBalancerState createLoadBalancerState(String lbdLink, String computeLink,
            String subnetLink) throws Throwable {
        synchronized (initializationLock) {
            LoadBalancerState lbState = TestRequestStateFactory
                    .createLoadBalancerState(UUID.randomUUID().toString());
            lbState.descriptionLink = lbdLink;
            lbState.endpointLink = this.endpoint.documentSelfLink;
            lbState.computeLinks = Collections.singleton(computeLink);
            lbState.subnetLinks = Collections.singleton(subnetLink);
            lbState.instanceAdapterReference = UriUtils
                    .buildUri(host, AWSLoadBalancerService.SELF_LINK);

            loadBalancerState = doPost(lbState, LoadBalancerService.FACTORY_LINK);
            assertNotNull(loadBalancerState);
            return loadBalancerState;
        }
    }
}
