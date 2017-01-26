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

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;

public class ContainerRecommendationTest extends RequestBaseTest {

    private ContainerDescription containerDescription;
    private Map<String, List<ContainerState>> containersPerContextId;
    private String contextA;
    private String contextB;

    @Before
    public void init() {
        containerDescription = TestRequestStateFactory.createContainerDescription();

        // create ContainerDescriptionDiff with 4 containers per description,
        // grouped by context_id on 2 groups and all of the containers are in ERROR state
        contextA = "context_A";
        contextB = "context_B";
        List<ContainerState> containersPerContextA = new ArrayList<>();
        List<ContainerState> containersPerContextB = new ArrayList<>();

        containersPerContextId = new HashMap<>();

        for (int i = 0; i < 4; i++) {
            ContainerState cs = TestRequestStateFactory.createContainer();
            cs.powerState = PowerState.ERROR;

            if (i < 2) {
                cs.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextA);
                containersPerContextA.add(cs);
            } else {
                cs.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextB);
                containersPerContextB.add(cs);
            }
        }

        containersPerContextId.put(contextA, containersPerContextA);
        containersPerContextId.put(contextB, containersPerContextB);
    }

    @Test
    public void testRecommendationContainersToBeRemoved() {

        ContainerStateInspector inspectedContainerStates = ContainerStateInspector.inspect(containerDescription, containersPerContextId);

        // do recommendation
        ContainerRecommendation recommendation = ContainerRecommendation.recommend(inspectedContainerStates);
        assertNotNull(recommendation);

        // assert container description is not null
        assertNotNull(recommendation.getContainerDescription());


        // assert containers to be removed
        Map<String, List<ContainerState>> numberOfContainersToBeClusteredPerContextId = recommendation.getContainersToBeRemoved();
        assertNotNull(numberOfContainersToBeClusteredPerContextId);
        // assert number of containers to be provisioned in context_A
        assertEquals(2, numberOfContainersToBeClusteredPerContextId.get(contextA).size());
        // assert number of containers to be provisioned in context_B
        assertEquals(2, numberOfContainersToBeClusteredPerContextId.get(contextB).size());

        // assert recommendation type
        assertEquals(ContainerRecommendation.Recommendation.REDEPLOY, recommendation.getRecommendation());
    }
}
