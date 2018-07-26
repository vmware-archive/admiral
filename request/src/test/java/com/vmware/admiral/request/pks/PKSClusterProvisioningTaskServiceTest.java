/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.pks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_CREATE;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.vmware.admiral.adapter.pks.test.MockPKSAdapterService;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.pks.PKSEndpointService.Endpoint;
import com.vmware.admiral.compute.pks.PKSEndpointService.Endpoint.PlanSet;
import com.vmware.admiral.request.pks.PKSClusterProvisioningTaskService.CallbackCompleteResponse;
import com.vmware.admiral.request.pks.PKSClusterProvisioningTaskService.PKSProvisioningTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.TaskState;

public class PKSClusterProvisioningTaskServiceTest extends PKSClusterOpBaseTest {

    @Test
    public void testGetFailedCallbackResponse() throws Throwable {
        PKSClusterProvisioningTaskService service = new PKSClusterProvisioningTaskService();
        Method m = service.getClass().getDeclaredMethod("getFailedCallbackResponse",
                PKSProvisioningTaskState.class);
        m.setAccessible(true);

        PKSProvisioningTaskState task = new PKSProvisioningTaskState();
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task.taskInfo = new TaskState();
        task.resourceLinks = new HashSet<>();
        task.resourceLinks.add("link1");

        CallbackCompleteResponse response = (CallbackCompleteResponse) m.invoke(service, task);

        assertNotNull(response);
        assertNotNull(response.resourceLinks);
        assertEquals(1, response.resourceLinks.size());
        assertEquals("link1", response.resourceLinks.iterator().next());
    }

    @Test
    public void testDoValidatePlanSelection() {
        final String serviceId = "test-task";
        PKSClusterProvisioningTaskService service = new PKSClusterProvisioningTaskService() {
            protected String getSelfId() {
                return serviceId;
            }
        };

        final String projectLink = QueryUtil.PROJECT_IDENTIFIER + "some-project";
        final String planName = "some-plan";
        final Set<String> tenantLinks = Collections.singleton(projectLink);

        Endpoint endpoint = new Endpoint();
        PlanSet planSet = new PlanSet();
        planSet.plans = Collections.singleton(planName);
        endpoint.planAssignments = Collections.singletonMap(projectLink, planSet);

        service.doValidatePlanSelection(planName, tenantLinks, endpoint);

        final String wrongPlan = "wrong-plan";
        try {
            service.doValidatePlanSelection(wrongPlan, tenantLinks, endpoint);
            fail("Should have thrown an exception because " + wrongPlan + " is not assigned");
        } catch (IllegalArgumentException ex) {
            String expectedError = String.format(
                    PKSClusterProvisioningTaskService.INVALID_PLAN_SELECTION_MESSAGE_FORMAT,
                    wrongPlan, serviceId);
            assertEquals(expectedError, ex.getMessage());
        }
    }

    @Test
    public void testCreateClusterFail() throws Throwable {
        request.customProperties.put(PKS_CLUSTER_NAME_PROP_NAME,
                MockPKSAdapterService.CLUSTER_NAME_CREATE_FAIL);

        MockPKSAdapterService.setLastAction(PKS_LAST_ACTION_CREATE);
        request = startRequest(request);
        waitForRequestToFail(request);
    }

}
