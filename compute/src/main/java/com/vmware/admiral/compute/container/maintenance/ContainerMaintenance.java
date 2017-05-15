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

package com.vmware.admiral.compute.container.maintenance;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerMaintenance {
    public static final String SERVICE_REFERRER_PATH = "/container-maintenance";

    public static final long MAINTENANCE_INTERVAL_MICROS = Long.getLong(
            "dcp.management.container.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(30));
    protected static final long MAINTENANCE_PERIOD_MICROS = Long.getLong(
            "dcp.management.container.periodic.maintenance.period.micros",
            TimeUnit.MINUTES.toMicros(5));
    protected static final long MAINTENANCE_SLOW_DOWN_AGE_MICROS = Long.getLong(
            "dcp.management.container.periodic.maintenance.slow.down.age.micros",
            TimeUnit.MINUTES.toMicros(10));
    protected static final long MAINTENANCE_SLOW_DOWN_PERIOD_MICROS = Long.getLong(
            "dcp.management.container.periodic.maintenance.slow.down.period.micros",
            TimeUnit.HOURS.toMicros(1));

    private final ServiceHost host;
    private final String containerSelfLink;
    private long lastInspectMaintenanceInMicros;
    private long lastStatsMaintenanceInMicros;

    public static ContainerMaintenance create(ServiceHost host, String containerSelfLink) {
        return new ContainerMaintenance(host, containerSelfLink);
    }

    private ContainerMaintenance(ServiceHost host, String containerSelfLink) {
        this.host = host;
        this.containerSelfLink = containerSelfLink;
    }

    public void handlePeriodicMaintenance(Operation post) {
        if (DeploymentProfileConfig.getInstance().isTest()) {
            host.log(Level.FINE,
                    "Skipping scheduled maintenance in test mode: %s", containerSelfLink);
            post.complete();
            return;
        }

        host.log(Level.FINE, "Performing maintenance for: %s", containerSelfLink);
        host.sendRequest(Operation
                .createGet(host, containerSelfLink)
                .setReferer(UriUtils.buildUri(host, SERVICE_REFERRER_PATH))
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                Utils.logWarning(
                                        "Failed to fetch self state for periodic maintenance: %s",
                                        o.getUri());
                                post.fail(ex);
                                return;
                            }
                            ContainerState containerState = o.getBody(ContainerState.class);

                            // inspect the container or update its stats (if needed)
                            if (!inspectContainerIfNeeded(containerState, post)) {
                                if (!collectStatsIfNeeded(containerState, post)) {
                                    // in all other cases the inspect/stats request will
                                    // complete the operation
                                    post.complete();
                                }
                            }
                        }));
    }

    /**
     * Checks whether it is time to inspect this container and sends an inspect request if needed
     *
     * @return whether an inspect request was sent or not
     */
    private boolean inspectContainerIfNeeded(ContainerState containerState, Operation post) {
        long nowMicrosUtc = Utils.getSystemNowMicrosUtc();
        long updatePeriod = isUpdatedRecently(containerState, nowMicrosUtc)
                ? MAINTENANCE_PERIOD_MICROS : MAINTENANCE_SLOW_DOWN_PERIOD_MICROS;

        // check whether the update period has passed
        if (lastInspectMaintenanceInMicros + updatePeriod < nowMicrosUtc) {
            // schedule next period and request inspect
            lastInspectMaintenanceInMicros = nowMicrosUtc;
            processContainerInspect(post, containerState);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks whether it is time to update the stats for this container and sends a stats collection
     * request if needed
     *
     * @return whether a stats collection request was sent or not
     */
    private boolean collectStatsIfNeeded(ContainerState containerState, Operation post) {
        long nowMicrosUtc = Utils.getSystemNowMicrosUtc();
        // if the container state is recently updated, we want to collect stats on each maintenance
        long updatePeriod = isUpdatedRecently(containerState, nowMicrosUtc)
                ? 0 : MAINTENANCE_PERIOD_MICROS;

        // check whether the update period has passed
        if (lastStatsMaintenanceInMicros + updatePeriod < nowMicrosUtc) {
            // schedule next period and request stats collection
            lastStatsMaintenanceInMicros = nowMicrosUtc;
            performStatsInspection(post, containerState);
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return whether the specified {@link ContainerState} has been updated in the previous
     *         MAINTENANCE_SLOW_DOWN_AGE_MICROS microseconds.
     */
    private boolean isUpdatedRecently(ContainerState containerState, long nowMicrosUtc) {
        return containerState.documentUpdateTimeMicros
                + MAINTENANCE_SLOW_DOWN_AGE_MICROS > nowMicrosUtc;
    }

    private void processContainerInspect(Operation post, ContainerState containerState) {
        if (containerState.adapterManagementReference == null) {
            // probably the container hasn't finished provisioning
            Utils.log(getClass(), containerSelfLink, Level.FINE,
                    "Can't perform maintenance because adapter reference is not set: %s",
                    containerState.documentSelfLink);

            post.complete();
            return;
        }

        if (containerState.powerState == null || containerState.powerState.isUnmanaged()) {
            Utils.log(getClass(), containerSelfLink, Level.FINE,
                    "Skipping maintenance for unmanaged container: %s",
                    containerState.documentSelfLink);
            post.complete();
            return;
        }

        requestContainerInspection(post, containerState);
    }

    private void requestContainerInspection(Operation post, ContainerState containerState) {
        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildPublicUri(host,
                containerState.documentSelfLink);

        request.operationTypeId = ContainerOperationType.INSPECT.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        host.sendRequest(Operation
                .createPatch(host, containerState.adapterManagementReference.toString())
                .setBody(request)
                .setReferer(UriUtils.buildUri(host, SERVICE_REFERRER_PATH))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        Utils.logWarning(
                                "Exception while inspect request for container: %s. Error: %s",
                                containerState.documentSelfLink, Utils.toString(ex));
                    }
                    post.complete();
                }));
    }

    public void performStatsInspection(Operation post, ContainerState containerState) {
        if (containerState.adapterManagementReference == null) {
            // probably the container hasn't finished provisioning
            Utils.log(getClass(), containerSelfLink, Level.FINE,
                    "Can't perform maintenance because adapter reference is not set: %s",
                    containerState.documentSelfLink);

            post.complete();
            return;
        }

        if (containerState.powerState != PowerState.RUNNING) {
            Utils.log(getClass(), containerSelfLink, Level.FINE,
                    "Skipping fetching stats for a container that is not running: %s",
                    containerState.documentSelfLink);

            post.complete();
            return;
        }

        requestStatsInspection(post, containerState);
    }

    public void requestStatsInspection(Operation post, ContainerState containerState) {
        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildPublicUri(host,
                containerState.documentSelfLink);

        request.operationTypeId = ContainerOperationType.STATS.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        host.sendRequest(Operation
                .createPatch(host, containerState.adapterManagementReference.toString())
                .setBody(request)
                .setReferer(UriUtils.buildUri(host, SERVICE_REFERRER_PATH))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        Utils.logWarning(
                                "Exception while stats request for container: %s. Error: %s",
                                containerState.documentSelfLink, Utils.toString(ex));
                    }
                    post.complete();
                }));
    }
}
