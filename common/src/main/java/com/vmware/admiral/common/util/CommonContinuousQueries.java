/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.service.common.EventTopicService.EventTopicState;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService.ExtensibilitySubscription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceSubscriptionState.ServiceSubscriber;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Helper class allowing sharing continuous queries between multiple components for performance
 * reasons.
 */
public class CommonContinuousQueries {
    /**
     * Unique prefix for continuous query tasks. Changes upon restart which is OK because
     * continuous queries are not persisted.
     */
    private static final String QUERY_TASK_SELF_LINK_PREFIX = UUID.randomUUID().toString();

    /**
     * Default query expiration is 10 minutes and cannot be set to infinite. Here we choose a very
     * long expiration period which should be fine for all practical reasons.
     *
     * Note that since local query tasks are not persistent, this expiration interval restarts at
     * every host start.
     */
    private static final long QUERY_TASK_EXPIRATION_DAYS = 5 * 365; // 5 years

    /**
     * Supported common queries.
     */
    public static enum ContinuousQueryId {
        /**
         * Query for all {@link ComputeState}s.
         */
        COMPUTES,

        /**
         * Query for all {@link ComputeState}s in {@link LifecycleState.RETIRED} state.
         */
        RETIRED_COMPUTES,

        /**
         * Query for all {@link EventTopicState}s
         */
        EVENT_TOPICS,

        /**
         * Query for all {@link ExtensibilitySubscription}
         */
        EXTENSIBILITY_SUBSCRIPTIONS
    }

    /**
     * Subscribes a consumer to the given continuous query.
     */
    public static void subscribeTo(ServiceHost host, ContinuousQueryId queryId,
            Consumer<Operation> consumer) {
        QueryTask task = getQueryTask(host, queryId);
        Operation.createPost(host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(task)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null && o.getStatusCode() != Operation.STATUS_CODE_CONFLICT) {
                        host.log(Level.SEVERE, Utils.toString(e));
                        return;
                    }

                    String taskUriPath = UriUtils.buildUriPath(
                            ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, task.documentSelfLink);
                    Operation subscribePost = Operation.createPost(host, taskUriPath)
                            .setReferer(host.getUri())
                            .setCompletion((op, ex) -> {
                                if (ex != null) {
                                    host.log(Level.SEVERE, Utils.toString(ex));
                                }
                            });

                    host.log(Level.INFO, "Subscribing to a continuous task: %s", taskUriPath);
                    host.startSubscriptionService(subscribePost, consumer,
                            ServiceSubscriber.create(false));
                }).sendWith(host);
    }

    private static QueryTask getQueryTask(ServiceHost host, ContinuousQueryId queryId) {
        QueryTask task;

        switch (queryId) {
        case COMPUTES:
            Query computeQuery = Query.Builder.create()
                    .addKindFieldClause(ComputeState.class)
                    .addFieldClause(ServiceDocument.FIELD_NAME_OWNER, host.getId())
                    .build();
            task = QueryTask.Builder.create().addOption(QueryOption.CONTINUOUS)
                    .setQuery(computeQuery).build();
            break;
        case RETIRED_COMPUTES:
            Query retiredComputesQuery = Query.Builder.create()
                    .addKindFieldClause(ComputeState.class)
                    .addFieldClause(ServiceDocument.FIELD_NAME_OWNER, host.getId())
                    .addFieldClause(ComputeState.FIELD_NAME_LIFECYCLE_STATE, LifecycleState.RETIRED)
                    .build();
            task = QueryTask.Builder.create().addOption(QueryOption.CONTINUOUS)
                    .setQuery(retiredComputesQuery).build();
            break;
        case EVENT_TOPICS:
            Query eventTopicQuery = Query.Builder.create()
                    .addKindFieldClause(EventTopicState.class)
                    .addFieldClause(ServiceDocument.FIELD_NAME_OWNER, host.getId())
                    .build();
            task = QueryTask.Builder.create()
                    .addOptions(EnumSet.of(QueryOption.CONTINUOUS, QueryOption.EXPAND_CONTENT))
                    .setQuery(eventTopicQuery).build();
            break;
        case EXTENSIBILITY_SUBSCRIPTIONS:
            Query extensibilitySubscriptionQuery = Query.Builder.create()
                    .addKindFieldClause(ExtensibilitySubscription.class)
                    .addFieldClause(ServiceDocument.FIELD_NAME_OWNER, host.getId())
                    .build();
            task = QueryTask.Builder.create()
                    .addOptions(EnumSet.of(QueryOption.CONTINUOUS, QueryOption.EXPAND_CONTENT))
                    .setQuery(extensibilitySubscriptionQuery).build();
            break;
        default:
            throw new LocalizableValidationException("Unrecognized common query: " + queryId, "compute.quieries.unrecognized", queryId);
        }

        task.documentSelfLink = getTaskSelfLink(queryId);
        task.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(
                TimeUnit.DAYS.toMicros(QUERY_TASK_EXPIRATION_DAYS));

        return task;
    }

    private static String getTaskSelfLink(ContinuousQueryId queryId) {
        return QUERY_TASK_SELF_LINK_PREFIX + "-" + queryId.name().toLowerCase();
    }
}
