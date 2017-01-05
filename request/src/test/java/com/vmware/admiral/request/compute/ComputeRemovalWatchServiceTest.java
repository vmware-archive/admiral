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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;

/**
 * Tests for the {@link ComputeRemovalWatchService} service.
 */
public class ComputeRemovalWatchServiceTest extends ComputeRequestBaseTest {
    private RequestBrokerState request;

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
        DeploymentProfileConfig.getInstance().setTest(true);

        // create a single powered-on compute available for placement
        createVmGuestCompute(true);

        request = TestRequestStateFactory.createRequestState(ResourceType.COMPUTE_TYPE.getName(),
                hostDesc.documentSelfLink);
        request.tenantLinks = computeGroupPlacementState.tenantLinks;
        request.resourceCount = 1;
    }

    @After
    public void tearDown() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(false);
    }

    @Test
    public void testComputeRemovalResourceOperationCycleAfterAllocation() throws Throwable {
        request.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST, "true");

        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);
        assertNotNull(request);

        // verify the resources are created as expected:
        assertEquals(request.resourceCount, request.resourceLinks.size());
        List<String> computeStateLinks = findResourceLinks(ComputeState.class,
                request.resourceLinks);
        assertFalse("ComputeState not created", computeStateLinks.isEmpty());

        // mark computes as RETIRED
        ComputeState patchState = new ComputeState();
        patchState.lifecycleState = LifecycleState.RETIRED;
        for (String computeLink : computeStateLinks) {
            doPatch(patchState, computeLink);
        }

        // wait until compute states are removed
        waitFor(() -> findResourceLinks(ComputeState.class, computeStateLinks).isEmpty());
    }
}
