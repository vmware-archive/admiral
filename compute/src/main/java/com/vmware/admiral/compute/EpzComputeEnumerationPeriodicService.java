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

package com.vmware.admiral.compute;

import java.util.concurrent.TimeUnit;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * A stateless service that periodically triggers enumeration of computes participating in all
 * query-driven placement zones.
 */
public class EpzComputeEnumerationPeriodicService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.EPZ_PERIODIC_ENUMERATION;

    public static final long MAINTENANCE_INTERVAL_MICROS = Long.getLong(
            "dcp.management.epz.compute.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(60));

    public EpzComputeEnumerationPeriodicService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(MAINTENANCE_INTERVAL_MICROS);
    }

    @Override
    public void handlePeriodicMaintenance(Operation post) {
        Query rpQuery = Query.Builder.create().addKindFieldClause(ResourcePoolState.class).build();
        QueryTask rpQueryTask = QueryTask.Builder.createDirectTask().setQuery(rpQuery).build();

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(rpQueryTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        post.fail(e);
                        return;
                    }

                    // start enumeration tasks for all resource pools in parallel
                    ServiceDocumentQueryResult result = o.getBody(QueryTask.class).results;
                    if (result != null && result.documentLinks != null) {
                        result.documentLinks.forEach(rpLink -> {
                            EpzComputeEnumerationTaskService.triggerForResourcePool(this, rpLink);
                        });
                    }
                    post.complete();
                }));
    }
}
