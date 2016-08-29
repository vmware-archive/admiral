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

package com.vmware.admiral.request.notifications;

import java.util.Collections;

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
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

public class NotificationsServiceTest extends RequestBaseTest {

    private static final String TENANT_LINK = "/tenants/qe";

    @Test
    public void testGetNotifications() throws Throwable {
        createEventLogState(null);
        createEventLogState(TENANT_LINK);
        createRequstStatus(null);
        createRequstStatus(TENANT_LINK);

        NotificationsAggregatorState notifications =
                getDocument(NotificationsAggregatorState.class, NotificationsService.SELF_LINK);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(2, notifications.recentEventLogsCount);
        Assert.assertEquals(2, notifications.activeRequestsCount);
    }

    @Test
    public void testGetNotificationsWithTenantLinks() throws Throwable {
        createEventLogState(null);
        createEventLogState(TENANT_LINK);
        createRequstStatus(null);
        createRequstStatus(TENANT_LINK);

        String notificationsUri = String.format("%s?%s", NotificationsService.SELF_LINK,
                UriUtils.buildUriQuery(MultiTenantDocument.FIELD_NAME_TENANT_LINKS, TENANT_LINK));
        NotificationsAggregatorState notifications =
                getDocument(NotificationsAggregatorState.class, notificationsUri);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(1, notifications.recentEventLogsCount);
        Assert.assertEquals(1, notifications.activeRequestsCount);
    }

    @Test
    public void testGetNoNotifications() throws Throwable {
        NotificationsAggregatorState notifications =
                getDocument(NotificationsAggregatorState.class, NotificationsService.SELF_LINK);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(0, notifications.recentEventLogsCount);
        Assert.assertEquals(0, notifications.activeRequestsCount);

        String notificationsUri = String.format("%s?%s", NotificationsService.SELF_LINK,
                UriUtils.buildUriQuery(MultiTenantDocument.FIELD_NAME_TENANT_LINKS, TENANT_LINK));
        notifications = getDocument(NotificationsAggregatorState.class, notificationsUri);
        Assert.assertNotNull(notifications);
        Assert.assertEquals(0, notifications.recentEventLogsCount);
        Assert.assertEquals(0, notifications.activeRequestsCount);
    }

    private void createEventLogState(String tenantLink) throws Throwable {
        EventLogState eventLogState = new EventLogState();
        eventLogState.resourceType = "Host";
        eventLogState.eventLogType = EventLogState.EventLogType.ERROR;
        eventLogState.description = "Host config failed.";
        if (tenantLink != null) {
            eventLogState.tenantLinks = Collections.singletonList(tenantLink);
        }

        EventLogState newEventLogState = doPost(eventLogState, EventLogService.FACTORY_LINK);
        Assert.assertNotNull(newEventLogState);

        addForDeletion(newEventLogState);
    }

    private void createRequstStatus(String tenantLink) throws Throwable {
        RequestStatus requestStatus = new RequestStatus();

        requestStatus.taskInfo = new TaskState();
        requestStatus.taskInfo.stage = TaskState.TaskStage.CREATED;

        if (tenantLink != null) {
            requestStatus.tenantLinks = Collections.singletonList(tenantLink);
        }

        RequestStatus newRequestStatus = doPost(requestStatus,
                RequestStatusFactoryService.SELF_LINK);
        Assert.assertNotNull(newRequestStatus);

        addForDeletion(newRequestStatus);
    }
}
