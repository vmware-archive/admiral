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

package com.vmware.admiral.compute.endpoint;

import static junit.framework.TestCase.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.endpoint.EndpointHealthCheckTaskService.EndpointHealthCheckTaskState;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;

public class EndpointHealthCheckTaskServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(EndpointHealthCheckTaskService.FACTORY_LINK);
    }

    @Test
    public void testValid() throws Throwable {

        EndpointHealthCheckTaskState request = new EndpointHealthCheckTaskState();

        EndpointState endpoint = createEndpoint("test");
        EndpointAllocationTaskState endpointAllocationTaskState = allocateEndpoint(endpoint);

        request.endpointLink = endpointAllocationTaskState.endpointState.documentSelfLink;

        EndpointHealthCheckTaskState endpointHealthCheckTaskState = doPost(request,
                EndpointHealthCheckTaskService.FACTORY_LINK);

        waitFor(() -> getDocumentNoWait(EndpointHealthCheckTaskState.class, endpointHealthCheckTaskState.documentSelfLink) == null);

        ComputeService.PowerState powerState = getDocument(ComputeService.ComputeState.class,
                getDocument(EndpointState.class, request.endpointLink).computeLink).powerState;

        assertEquals(ComputeService.PowerState.ON, powerState);
    }

    @Test
    public void testInvalid() throws Throwable {

        EndpointHealthCheckTaskState request = new EndpointHealthCheckTaskState();

        EndpointState endpoint = createEndpoint("test");
        EndpointAllocationTaskState endpointAllocationTaskState = allocateEndpoint(endpoint);

        request.endpointLink = endpointAllocationTaskState.endpointState.documentSelfLink;

        // Set test to false. Based on this we will not set the isMock option and since we're
        // passing an endpoint with wrong credentials we expect the validation to fail
        // TODO this will cause problems if tests are executed in parallel
        DeploymentProfileConfig.getInstance().setTest(false);

        EndpointHealthCheckTaskState endpointHealthCheckTaskState = doPost(request,
                EndpointHealthCheckTaskService.FACTORY_LINK);

        waitFor(() -> getDocumentNoWait(EndpointHealthCheckTaskState.class, endpointHealthCheckTaskState.documentSelfLink) == null);

        ComputeService.PowerState powerState = getDocument(ComputeService.ComputeState.class,
                getDocument(EndpointState.class, request.endpointLink).computeLink).powerState;

        assertEquals(ComputeService.PowerState.OFF, powerState);
    }

}