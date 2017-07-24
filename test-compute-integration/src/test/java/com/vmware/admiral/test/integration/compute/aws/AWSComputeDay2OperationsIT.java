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

package com.vmware.admiral.test.integration.compute.aws;

import java.util.Set;

import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;

public class AWSComputeDay2OperationsIT extends AwsComputeProvisionIT {

    @Override
    protected void doWithResources(Set<String> resourceLinks) throws Throwable {
        // Tests a single day 2 operation with an integration test.
        // The rest of Day2 ops should be tested with tests in the lower layer.
        doDay2Operation(resourceLinks, DAY_2_OPERATION_POWER_OFF, null);
        validateHostState(resourceLinks, PowerState.OFF);
    }

    @Override
    protected void provision(String resourceDescriptionLink) throws Throwable {
        Set<String> resourceLinks = null;
        RequestBrokerState provisionRequest = allocateAndProvision(resourceDescriptionLink);
        resourceLinks = provisionRequest.resourceLinks;
        try {
            doWithResources(resourceLinks);
            resourceLinks = testScaleOperations(resourceDescriptionLink);
        } finally {
            // create a host removal task - RequestBroker
            RequestBrokerState deleteRequest = new RequestBrokerState();
            deleteRequest.resourceType = getResourceType(resourceDescriptionLink);
            deleteRequest.resourceLinks = resourceLinks;
            deleteRequest.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
            RequestBrokerState cleanupRequest = postDocument(RequestBrokerFactoryService.SELF_LINK,
                    deleteRequest);

            waitForTaskToComplete(cleanupRequest.documentSelfLink);
        }
    }

}
