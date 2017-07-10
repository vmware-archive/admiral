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

import java.util.Collections;

import org.junit.After;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.compute.LoadBalancerOperationTaskService.LoadBalancerOperationTaskState;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSLoadBalancerService;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class LoadBalancerOperationTaskServiceTest extends RequestBaseTest {

    @Override
    public void setUp() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
        super.setUp();
    }

    @After
    public void tearDown() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(false);
    }

    @Test
    public void testLoadBalancerResourceOperationCycle() throws Throwable {

        host.log("########  testLoadBalancerResourceOperationCycle ######## ");

        LoadBalancerState loadBalancerState = createLoadBalancerState();

        RequestBrokerState updateLoadBalancerRequest = new RequestBrokerState();
        updateLoadBalancerRequest.resourceType = ResourceType.LOAD_BALANCER_TYPE.getName();
        updateLoadBalancerRequest.resourceLinks = Collections
                .singleton(loadBalancerState.documentSelfLink);
        updateLoadBalancerRequest.operation = LoadBalancerOperationType.UPDATE.id;

        updateLoadBalancerRequest = startRequest(updateLoadBalancerRequest);

        String loadBalancerOperationTaskStateLink = UriUtils.buildUriPath(
                LoadBalancerOperationTaskService.FACTORY_LINK,
                extractId(updateLoadBalancerRequest.documentSelfLink));

        waitForTaskSuccess(loadBalancerOperationTaskStateLink,
                LoadBalancerOperationTaskState.class);
        waitForRequestToComplete(updateLoadBalancerRequest);

    }

    @Test
    public void testLoadBalancerResourceOperationCycleWithInvalidOperation() throws Throwable {

        host.log("########  testLoadBalancerResourceOperationCycleWithInvalidOperation ######## ");

        LoadBalancerState loadBalancerState = createLoadBalancerState();

        RequestBrokerState updateLoadBalancerRequest = new RequestBrokerState();
        updateLoadBalancerRequest.resourceType = ResourceType.LOAD_BALANCER_TYPE.getName();
        updateLoadBalancerRequest.resourceLinks = Collections
                .singleton(loadBalancerState.documentSelfLink);
        updateLoadBalancerRequest.operation = "INVALID";

        updateLoadBalancerRequest = startRequest(updateLoadBalancerRequest);

        String loadBalancerOperationTaskStateLink = UriUtils.buildUriPath(
                LoadBalancerOperationTaskService.FACTORY_LINK,
                extractId(updateLoadBalancerRequest.documentSelfLink));

        waitForTaskError(loadBalancerOperationTaskStateLink, LoadBalancerOperationTaskState.class);
        waitForRequestToFail(updateLoadBalancerRequest);

    }

    @Test
    public void testLoadBalancerResourceOperationCycleWithUnreachableAdapter() throws Throwable {

        host.log("#####  testLoadBalancerResourceOperationCycleWithUnreachableAdapter ###### ");

        LoadBalancerState loadBalancerState = createLoadBalancerState();

        stopService(AWSLoadBalancerService.SELF_LINK);

        RequestBrokerState updateLoadBalancerRequest = new RequestBrokerState();
        updateLoadBalancerRequest.resourceType = ResourceType.LOAD_BALANCER_TYPE.getName();
        updateLoadBalancerRequest.resourceLinks = Collections
                .singleton(loadBalancerState.documentSelfLink);
        updateLoadBalancerRequest.operation = LoadBalancerOperationType.UPDATE.id;

        updateLoadBalancerRequest = startRequest(updateLoadBalancerRequest);

        String loadBalancerOperationTaskStateLink = UriUtils.buildUriPath(
                LoadBalancerOperationTaskService.FACTORY_LINK,
                extractId(updateLoadBalancerRequest.documentSelfLink));

        waitForTaskError(loadBalancerOperationTaskStateLink, LoadBalancerOperationTaskState.class);
        waitForRequestToFail(updateLoadBalancerRequest);
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

}
