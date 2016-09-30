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
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerNetworkMaintenance {
    public static final long MAINTENANCE_INTERVAL_MICROS = Long.getLong(
            "dcp.management.container.network.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(30));
    protected static final long MAINTENANCE_INTERVAL_INSPECT_MICROS = Long.getLong(
            "dcp.management.container.network.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(300));
    protected static final long MAINTENANCE_INTERVAL_SLOW_DOWN_PERIOD = Long.getLong(
            "dcp.management.container.network.periodic.maintenance.slow.down.period.micros",
            TimeUnit.SECONDS.toMicros(600));

    private final ServiceHost host;
    private final String networkSelfLink;
    private long lastInspectMaintainanceInMicros;

    public static ContainerNetworkMaintenance create(ServiceHost host, String networkSelfLink) {
        return new ContainerNetworkMaintenance(host, networkSelfLink);
    }

    private ContainerNetworkMaintenance(ServiceHost host, String networkSelfLink) {
        this.host = host;
        this.networkSelfLink = networkSelfLink;
    }

    public void handleMaintenance(Operation post) {
        if (DeploymentProfileConfig.getInstance().isTest()) {
            host.log(Level.FINE,
                    "Skipping scheduled maintenance in test mode: %s", networkSelfLink);
            post.complete();
            return;
        }

        host.log(Level.FINE, "Performing maintenance for: %s", networkSelfLink);
        host.sendRequest(Operation
                .createGet(host, networkSelfLink)
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
                            ContainerNetworkState containerState = o
                                    .getBody(ContainerNetworkState.class);
                            long nowMicrosUtc = Utils.getNowMicrosUtc();

                            /*
                             * if container hasn't been updated for a while, slow down the
                             * data-collection
                             */
                            if (containerState.documentUpdateTimeMicros
                                    + MAINTENANCE_INTERVAL_SLOW_DOWN_PERIOD < nowMicrosUtc) {
                                /* check if the slow-down period has expired */
                                if (lastInspectMaintainanceInMicros + 6
                                        * MAINTENANCE_INTERVAL_SLOW_DOWN_PERIOD < nowMicrosUtc) {
                                    /* set another slow-down period and perform collection */
                                    lastInspectMaintainanceInMicros = nowMicrosUtc + 6
                                            * MAINTENANCE_INTERVAL_SLOW_DOWN_PERIOD;
                                    processNetworkInspect(post, containerState);
                                } else {
                                    post.complete();
                                }

                                return;
                            }

                            if (lastInspectMaintainanceInMicros
                                    + MAINTENANCE_INTERVAL_INSPECT_MICROS < nowMicrosUtc) {
                                lastInspectMaintainanceInMicros = nowMicrosUtc;
                                processNetworkInspect(post, containerState);
                            }
                        }));
    }

    private void processNetworkInspect(Operation post, ContainerNetworkState networkState) {
        if (networkState.adapterManagementReference == null) {
            // probably the network hasn't finished provisioning
            Utils.log(getClass(), networkSelfLink, Level.FINE,
                    "Can't perform maintenance because adapter reference is not set: %s",
                    networkState.documentSelfLink);
            post.complete();
            return;
        }

        if (networkState.powerState == null || networkState.powerState.isUnmanaged()) {
            Utils.log(getClass(), networkSelfLink, Level.FINE,
                    "Skipping maintenance for unmanaged network: %s",
                    networkState.documentSelfLink);
            post.complete();
            return;
        }

        requestNetworkInspection(post, networkState);
    }

    private void requestNetworkInspection(Operation post, ContainerNetworkState networkState) {
        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildPublicUri(host, networkState.documentSelfLink);
        request.operationTypeId = NetworkOperationType.INSPECT.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        host.sendRequest(Operation
                .createPatch(networkState.adapterManagementReference)
                .setBody(request)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        Utils.logWarning(
                                "Exception while inspect request for network: %s. Error: %s",
                                networkState.documentSelfLink, Utils.toString(ex));
                    }
                    post.complete();
                }));
    }
}
