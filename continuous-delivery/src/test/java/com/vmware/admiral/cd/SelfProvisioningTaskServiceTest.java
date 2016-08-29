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

package com.vmware.admiral.cd;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.vmware.admiral.cd.SelfProvisioningTaskService.SelfProvisioningTaskState;
import com.vmware.admiral.cd.SelfProvisioningTaskService.SelfProvisioningTaskState.EndpointType;
import com.vmware.admiral.common.test.CommonTestStateFactory;

public class SelfProvisioningTaskServiceTest extends ContinuousDeliveryBaseTest {
    private static final String SECURITY_GROUP = "cell-manager-security-group";

    // AWS instance type.
    private static final String T2_MICRO_INSTANCE_TYPE = "t2.micro";

    // AWS east-1 zone ID
    private static final String EAST_1_ZONE_ID = "us-east-1";

    @Test
    public void testSelfProvisioningOnAWS() throws Throwable {
        SelfProvisioningTaskState stateTask = new SelfProvisioningTaskState();
        stateTask.endpointAuthKey = "aws-user-key";
        stateTask.endpointAuthPrivateKey = CommonTestStateFactory
                .getFileContent("docker-host-private-key.PEM");
        stateTask.availabilityZoneId = EAST_1_ZONE_ID;
        stateTask.securityGroup = SECURITY_GROUP;
        stateTask.endpointType = EndpointType.AWS;
        stateTask.computeInstanceType = T2_MICRO_INSTANCE_TYPE;
        stateTask.clusterSize = 3;

        stateTask = startTask(stateTask);

        // in unit test just validate the request is created
        assertNotNull(stateTask.documentSelfLink);
    }

    protected SelfProvisioningTaskState startTask(SelfProvisioningTaskState task)
            throws Throwable {
        SelfProvisioningTaskState requestState = doPost(task,
                SelfProvisioningTaskService.FACTORY_LINK);
        assertNotNull(requestState);
        return requestState;
    }
}
