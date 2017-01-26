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

import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.xenon.common.LocalizableValidationException;

public class ContainerStateInspectorTest extends RequestBaseTest {

    @Test
    public void testDiff() throws Throwable {
        ContainerDescription containerDescription = TestRequestStateFactory
                .createContainerDescription();

        // create 10 containers, half of them with context_id = A, the others with context_id = B
        // In each context there are 2 containers in ERROR state.
        Map<String, List<ContainerState>> containersPerContextId = new HashMap<>();
        String contextA = "A";
        String contextB = "B";
        List<ContainerState> containersInA = new ArrayList<>();
        List<ContainerState> containersInB = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            ContainerState cs = TestRequestStateFactory.createContainer();
            if (i < 5) {
                cs.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextA);
                if (i < 2) {
                    cs.powerState = PowerState.ERROR;
                }

                containersInA.add(cs);
            } else {
                cs.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextB);
                if (i < 7) {
                    cs.powerState = PowerState.ERROR;
                }

                containersInB.add(cs);
            }
        }

        containersPerContextId.put(contextA, containersInA);
        containersPerContextId.put(contextB, containersInB);

        // invoke inspect method
        ContainerStateInspector inspectedContainerStates = ContainerStateInspector.inspect(containerDescription,
                containersPerContextId);
        assertNotNull(inspectedContainerStates);

        // assert container description not null
        assertNotNull(inspectedContainerStates.getContainerDescription());

        // assert actual containers per context_id not null
        assertNotNull(inspectedContainerStates.getActualContainersPerContextId());

        // assert unhealthy containers per context_id not null
        assertNotNull(inspectedContainerStates.getUnhealthyContainersPerContextId());

        // assert unhealthy containers number in context_A
        List<ContainerState> containersInContextA = inspectedContainerStates.getUnhealthyContainersPerContextId()
                .get(contextA);
        assertNotNull(containersInContextA);
        assertEquals(2, containersInContextA.size());
        containersInContextA.stream().forEach(c -> {
            assertEquals(PowerState.ERROR, c.powerState);
        });

        // assert unhealthy containers number in context_B
        List<ContainerState> containersInContextB = inspectedContainerStates.getUnhealthyContainersPerContextId()
                .get(contextB);
        assertNotNull(containersInContextB);
        assertEquals(2, containersInContextB.size());
        containersInContextB.stream().forEach(c -> {
            assertEquals(PowerState.ERROR, c.powerState);
        });
    }

    @Test(expected = LocalizableValidationException.class)
    public void testDiffWithNoDescriptionProvided() {
        ContainerStateInspector.inspect(null, new HashMap<>());
    }

    @Test(expected = LocalizableValidationException.class)
    public void testDiffWithNoContainersProvided() {
        ContainerStateInspector.inspect(new ContainerDescription(), null);
    }
}
