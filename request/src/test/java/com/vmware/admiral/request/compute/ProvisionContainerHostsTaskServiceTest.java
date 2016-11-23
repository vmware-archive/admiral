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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;

public class ProvisionContainerHostsTaskServiceTest extends RequestBaseTest {
    // AWS instance type.
    private static final String T2_MICRO_INSTANCE_TYPE = "t2.micro";

    @Override
    @Before
    public void setUp() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
        super.setUp();
    }

    @Ignore("Fails around 10 times per day on Bellevue and Bellevue-gerrit jobs. Jira task - https://jira-hzn.eng.vmware.com/browse/VBV-858")
    @Test
    public void testProvisionDockerHostVMsOnAWS() throws Throwable {

        ComputeDescription awsComputeDesc = doPost(createAwsCoreOsComputeDescription(),
                ComputeDescriptionService.FACTORY_LINK);

        RequestBrokerState request = startRequest(
                createRequestState(awsComputeDesc.documentSelfLink));
        waitForRequestToComplete(request);
        request = getDocument(RequestBrokerState.class, request.documentSelfLink);

        assertEquals(3, request.resourceLinks.size());
    }

    public ComputeDescription createAwsCoreOsComputeDescription()
            throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc.name = "admvm" + String.valueOf(System.currentTimeMillis() / 1000);
        computeDesc.instanceType = T2_MICRO_INSTANCE_TYPE;
        computeDesc.supportedChildren = new ArrayList<>(
                Arrays.asList(ComputeType.DOCKER_CONTAINER.toString()));
        computeDesc.customProperties = new HashMap<>();
        computeDesc.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, "coreos");
        computeDesc.customProperties.put(ComputeProperties.ENDPOINT_LINK_PROP_NAME,
                endpoint.documentSelfLink);
        return computeDesc;
    }

    public static RequestBrokerState createRequestState(String resourceDescLink) {
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_HOST_TYPE.getName();
        request.operation = ProvisionContainerHostsTaskService.PROVISION_CONTAINER_HOSTS_OPERATITON;
        request.resourceDescriptionLink = resourceDescLink;
        request.resourceCount = 3;

        return request;
    }

    @Override
    protected RequestBrokerState startRequest(RequestBrokerState request) throws Throwable {
        RequestBrokerState requestState = doPost(request, RequestBrokerFactoryService.SELF_LINK);
        assertNotNull(requestState);
        return requestState;
    }
}
