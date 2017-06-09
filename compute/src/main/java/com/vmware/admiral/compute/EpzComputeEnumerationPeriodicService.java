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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.CommonContinuousQueries;
import com.vmware.admiral.common.util.CommonContinuousQueries.ContinuousQueryId;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.QueryTask;

/**
 * A stateless service that periodically triggers enumeration of computes participating in all
 * query-driven placement zones.
 */
public class EpzComputeEnumerationPeriodicService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.EPZ_PERIODIC_ENUMERATION;

    public static final long MAINTENANCE_INTERVAL_MICROS = Long.getLong(
            "dcp.management.epz.compute.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(300));

    // used to avoid refresh on compute change too soon after a previous refresh
    private static final long PAUSE_SECONDS = Long.getLong(
            "dcp.management.epz.compute.periodic.pause.seconds", 10);
    private final AtomicBoolean paused = new AtomicBoolean();
    private final AtomicBoolean invalidated = new AtomicBoolean();

    public EpzComputeEnumerationPeriodicService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.toggleOption(ServiceOption.PERSISTENCE, false);
        super.setMaintenanceIntervalMicros(MAINTENANCE_INTERVAL_MICROS);
    }

    @Override
    public void handlePeriodicMaintenance(Operation post) {
        post.complete();
        doTrigger(() -> "Periodic refresh", false);
    }

    @Override
    public void handleStart(Operation startPost) {
        startPost.complete();
        CommonContinuousQueries.subscribeTo(this.getHost(), ContinuousQueryId.COMPUTES,
                this::onComputeChange);
    }

    public void onComputeChange(Operation op) {
        op.complete();
        QueryTask queryTask = op.getBody(QueryTask.class);
        if (queryTask.results != null && queryTask.results.documentLinks != null
                && !queryTask.results.documentLinks.isEmpty()) {
            doTrigger(() -> String.format("Compute change: %s",
                    String.join(", ", queryTask.results.documentLinks)), true);
        }
    }

    private void doTrigger(Supplier<String> logSupplier, boolean postponeIfPaused) {
        // do nothing if refresh is currently paused
        if (!this.paused.compareAndSet(false, true)) {
            String s = logSupplier.get() + " %s";
            if (postponeIfPaused) {
                logFine(s, "[postponed]");
                invalidated.set(true);
            } else {
                logFine(s, "[not needed]");
            }
            return;
        }

        // refresh
        logInfo(logSupplier);
        EpzComputeEnumerationTaskService.triggerForAllResourcePools(this);

        // re-enable after the pause
        this.getHost().schedule(() -> {
            boolean isRefreshRequired = this.invalidated.getAndSet(false);
            this.paused.set(false);

            if (isRefreshRequired) {
                doTrigger(() -> "Postponed refresh", false);
            }
        }, PAUSE_SECONDS, TimeUnit.SECONDS);
    }
}
