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
import static org.junit.Assert.fail;

import java.util.List;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.log.EventLogService.EventLogState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;

public class EventLogServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(EventLogService.FACTORY_LINK);
    }

    @Test
    public void testEventLog() throws Throwable {
        EventLogState eventLogState = new EventLogState();
        eventLogState.resourceType = "Host";
        eventLogState.eventLogType = EventLogState.EventLogType.ERROR;
        eventLogState.description = "Host config failed.";

        EventLogState newEventLogState = doPost(eventLogState, EventLogService.FACTORY_LINK);

        assertEquals(eventLogState.resourceType, newEventLogState.resourceType);
        assertEquals(eventLogState.eventLogType, newEventLogState.eventLogType);
        assertEquals(eventLogState.description, newEventLogState.description);
    }

    @Test
    public void testPutNotSupported() throws Throwable {
        EventLogState eventLogState = new EventLogState();
        eventLogState.resourceType = "Host";
        eventLogState.eventLogType = EventLogState.EventLogType.ERROR;
        eventLogState.description = "Host config failed.";

        EventLogState newEventLogState = doPost(eventLogState, EventLogService.FACTORY_LINK);
        try {
            doPut(newEventLogState);
            fail();
        } catch (IllegalStateException e) {
            assertEquals("Action not supported: PUT", e.getMessage());
        }
    }

    @Test
    public void testGetEventLogs() throws Throwable {
        host.log(Level.INFO, ">>>>>>>>>>>>>>>>>>>>>>Testing GET EventLogs <<<<<<<<<<<<<<<<<<<<<<");
        List<String> links = getDocumentLinksOfType(EventLogState.class);
        assertEquals("Unexpected number of initially existing entries", 0, links.size());

        EventLogState eventLogState = new EventLogState();
        eventLogState.resourceType = "Host";
        eventLogState.eventLogType = EventLogState.EventLogType.ERROR;
        eventLogState.description = "Host config failed.";

        EventLogState newEventLogState = doPost(eventLogState, EventLogService.FACTORY_LINK);

        assertEquals(eventLogState.resourceType, newEventLogState.resourceType);
        assertEquals(eventLogState.eventLogType, newEventLogState.eventLogType);
        assertEquals(eventLogState.description, newEventLogState.description);

        Operation get = Operation.createGet(UriUtils.buildUri(host, EventLogService.FACTORY_LINK))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        host.log(Level.WARNING, e.getMessage(), e);
                    } else {
                        List<String> documentLinks = o.getBody(ServiceDocumentQueryResult.class).documentLinks;
                        assertEquals("Unexpected number of existing entries", 1, documentLinks.size());
                        documentLinks.stream().forEach(l -> {
                            try {
                                EventLogState retrievedEventLog = getDocument(EventLogState.class, l);
                                assertEquals(eventLogState.resourceType, retrievedEventLog.resourceType);
                                assertEquals(eventLogState.eventLogType, retrievedEventLog.eventLogType);
                                assertEquals(eventLogState.description, retrievedEventLog.description);
                                host.completeIteration();
                            } catch (Throwable ex) {
                                host.log(Level.WARNING, ex.getMessage(), ex);
                                host.failIteration(ex);
                            }
                        });
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();
    }
}
