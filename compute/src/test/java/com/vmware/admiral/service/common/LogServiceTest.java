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

package com.vmware.admiral.service.common;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.service.common.LogService.LogServiceState;

public class LogServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(LogService.FACTORY_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
    }

    @Test
    public void testLog() throws Throwable {
        LogServiceState logState = new LogServiceState();
        logState.logs = "Test-file234".getBytes();

        LogServiceState newLogState = doPost(logState, LogService.FACTORY_LINK);

        assertEquals(new String(logState.logs), new String(newLogState.logs));
    }
}
