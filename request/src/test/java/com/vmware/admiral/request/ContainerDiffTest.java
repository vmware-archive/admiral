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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.request.ContainerDiff.ContainerPropertyDiff;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.xenon.common.LocalizableValidationException;

public class ContainerDiffTest extends RequestBaseTest {

    @Test
    public void testDiff() throws Throwable {
        ContainerDescription containerDescription = TestRequestStateFactory
                .createContainerDescription();
        ContainerState containerState = TestRequestStateFactory.createContainer();
        containerState.powerState = PowerState.ERROR;
        containerState.env = new String[]{"a=b"};

        List<ContainerDiff> containerDiffs = ContainerDiff.inspect(containerDescription, Lists
                .newArrayList(containerState));

        assertEquals(1, containerDiffs.size());
        assertEquals(2, containerDiffs.get(0).diffs.size());

        ContainerPropertyDiff envDiff = new ContainerPropertyDiff<>(ContainerDescription
                .FIELD_NAME_ENV, ContainerState.FIELD_NAME_ENV, containerDescription.env,
                containerState.env);
        ContainerPropertyDiff stateDiff = new ContainerPropertyDiff(null, ContainerState
                .FIELD_NAME_POWER_STATE, PowerState.RUNNING, PowerState.ERROR);
        assertTrue(containerDiffs.get(0).diffs.contains(envDiff));
        assertTrue(containerDiffs.get(0).diffs.contains(stateDiff));
    }

    @Test
    public void testEmptyDiff() throws Throwable {
        ContainerDescription containerDescription = TestRequestStateFactory
                .createContainerDescription();
        ContainerState containerState = TestRequestStateFactory.createContainer();
        containerState.env = containerDescription.env;

        List<ContainerDiff> containerDiffs = ContainerDiff.inspect(containerDescription, Lists
                .newArrayList(containerState));

        assertEquals(0, containerDiffs.size());
    }

    @Test(expected = LocalizableValidationException.class)
    public void testDiffWithNoDescriptionProvided() {
        ContainerDiff.inspect(null, new ArrayList<>());
    }

    @Test(expected = LocalizableValidationException.class)
    public void testDiffWithNoContainersProvided() {
        ContainerDiff.inspect(new ContainerDescription(), null);
    }

    @Test
    public void testEquals() {
        ContainerPropertyDiff envDiff = new ContainerPropertyDiff<>(ContainerDescription
                .FIELD_NAME_ENV, ContainerState.FIELD_NAME_ENV, new String[]{"a=b"},
                new String[]{"c=d"});
        ContainerPropertyDiff envDiff2 = new ContainerPropertyDiff<>(ContainerDescription
                .FIELD_NAME_ENV, ContainerState.FIELD_NAME_ENV, new String[]{"a=b"},
                new String[]{"c=d"});
        assertTrue(Arrays.equals(new String[]{"a=b"}, new String[]{"a=b"}));
        assertTrue(envDiff.equals(envDiff2));
        assertEquals(envDiff, envDiff2);
        System.out.println(envDiff.hashCode());
        System.out.println(envDiff2.hashCode());
        assertTrue(envDiff.hashCode() == envDiff2.hashCode());
    }
}
