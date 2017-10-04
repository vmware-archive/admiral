/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.notifications;

import java.net.URI;
import java.util.ArrayList;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestStatusFactoryService;
import com.vmware.admiral.request.RequestStatusService.RequestStatus;
import com.vmware.admiral.request.notification.NotificationsService;
import com.vmware.admiral.request.notification.NotificationsService.NotificationsAggregatorState;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class NotificationsServiceTest extends RequestBaseTest {

    private static final String TENANT_LINK = "/tenants/qe";
    private static final String PROJECT_LINK = "/projects/qe-project";
    private static final String PROJECT_LINK_2 = "/projects/dev-project";

    @Test
    public void testGetNotifications() throws Throwable {
        init();

        NotificationsAggregatorState notifications;

        notifications = getNotifications(NotificationsService.SELF_LINK, null);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(5, notifications.recentEventLogsCount);
        Assert.assertEquals(5, notifications.activeRequestsCount);

        notifications = getNotifications(NotificationsService.SELF_LINK, PROJECT_LINK);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(2, notifications.recentEventLogsCount);
        Assert.assertEquals(2, notifications.activeRequestsCount);

        notifications = getNotifications(NotificationsService.SELF_LINK, PROJECT_LINK_2);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(1, notifications.recentEventLogsCount);
        Assert.assertEquals(1, notifications.activeRequestsCount);
    }

    @Test
    public void testGetNotificationsWithTenantLinks() throws Throwable {
        init();

        NotificationsAggregatorState notifications;
        String notificationsUri = String.format("%s?%s", NotificationsService.SELF_LINK,
                UriUtils.buildUriQuery(MultiTenantDocument.FIELD_NAME_TENANT_LINKS, TENANT_LINK));

        notifications = getNotifications(notificationsUri, null);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(3, notifications.recentEventLogsCount);
        Assert.assertEquals(3, notifications.activeRequestsCount);

        notifications = getNotifications(notificationsUri, PROJECT_LINK);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(1, notifications.recentEventLogsCount);
        Assert.assertEquals(1, notifications.activeRequestsCount);

        notifications = getNotifications(notificationsUri, PROJECT_LINK_2);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(1, notifications.recentEventLogsCount);
        Assert.assertEquals(1, notifications.activeRequestsCount);
    }

    @Test
    public void testGetNoNotifications() throws Throwable {
        NotificationsAggregatorState notifications;

        notifications = getNotifications(NotificationsService.SELF_LINK, null);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(0, notifications.recentEventLogsCount);
        Assert.assertEquals(0, notifications.activeRequestsCount);

        notifications = getNotifications(NotificationsService.SELF_LINK, PROJECT_LINK);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(0, notifications.recentEventLogsCount);
        Assert.assertEquals(0, notifications.activeRequestsCount);

        String notificationsUri = String.format("%s?%s", NotificationsService.SELF_LINK,
                UriUtils.buildUriQuery(MultiTenantDocument.FIELD_NAME_TENANT_LINKS, TENANT_LINK));
        notifications = getNotifications(notificationsUri, PROJECT_LINK);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(0, notifications.recentEventLogsCount);
        Assert.assertEquals(0, notifications.activeRequestsCount);
    }

    private void init() throws Throwable {
        createEventLogState(null, null);
        createEventLogState(null, PROJECT_LINK);
        createEventLogState(TENANT_LINK, null);
        createEventLogState(TENANT_LINK, PROJECT_LINK);
        createEventLogState(TENANT_LINK, PROJECT_LINK_2);
        createRequestStatus(null, null);
        createRequestStatus(null, PROJECT_LINK);
        createRequestStatus(TENANT_LINK, null);
        createRequestStatus(TENANT_LINK, PROJECT_LINK);
        createRequestStatus(TENANT_LINK, PROJECT_LINK_2);
    }

    private void createEventLogState(String tenantLink, String projectLink) throws Throwable {
        EventLogState eventLogState = new EventLogState();
        eventLogState.resourceType = "Host";
        eventLogState.eventLogType = EventLogState.EventLogType.ERROR;
        eventLogState.description = "Host config failed.";

        eventLogState.tenantLinks = new ArrayList<>(2);
        if (tenantLink != null) {
            eventLogState.tenantLinks.add(tenantLink);
        }
        if (projectLink != null) {
            eventLogState.tenantLinks.add(projectLink);
        }
        if (eventLogState.tenantLinks.size() == 0) {
            eventLogState.tenantLinks = null;
        }

        EventLogState newEventLogState = doPost(eventLogState, EventLogService.FACTORY_LINK);
        Assert.assertNotNull(newEventLogState);

        addForDeletion(newEventLogState);
    }

    private void createRequestStatus(String tenantLink, String projectLink) throws Throwable {
        RequestStatus requestStatus = new RequestStatus();

        requestStatus.taskInfo = new TaskState();
        requestStatus.taskInfo.stage = TaskState.TaskStage.CREATED;

        requestStatus.tenantLinks = new ArrayList<>(2);
        if (tenantLink != null) {
            requestStatus.tenantLinks.add(tenantLink);
        }
        if (projectLink != null) {
            requestStatus.tenantLinks.add(projectLink);
        }
        if (requestStatus.tenantLinks.size() == 0) {
            requestStatus.tenantLinks = null;
        }

        RequestStatus newRequestStatus = doPost(requestStatus,
                RequestStatusFactoryService.SELF_LINK);
        Assert.assertNotNull(newRequestStatus);

        addForDeletion(newRequestStatus);
    }

    private NotificationsAggregatorState getNotifications(String selfLink, String projectLink)
            throws Throwable {
        TestContext ctx = testCreate(1);
        URI uri = UriUtils.buildUri(host, selfLink);
        NotificationsAggregatorState[] result = new NotificationsAggregatorState[1];
        Operation get = Operation
                .createGet(uri)
                .setReferer(host.getReferer())
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.log(Level.WARNING, "Can't load document %s. Error: %s",
                                        selfLink, e.toString());
                            } else {
                                result[0] = o.getBody(NotificationsAggregatorState.class);
                            }
                            ctx.completeIteration();
                        });
        if (projectLink != null) {
            setProjectHeader(projectLink, get);
        }

        host.send(get);
        ctx.await();
        return result[0];
    }

}
