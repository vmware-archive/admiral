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

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.xenon.common.LocalizableValidationException;

public class ContainerRecommendationTest extends RequestBaseTest {

    @Test
    public void testStateRecommend() throws Throwable {
        ContainerDescription containerDescription = TestRequestStateFactory
                .createContainerDescription();
        ContainerState containerState = TestRequestStateFactory.createContainer();
        containerState.powerState = PowerState.ERROR;

        List<ContainerDiff> containerDiffs = ContainerDiff.inspect(containerDescription, Lists
                .newArrayList(containerState));

        assertEquals(ContainerRecommendation.Recommendation.REDEPLOY, ContainerRecommendation
                .recommend(containerDiffs.get(0)));
    }

    @Test
    public void testEnvRecommend() throws Throwable {
        ContainerDescription containerDescription = TestRequestStateFactory
                .createContainerDescription();
        ContainerState containerState = TestRequestStateFactory.createContainer();
        containerState.env = new String[]{"a=b"};

        List<ContainerDiff> containerDiffs = ContainerDiff.inspect(containerDescription, Lists
                .newArrayList(containerState));

        assertEquals(ContainerRecommendation.Recommendation.REDEPLOY, ContainerRecommendation
                .recommend(containerDiffs.get(0)));
    }

    @Test
    public void testNoRecommend() throws Throwable {
        ContainerState containerState = TestRequestStateFactory.createContainer();
        ContainerDiff containerDiff = new ContainerDiff(containerState);

        assertEquals(ContainerRecommendation.Recommendation.NONE, ContainerRecommendation
                .recommend(containerDiff));
    }

    @Test(expected = LocalizableValidationException.class)
    public void testNullRecommend() throws Throwable {
        ContainerRecommendation.recommend(null);
    }
}
