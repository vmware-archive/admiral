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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerNetworkMaintenance {
    private static final String SERVICE_REFERRER_PATH = "/container-network-maintenance";

    private static final long MAINTENANCE_INTERVAL_INSPECT_MICROS = Long.getLong(
            "dcp.management.container.network.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(300));

    private final ServiceHost host;

    public ContainerNetworkMaintenance(ServiceHost host) {
        this.host = host;
    }

    public void requestNetworksInspectIfNeeded(List<ContainerNetworkState> networkStates) {
        if (networkStates == null || networkStates.isEmpty()) {
            return;
        }
        for (ContainerNetworkState network : networkStates) {
            try {
                this.requestNetworkInspectIfNeeded(network);
            } catch (Exception e) {
                Utils.log(getClass(), SERVICE_REFERRER_PATH, Level.WARNING,
                        "Unexpected exception inspecting network %s : %s",
                        network.documentSelfLink, Utils.toString(e));
            }
        }
    }

    private void requestNetworkInspectIfNeeded(ContainerNetworkState networkState) {
        long lastUpdate = networkState.documentUpdateTimeMicros;
        if (lastUpdate + MAINTENANCE_INTERVAL_INSPECT_MICROS > Utils.getSystemNowMicrosUtc()) {
            // network was recently updated, skip inspection
            Utils.log(getClass(), SERVICE_REFERRER_PATH, Level.FINE,
                    "Skipping maintenance for network %s, it is recently updated",
                    networkState.documentSelfLink);
            return;
        }

        if (networkState.adapterManagementReference == null) {
            // probably the network hasn't finished provisioning
            Utils.log(getClass(), SERVICE_REFERRER_PATH, Level.FINE,
                    "Can't perform maintenance because adapter reference is not set: %s",
                    networkState.documentSelfLink);
            return;
        }

        if (networkState.powerState == null || networkState.powerState.isUnmanaged()) {
            Utils.log(getClass(), SERVICE_REFERRER_PATH, Level.FINE,
                    "Skipping maintenance for unmanaged network: %s",
                    networkState.documentSelfLink);
            return;
        }

        requestNetworkInspection(networkState);
    }

    public void requestNetworkInspection(ContainerNetworkState networkState) {
        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildPublicUri(host, networkState.documentSelfLink);
        request.operationTypeId = NetworkOperationType.INSPECT.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        host.sendRequest(Operation
                .createPatch(host, networkState.adapterManagementReference.toString())
                .setBodyNoCloning(request)
                .setReferer(UriUtils.buildUri(host, SERVICE_REFERRER_PATH))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        Utils.log(getClass(), SERVICE_REFERRER_PATH, Level.WARNING,
                                "Exception while inspect request for network: %s. Error: %s",
                                networkState.documentSelfLink, Utils.toString(ex));
                    }
                }));
    }

}
