/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.log.EventLogService.EventLogState.EventLogType;
import com.vmware.xenon.common.ServiceHost.Arguments;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

public class EventLogServiceTest extends ComputeBaseTest {

    public static final String ACTION_PUT_NOT_SUPPORTED = "Action 'PUT' not supported. Should've failed.";

    private VerificationHost savedState = null;
    private EventLogState eventLogState = null;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(EventLogService.FACTORY_LINK);
        verifyHostIsWithoutAuthorization();
        eventLogState = createEventLog();

        List<String> allStatesLinks = getDocumentLinksOfType(EventLogState.class);
        assertEquals("Unexpected number of initially existing entries", 0, allStatesLinks.size());
    }

    @After
    public void after() throws Throwable {
        List<String> allStatesLinks = getDocumentLinksOfType(EventLogState.class);
        assertEquals("Unexpected number of left over entries", 0, allStatesLinks.size());

        returnHostState();
    }

    @Test
    public void testPost() throws Throwable {
        EventLogState createdState = doPost(eventLogState, EventLogFactoryService.SELF_LINK);
        assertNotNull(createdState.documentSelfLink);
        assertEventLogEquals(eventLogState, createdState);

        List<String> allStatesLinks = getDocumentLinksOfType(EventLogState.class);
        assertEquals("Unexpected number of existing entries",1, allStatesLinks.size());

        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
    }

    @Test
    public void testPutNotSupported() throws Throwable {
        try {
            doPut(eventLogState);
            fail(ACTION_PUT_NOT_SUPPORTED);
        } catch (IllegalStateException e) {
            assertEquals("Action not supported: PUT", e.getMessage());
        }
    }

    @Test
    public void testGetSingleEventLog() throws Throwable {
        EventLogState createdState = doPost(eventLogState, EventLogFactoryService.SELF_LINK);

        EventLogState retrievedState = getDocument(EventLogState.class, createdState.documentSelfLink);
        assertEventLogEquals(createdState, retrievedState);

        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
    }

    @Test
    public void testGetAllEventLogs() throws Throwable {
        EventLogState createdState1 = doPost(eventLogState, EventLogService.FACTORY_LINK);
        EventLogState createdState2 = doPost(eventLogState, EventLogService.FACTORY_LINK);
        EventLogState createdState3 = doPost(eventLogState, EventLogService.FACTORY_LINK);

        List<EventLogState> eventLogs = getDocumentsOfType(EventLogState.class);
        assertNotNull(eventLogs);
        eventLogs.forEach( eventLog -> assertEventLogEquals(createdState1, eventLog));

        doDelete(UriUtils.buildUri(host, createdState1.documentSelfLink), false);
        doDelete(UriUtils.buildUri(host, createdState2.documentSelfLink), false);
        doDelete(UriUtils.buildUri(host, createdState3.documentSelfLink), false);
    }

    @Test
    public void testDelete() throws Throwable {
        EventLogState createdState = doPost(eventLogState, EventLogService.FACTORY_LINK);

        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
        EventLogState retrievedState = getDocumentNoWait(EventLogState.class, createdState.documentSelfLink);
        assertNull(retrievedState);
    }

    private void verifyHostIsWithoutAuthorization() throws Exception {
        if (host.isAuthorizationEnabled()) {
            savedState = host;
            host = VerificationHost.create(new Arguments());
        }
    }

    private void returnHostState() {
        if (savedState != null) {
            host.stopHost(host);
            host = savedState;
        }
    }

    private EventLogState createEventLog() {
        EventLogState eventLog = new EventLogState();
        eventLog.eventLogType = EventLogType.ERROR;
        eventLog.resourceType = "Host config";
        eventLog.description = "Host config failed.";

        return eventLog;
    }

    private void assertEventLogEquals(EventLogState state1, EventLogState state2) {
        assertNotNull(state1);
        assertNotNull(state2);
        assertEquals(state1.eventLogType, state2.eventLogType);
        assertEquals(state1.resourceType, state2.resourceType);
        assertEquals(state1.description, state2.description);
    }
}
