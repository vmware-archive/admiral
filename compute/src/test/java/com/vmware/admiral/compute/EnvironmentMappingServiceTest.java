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

package com.vmware.admiral.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.vmware.admiral.compute.EnvironmentMappingService.EnvironmentMappingState;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.xenon.common.UriUtils;

public class EnvironmentMappingServiceTest extends ComputeBaseTest {

    @Test
    public void testEnvironmentValuesLoadedOnStartUp() throws Throwable {
        String awsEnvLink = UriUtils.buildUriPath(EnvironmentMappingService.FACTORY_LINK, "aws");

        waitForServiceAvailability(awsEnvLink);
        EnvironmentMappingState envState = getDocument(EnvironmentMappingState.class, awsEnvLink);

        assertNotNull(envState);
        assertNotNull(envState.properties);
        assertEquals("t2.micro", envState.getMappingValue("instanceType", "small"));
        assertEquals("ami-6869aa05", envState.getMappingValue("imageType", "linux"));
    }

}
