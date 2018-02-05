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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.log.EventLogService.EventLogState.EventLogType;
import com.vmware.admiral.request.ServiceDocumentDeleteTaskService.ServiceDocumentDeleteTaskState;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.TaskState;
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
        createEventLogs(eventsCount, null);

        ServiceDocumentDeleteTaskState deleteTaskState = doPost(request,
                ServiceDocumentDeleteTaskService.FACTORY_LINK);

        waitForTaskSuccess(deleteTaskState.documentSelfLink,
                ServiceDocumentDeleteTaskService.ServiceDocumentDeleteTaskState.class);

        verifyEventsCount(0);
    }

    @Test
    public void testDeleteDocumentsFailsWithInvalidSubStage() throws Throwable {

        // Make sure we are working with the service create on the host
        ServiceDocumentDeleteTaskService service = new ServiceDocumentDeleteTaskService();
        service.setHost(host);
        host.startFactory(service);

        waitForServiceAvailability(ServiceDocumentDeleteTaskService.FACTORY_LINK);

        // Create a state we are going to work with
        ServiceDocumentDeleteTaskState state = new ServiceDocumentDeleteTaskState();
        state.taskInfo = TaskState.createAsStarted();
        state.taskSubStage = DefaultSubStage.COMPLETED;
        state.serviceTaskCallback = ServiceTaskCallback.create(ServiceDocumentDeleteTaskService.FACTORY_LINK);
        state.deleteDocumentKind = "";

        // We need to actually create the state, so we have a documentSelfLink to update when the task fails
        ServiceDocumentDeleteTaskState created = doPost(state, ServiceDocumentDeleteTaskService.FACTORY_LINK);
        waitForTaskCompletion(created.documentSelfLink, ServiceDocumentDeleteTaskState.class);

        // get the documentSelfLink
        state.documentSelfLink = created.documentSelfLink;

        // Test that the task will fail with wrong substage
        service.handleStartedStagePatch(state);
        waitForTaskError(state.documentSelfLink, ServiceDocumentDeleteTaskState.class);
    }

    @Test
    public void testDeleteEventsWithGroups() throws Throwable {
        int eventsCountGroup1 = 5;
        int eventsCountGroup2 = 2;
        String group1 = "/tenants/qe/groups/" + UUID.randomUUID().toString();
        String group2 = "/tenants/qe/groups/" + UUID.randomUUID().toString();
        createEventLogs(eventsCountGroup1, Collections.singletonList(group1));
        createEventLogs(eventsCountGroup2, Collections.singletonList(group2));

        ServiceDocumentDeleteTaskState deleteTaskState = doPostWithProjectHeader(
                request, ServiceDocumentDeleteTaskService.FACTORY_LINK, group1,
                ServiceDocumentDeleteTaskState.class);

        waitForTaskSuccess(deleteTaskState.documentSelfLink,
                ServiceDocumentDeleteTaskService.ServiceDocumentDeleteTaskState.class);

        // Only event for group1 should get deleted
        verifyEventsCount(eventsCountGroup2);

        deleteTaskState = doPostWithProjectHeader(
                request, ServiceDocumentDeleteTaskService.FACTORY_LINK, group2,
                ServiceDocumentDeleteTaskState.class);
        waitForTaskSuccess(deleteTaskState.documentSelfLink,
                ServiceDocumentDeleteTaskService.ServiceDocumentDeleteTaskState.class);

        verifyEventsCount(0);
    }

    @Test
    public void testDeleteEventsWhenNoneAreAvailable() throws Throwable {
        verifyEventsCount(0);

        ServiceDocumentDeleteTaskState deleteTaskState = doPost(request,
                ServiceDocumentDeleteTaskService.FACTORY_LINK);

        waitForTaskSuccess(deleteTaskState.documentSelfLink,
                ServiceDocumentDeleteTaskService.ServiceDocumentDeleteTaskState.class);

        verifyEventsCount(0);
    }

    private void createEventLogs(int count, List<String> tenantLinks) throws Throwable {

        int[] initCount = new int[1];
        Throwable[] initEx = new Throwable[1];
        boolean[] initDone = new boolean[1];

        waitFor(() -> {

            QueryTask query = QueryUtil.buildQuery(EventLogState.class, true);
            QueryUtil.addCountOption(query);

            new ServiceDocumentQuery<EventLogState>(host, EventLogState.class)
                    .query(query, (r) -> {
                        if (r.hasException()) {
                            initEx[0] = r.getException();
                            return;
                        }

                        initCount[0] = (int) r.getCount();
                        initDone[0] = true;
                    });

            return initDone[0];
        });

        if (initEx[0] != null) {
            Assert.fail(
                    String.format("Could not retrieve created events: %s", initEx[0].getMessage()));
        }

        for (int i = 0; i < count; i++) {
            EventLogState event = new EventLogState();
            event.description = "Event";
            event.resourceType = "Res type";
            event.eventLogType = EventLogType.INFO;
            if (tenantLinks != null && !tenantLinks.isEmpty()) {
                event.tenantLinks = tenantLinks;
            }
            event = doPost(event, EventLogService.FACTORY_LINK);
        }

        // verify event logs
        boolean[] done = new boolean[1];
        Throwable[] ex = new Throwable[1];
        waitFor(() -> {
            QueryTask query = QueryUtil.buildQuery(EventLogState.class, true);
            QueryUtil.addCountOption(query);

            new ServiceDocumentQuery<EventLogState>(host, EventLogState.class)
                    .query(query, (r) -> {
                        if (r.hasException()) {
                            ex[0] = r.getException();
                            return;
                        }

                        done[0] = (initCount[0] + count == r.getCount());
                    });

            return done[0] || ex[0] != null;
        });

        if (ex[0] != null) {
            Assert.fail(String.format("Could not retrieve created events: %s", ex[0].getMessage()));
        }
    }

    private void verifyEventsCount(int count) throws Throwable {
        Long[] resultCount = new Long[1];
        Throwable[] ex = new Throwable[1];
        waitFor(() -> {
            QueryTask query = QueryUtil.buildQuery(EventLogState.class, true);
            QueryUtil.addCountOption(query);

            new ServiceDocumentQuery<EventLogState>(host, EventLogState.class)
                    .query(query, (r) -> {
                        if (r.hasException()) {
                            ex[0] = r.getException();
                            return;
                        }
                        resultCount[0] = r.getCount();
                    });
            return resultCount[0] != null;
        });

        if (ex[0] != null) {
            Assert.fail("Could not retrieve list of events after delete");
        }

        Assert.assertEquals(count, resultCount[0].longValue());
    }

}