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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.function.Predicate;

import org.junit.Test;

import com.vmware.admiral.cd.SelfProvisioningTaskService;
import com.vmware.admiral.cd.SelfProvisioningTaskService.SelfProvisioningTaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

/**
 * Self provision on AWS
 *      an integration test (although the name does not end with IT on purpose).
 *      Upon execution a new vm will be provisioned in amazon EC2 (credentials needed)
 *      and admiral will be provisioned on it.
 */
public class SelfProvisionOnAmazonCI extends BaseIntegrationSupportIT {

    private static final String awsAccessKey = System.getProperty("aws.access.key");
    private static final String awsSecretKey = System.getProperty("aws.secret.key");

    protected static final int STATE_CHANGE_WAIT_POLLING_RETRY_COUNT = Integer.getInteger(
            "test.state.change.wait.retry.count", 60);
    protected static final int STATE_CHANGE_WAIT_POLLING_PERIOD_MILLIS = Integer.getInteger(
            "test.state.change.wait.period.millis", 10000);

    // Security group for AWS. Fixed for now
    //FIXME: This should be the current configured security group in AWS.
    private static final String SECURITY_GROUP = "cell-manager-security-group";

    // AWS instance type.
    private static final String T2_MICRO_INSTANCE_TYPE = "t2.micro";
    protected static final String T2_LARGE_INSTANCE_TYPE = "t2.large";

    // AWS east-1 zone ID
    public static final String EAST_1_ZONE_ID = "us-east-1";
    // AWS west-1 zone ID
    public static final String WEST_1_ZONE_ID = "us-west-1";

    // Core OS image for us-east-1
    protected static final String EAST_1_IMAGE_ID = "ami-cbfdb2a1"; // CoreOS Alpha 983.0.0
    protected static final String WEST_1_IMAGE_ID = "ami-0eacc46e";

    @Test
    public void testSelfProvisioning() throws Exception {

        logger.info("0. Start self provisioning on AWS test");

        SelfProvisioningTaskState request = new SelfProvisioningTaskState();

        request.endpointAuthKey = awsAccessKey;
        request.endpointAuthPrivateKey = awsSecretKey;
        request.availabilityZoneId = EAST_1_ZONE_ID;
        request.endpointType = SelfProvisioningTaskState.EndpointType.AWS;
        request.computeInstanceType = T2_MICRO_INSTANCE_TYPE;
        request.securityGroup = SECURITY_GROUP;

        logger.info("1. Post request");
        request = postDocument(
                SelfProvisioningTaskService.FACTORY_LINK,
                request, TestDocumentLifeCycle.NO_DELETE
        );

        logger.info("2. Wait request to complete");

        waitForStateChange(request.documentSelfLink, (body) -> {
            SelfProvisioningTaskState state = Utils.fromJson(body, SelfProvisioningTaskState.class);

            return state != null &&
                    (SelfProvisioningTaskState.SubStage.COMPLETED .equals(state.taskSubStage)
                            || SelfProvisioningTaskState.SubStage.ERROR .equals(state.taskSubStage));
        });

        request = getDocument(request.documentSelfLink, SelfProvisioningTaskState.class);

        logger.info("3. Complete. Validating...");

        assertEquals("task stage must be finished", request.taskInfo.stage, TaskStage.FINISHED);
        assertEquals("task sub-stage must be completed",
                request.taskSubStage,
                SelfProvisioningTaskState.SubStage.COMPLETED);
        assertNotNull("admiral containers provisioned empty", request.resourceLinks);
        assertEquals("admiral containers provisioned count", request.resourceLinks.size(), 3);

        logger.info("4. Done!");
    }

    protected static void waitForStateChange(String documentSelfLink,
            Predicate<String> predicate) throws Exception {

        for (int i = 0; i < STATE_CHANGE_WAIT_POLLING_RETRY_COUNT; i++) {
            Thread.sleep(STATE_CHANGE_WAIT_POLLING_PERIOD_MILLIS);
            String body = sendRequest(SimpleHttpsClient.HttpMethod.GET, documentSelfLink, null);
            if (predicate.test(body)) {
                return;
            }
        }

        throw new RuntimeException(
                String.format("Failed waiting for state change on link %s", documentSelfLink));
    }

}
