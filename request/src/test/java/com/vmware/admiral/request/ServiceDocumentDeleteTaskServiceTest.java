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

package com.vmware.admiral.request;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.log.EventLogService.EventLogState.EventLogType;
import com.vmware.admiral.request.ServiceDocumentDeleteTaskService.ServiceDocumentDeleteTaskState;
import com.vmware.xenon.services.common.QueryTask;

public class ServiceDocumentDeleteTaskServiceTest extends RequestBaseTest {

    private ServiceDocumentDeleteTaskState request;

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
        request = new ServiceDocumentDeleteTaskState();
        request.deleteDocumentKind = EventLogState.class.getCanonicalName().replace(".", ":");
    }

    @Test
    public void testDeleteEvents() throws Throwable {
        int eventsCount = 5;
        createEventLogs(eventsCount);

        ServiceDocumentDeleteTaskState deleteTaskState = doPost(request, ServiceDocumentDeleteTaskService.FACTORY_LINK);

        waitForTaskSuccess(deleteTaskState.documentSelfLink, ServiceDocumentDeleteTaskService.ServiceDocumentDeleteTaskState.class);

        verifyZeroEvents();
    }

    @Test
    public void testDeleteEventsWhenNoneAreAvailable() throws Throwable {
        verifyZeroEvents();

        ServiceDocumentDeleteTaskState deleteTaskState = doPost(request, ServiceDocumentDeleteTaskService.FACTORY_LINK);

        waitForTaskSuccess(deleteTaskState.documentSelfLink, ServiceDocumentDeleteTaskService.ServiceDocumentDeleteTaskState.class);

        verifyZeroEvents();
    }

    private void createEventLogs(int count) throws Throwable {
        for (int i = 0; i < count; i++) {
            EventLogState event = new EventLogState();
            event.description = "Event";
            event.resourceType = "Res type";
            event.eventLogType = EventLogType.INFO;
            event = doPost(event, EventLogService.FACTORY_LINK);
        }

        // verify event logs
        boolean[] done = new boolean[1];
        Throwable[] ex = new Throwable[1];
        waitFor( () -> {
            QueryTask query = QueryUtil.buildQuery(EventLogState.class, true);
            QueryUtil.addCountOption(query);

            new ServiceDocumentQuery<EventLogState>(host, EventLogState.class)
                    .query(query, (r) -> {
                        if (r.hasException()) {
                            ex[0] = r.getException();
                            return;
                        }

                        done[0] = (count == r.getCount());
                    });

            return done[0] || ex[0] != null;
        });

        if (ex[0] != null) {
            Assert.fail(String.format("Could not retrieve created events: %s", ex[0].getMessage()));
        }
    }

    private void verifyZeroEvents() throws Throwable {
        Long[] zeroCount = new Long[1];
        Throwable[] ex = new Throwable[1];
        waitFor( () -> {
            QueryTask query = QueryUtil.buildQuery(EventLogState.class, true);
            QueryUtil.addCountOption(query);

            new ServiceDocumentQuery<EventLogState>(host, EventLogState.class)
                    .query(query, (r) -> {
                        if (r.hasException()) {
                            ex[0] = r.getException();
                            return;
                        }
                        zeroCount[0] = r.getCount();
                    });
            return zeroCount[0] != null;
        });

        if (ex[0] != null) {
            Assert.fail("Could not retrieve empty list of events after delete");
        }

        Assert.assertEquals(0, zeroCount[0].longValue());
    }

}