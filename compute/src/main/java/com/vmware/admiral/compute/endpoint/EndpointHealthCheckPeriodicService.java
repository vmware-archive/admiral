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

package com.vmware.admiral.compute.endpoint;

import static java.util.concurrent.TimeUnit.HOURS;

import java.util.HashSet;
import java.util.Set;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.endpoint.EndpointHealthCheckTaskService.EndpointHealthCheckTaskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

public class EndpointHealthCheckPeriodicService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.ENDPOINT_PERIODIC_HEALTHCHECK;

    public static final long MAINTENANCE_INTERVAL_MICROS = Long.getLong(
            "dcp.management.endpoint.health.check.compute.periodic.maintenance.period.micros",
            HOURS.toMicros(1));

    public EndpointHealthCheckPeriodicService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(MAINTENANCE_INTERVAL_MICROS);
    }

    @Override
    public void handlePeriodicMaintenance(Operation post) {
        getEndpointLinks().thenAccept(endpointLinks -> {
            endpointLinks.stream().forEach(endpointLink -> {
                EndpointHealthCheckTaskState state = new EndpointHealthCheckTaskState();
                state.endpointLink = endpointLink;
                state.documentSelfLink = UriUtils.getLastPathSegment(endpointLink);

                Operation startTaskOperation = Operation
                        .createPost(this, EndpointHealthCheckTaskService.FACTORY_LINK)
                        .setBody(state);

                this.sendWithDeferredResult(startTaskOperation);
            });
        });

        post.complete();
    }

    private DeferredResult<Set<String>> getEndpointLinks() {
        QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(EndpointState.class);

        QueryTask queryTask = QueryTask.Builder.create()
                .addOption(QueryTask.QuerySpecification.QueryOption.OWNER_SELECTION)
                .setQuery(queryBuilder.build()).build();

        DeferredResult<Set<String>> result = new DeferredResult<>();

        Set<String> endpointLinks = new HashSet<>();
        new ServiceDocumentQuery<>(getHost(), EndpointState.class).query(queryTask, r -> {
            if (r.hasException()) {
                result.fail(r.getException());
            } else if (r.hasResult()) {
                endpointLinks.add(r.getDocumentSelfLink());
            } else {
                result.complete(endpointLinks);
            }
        });

        return result;
    }

}
