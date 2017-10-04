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

package com.vmware.admiral.request.notification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.request.RequestStatusService.RequestStatus;
import com.vmware.admiral.service.common.AbstractTaskStatefulService.TaskStatusState;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Helper service for aggregating notifications displayed in UI for both event logs and
 * request tasks.
 */
public class NotificationsService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.NOTIFICATIONS;

    public static final long EVENTS_TIME_INTERVAL_MICROS = TimeUnit.MICROSECONDS
            .convert(Long.getLong(
                    "com.vmware.admiral.log.notificationsaggregator.eventstimeinterval.minutes",
                    20),
                    TimeUnit.MINUTES);

    public static class NotificationsAggregatorState {
        public long recentEventLogsCount;
        public long activeRequestsCount;
    }

    @Override
    public void handleGet(Operation get) {
        Map<String, String> queryParams = UriUtils.parseUriQueryParams(get.getUri());
        String tenantLink = queryParams.get(MultiTenantDocument.FIELD_NAME_TENANT_LINKS);

        List<String> tenantLinks = tenantLink == null ?
                new ArrayList<>(1) :
                new ArrayList<>(Arrays.asList(tenantLink.split("\\s*,\\s*")));

        NotificationsAggregatorState state = new NotificationsAggregatorState();

        String projectLink = OperationUtil.extractProjectFromHeader(get);
        if (projectLink != null && projectLink.length() > 0) {
            // add project link to filter result
            tenantLinks.add(projectLink);
        }

        QueryTask requestStatusQuery = buildRequestStatusQuery(tenantLinks);
        new ServiceDocumentQuery<RequestStatus>(getHost(), RequestStatus.class)
                .query(requestStatusQuery, (r) -> {
                    if (r.hasException()) {
                        get.fail(r.getException());
                        return;
                    } else {
                        state.activeRequestsCount = r.getCount();

                        QueryTask eventLogQuery = buildEventLogCountQuery(tenantLinks);
                        new ServiceDocumentQuery<EventLogState>(getHost(), EventLogState.class)
                                .query(eventLogQuery, (counter) -> {
                                    if (counter.hasException()) {
                                        get.fail(counter.getException());
                                        return;
                                    }

                                    state.recentEventLogsCount = counter.getCount();

                                    get.setBody(state);
                                    get.complete();
                                });
                    }
                });
    }

    private QueryTask buildEventLogCountQuery(List<String> tenantLinks) {
        QueryTask qt = QueryUtil.buildQuery(EventLogState.class, true);

        if (!tenantLinks.isEmpty()) {
            qt.querySpec.query.addBooleanClause(QueryUtil.addTenantGroupAndUserClause(tenantLinks));
        }

        long nMinutesAgo = Utils.fromNowMicrosUtc(-EVENTS_TIME_INTERVAL_MICROS);
        QueryTask.Query numOfInstancesClause = new QueryTask.Query()
                .setTermPropertyName(ServiceDocument.FIELD_NAME_UPDATE_TIME_MICROS)
                .setNumericRange(NumericRange.createLongRange(nMinutesAgo,
                        Long.MAX_VALUE, true, false))
                .setTermMatchType(MatchType.TERM);
        qt.querySpec.query.addBooleanClause(numOfInstancesClause);

        QueryTask.Query eventTypeClause = new QueryTask.Query()
                .setTermPropertyName(EventLogState.FIELD_NAME_EVENT_LOG_TYPE)
                .setTermMatchValue(EventLogState.EventLogType.INFO.toString())
                .setTermMatchType(MatchType.TERM);
        eventTypeClause.occurance = Occurance.MUST_NOT_OCCUR;
        qt.querySpec.query.addBooleanClause(eventTypeClause);

        QueryUtil.addCountOption(qt);

        return qt;
    }

    private QueryTask buildRequestStatusQuery(List<String> tenantLinks) {
        QueryTask requestStatusQuery = QueryUtil.buildQuery(RequestStatus.class, true);
        QueryTask.Query runningTasksClause = new QueryTask.Query();

        if (!tenantLinks.isEmpty()) {
            requestStatusQuery.querySpec.query.addBooleanClause(QueryUtil
                    .addTenantGroupAndUserClause(tenantLinks));
        }

        QueryTask.Query taskCreatedClause = new QueryTask.Query()
                .setTermPropertyName(TaskStatusState.FIELD_NAME_TASK_INFO + ".stage")
                .setTermMatchValue(TaskState.TaskStage.CREATED.toString());
        taskCreatedClause.occurance = Occurance.SHOULD_OCCUR;
        runningTasksClause.addBooleanClause(taskCreatedClause);

        QueryTask.Query taskStartedClause = new QueryTask.Query()
                .setTermPropertyName(TaskStatusState.FIELD_NAME_TASK_INFO + ".stage")
                .setTermMatchValue(TaskState.TaskStage.STARTED.toString());
        taskStartedClause.occurance = Occurance.SHOULD_OCCUR;
        runningTasksClause.addBooleanClause(taskStartedClause);

        requestStatusQuery.querySpec.query.addBooleanClause(runningTasksClause);
        QueryUtil.addCountOption(requestStatusQuery);

        return requestStatusQuery;
    }

}
