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

import java.lang.reflect.Method;
import java.util.HashSet;

import org.junit.Test;

import com.vmware.admiral.request.pks.PKSClusterProvisioningTaskService.CallbackCompleteResponse;
import com.vmware.admiral.request.pks.PKSClusterProvisioningTaskService.PKSProvisioningTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.TaskState;

public class PKSClusterProvisioningTaskServiceTest {

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

}