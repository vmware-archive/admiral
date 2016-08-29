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
    public static final long MAINTENANCE_INTERVAL_MICROS = Long.getLong(
            "dcp.management.container.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(30));
    protected static final long MAINTENANCE_INTERVAL_INSPECT_MICROS = Long.getLong(
            "dcp.management.container.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(300));
    protected static final long MAINTENANCE_INTERVAL_SLOW_DOWN_PERIOD = Long.getLong(
            "dcp.management.container.periodic.maintenance.slow.down.period.micros",
            TimeUnit.SECONDS.toMicros(600));

    private final ServiceHost host;
    private final String containerSelfLink;
    private long lastInspectMaintainanceInMicros;

    public static ContainerMaintenance create(ServiceHost host, String containerSelfLink) {
        return new ContainerMaintenance(host, containerSelfLink);
    }

    private ContainerMaintenance(ServiceHost host, String containerSelfLink) {
        this.host = host;
        this.containerSelfLink = containerSelfLink;
    }

    public void handleMaintenance(Operation post) {
        if (DeploymentProfileConfig.getInstance().isTest()) {
            host.log(Level.FINE,
                    "Skipping scheduled maintenance in test mode: %s", containerSelfLink);
            post.complete();
            return;
        }

        host.log(Level.FINE, "Performing maintenance for: %s", containerSelfLink);
        host.sendRequest(Operation
                .createGet(host, containerSelfLink)
                .setReferer(host.getUri())
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
                            long nowMicrosUtc = Utils.getNowMicrosUtc();

                            /* if container hasn't been updated for a while, slow down the data-collection */
                            if (containerState.documentUpdateTimeMicros
                                    + MAINTENANCE_INTERVAL_SLOW_DOWN_PERIOD < nowMicrosUtc) {
                                /* check if the slow-down period has expired */
                                if (lastInspectMaintainanceInMicros + 6
                                        * MAINTENANCE_INTERVAL_SLOW_DOWN_PERIOD < nowMicrosUtc) {
                                    /* set another slow-down period and perform collection */
                                    lastInspectMaintainanceInMicros = nowMicrosUtc + 6
                                            * MAINTENANCE_INTERVAL_SLOW_DOWN_PERIOD;
                                    processContainerInspect(post, containerState);
                                } else {
                                    post.complete();
                                }

                                return;
                            }

                            if (lastInspectMaintainanceInMicros
                                    + MAINTENANCE_INTERVAL_INSPECT_MICROS < nowMicrosUtc) {
                                lastInspectMaintainanceInMicros = nowMicrosUtc;
                                processContainerInspect(post, containerState);
                            } else {
                                performStatsInspection(post, containerState);
                            }
                        }));
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
                .createPatch(containerState.adapterManagementReference)
                .setBody(request)
                .setReferer(host.getUri())
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
                .createPatch(containerState.adapterManagementReference)
                .setBody(request)
                .setReferer(host.getUri())
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
