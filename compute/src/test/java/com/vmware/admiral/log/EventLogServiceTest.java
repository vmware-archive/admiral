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

package com.vmware.admiral.log;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.log.EventLogService.EventLogState;

public class EventLogServiceTest extends ComputeBaseTest {
    private List<String> eventLogsForDeletion;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(EventLogService.FACTORY_LINK);
        eventLogsForDeletion = new ArrayList<>();
    }

    @After
    public void tearDown() throws Throwable {
        for (String selfLink : eventLogsForDeletion) {
            delete(selfLink);
        }
    }

    @Test
    public void testEventLog() throws Throwable {
        EventLogState eventLogState = new EventLogState();
        eventLogState.resourceType = "Host";
        eventLogState.eventLogType = EventLogState.EventLogType.ERROR;
        eventLogState.description = "Host config failed.";

        EventLogState newEventLogState = doPost(eventLogState, EventLogService.FACTORY_LINK);
        eventLogsForDeletion.add(newEventLogState.documentSelfLink);

        assertEquals(eventLogState.resourceType, newEventLogState.resourceType);
        assertEquals(eventLogState.eventLogType, newEventLogState.eventLogType);
        assertEquals(eventLogState.description, newEventLogState.description);
    }
}
