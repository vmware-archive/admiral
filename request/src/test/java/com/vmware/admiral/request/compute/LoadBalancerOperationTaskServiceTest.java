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

import java.util.Collections;

import org.junit.After;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.LoadBalancerOperationTaskService.LoadBalancerOperationTaskState;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSLoadBalancerService;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class LoadBalancerOperationTaskServiceTest extends RequestBaseTest {

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        DeploymentProfileConfig.getInstance().setTest(true);
    }

    @After
    public void tearDown() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(false);
    }

    @Test
    public void testLoadBalancerResourceOperationCycle() throws Throwable {

        host.log("########  testLoadBalancerResourceOperationCycle ######## ");

        LoadBalancerState loadBalancerState = createLoadBalancerState();

        LoadBalancerOperationTaskState loadBalancerOperationTaskState = createLoadBalancerOperationTask(
                loadBalancerState, LoadBalancerOperationType.UPDATE.id);

        update(loadBalancerOperationTaskState);

    }

    @Test
    public void testLoadBalancerResourceOperationCycleWithInvalidOperation() throws Throwable {

        host.log("########  testLoadBalancerResourceOperationCycleWithInvalidOperation ######## ");

        LoadBalancerState loadBalancerState = createLoadBalancerState();

        LoadBalancerOperationTaskState loadBalancerOperationTaskState = createLoadBalancerOperationTask(
                loadBalancerState, "INVALID");

        updateWithError(loadBalancerOperationTaskState);

    }

    @Test
    public void testLoadBalancerResourceOperationCycleWithUnreachableAdapter() throws Throwable {

        host.log("#####  testLoadBalancerResourceOperationCycleWithUnreachableAdapter ###### ");

        LoadBalancerState loadBalancerState = createLoadBalancerState();

        LoadBalancerOperationTaskState loadBalancerOperationTaskState = createLoadBalancerOperationTask(
                loadBalancerState, LoadBalancerOperationType.UPDATE.id);

        stopService(AWSLoadBalancerService.SELF_LINK);
        updateWithError(loadBalancerOperationTaskState);

    }

    private LoadBalancerOperationTaskState update(LoadBalancerOperationTaskState operationTask)
            throws Throwable {
        operationTask = startUpdateTask(operationTask);
        host.log("Start update task: " + operationTask.documentSelfLink);

        operationTask = waitForTaskSuccess(operationTask.documentSelfLink,
                LoadBalancerOperationTaskState.class);

        return operationTask;
    }

    private LoadBalancerOperationTaskState updateWithError(LoadBalancerOperationTaskState
            operationTask)
            throws Throwable {
        operationTask = startUpdateTask(operationTask);
        host.log("Start update task: " + operationTask.documentSelfLink);

        operationTask = waitForTaskError(operationTask.documentSelfLink,
                LoadBalancerOperationTaskState.class);

        return operationTask;
    }

    private LoadBalancerOperationTaskState createLoadBalancerOperationTask(LoadBalancerState
            loadBalancerState, String operation) {
        LoadBalancerOperationTaskState lbOperationState = new LoadBalancerOperationTaskState();
        lbOperationState.resourceLinks = Collections.singleton(loadBalancerState.documentSelfLink);
        lbOperationState.operation = operation;
        lbOperationState.tenantLinks = loadBalancerState.tenantLinks;

        return lbOperationState;
    }

    private LoadBalancerOperationTaskState startUpdateTask(
            LoadBalancerOperationTaskState operationTask)
            throws Throwable {
        LoadBalancerOperationTaskState loadBalancerOperationTaskState = doPost(operationTask,
                LoadBalancerOperationTaskService.FACTORY_LINK);

        assertNotNull(loadBalancerOperationTaskState);
        return loadBalancerOperationTaskState;
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
