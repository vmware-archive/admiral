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

package com.vmware.admiral.compute.container.maintenance;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.VolumeOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerVolumeMaintenance {
    public static final String SERVICE_REFERRER_PATH = "/container-volume-maintenance";

    public static final long MAINTENANCE_INTERVAL_MICROS = Long.getLong(
            "dcp.management.container.volume.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(30));
    protected static final long MAINTENANCE_INTERVAL_INSPECT_MICROS = Long.getLong(
            "dcp.management.container.volume.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(300));
    protected static final long MAINTENANCE_INTERVAL_SLOW_DOWN_PERIOD = Long.getLong(
            "dcp.management.container.volume.periodic.maintenance.slow.down.period.micros",
            TimeUnit.SECONDS.toMicros(600));


    private final ServiceHost host;
    private final String volumeSelfLink;
    private long lastInspectMaintainanceInMicros;

    public static ContainerVolumeMaintenance create(ServiceHost host, String volumeSelfLink,
            boolean delayFirstMaintenance) {
        return new ContainerVolumeMaintenance(host, volumeSelfLink, delayFirstMaintenance);
    }

    private ContainerVolumeMaintenance(ServiceHost host, String volumeSelfLink,
            boolean delayFirstMaintenance) {
        this.host = host;
        this.volumeSelfLink = volumeSelfLink;
        if (delayFirstMaintenance) {
            this.lastInspectMaintainanceInMicros = Utils.getSystemNowMicrosUtc();
        }
    }

    public void handlePeriodicMaintenance(Operation post) {
        if (DeploymentProfileConfig.getInstance().isTest()) {
            host.log(Level.FINE,
                    "Skipping scheduled maintenance in test mode: %s", volumeSelfLink);
            post.complete();
            return;
        }

        host.log(Level.FINE, "Performing maintenance for: %s", volumeSelfLink);
        host.sendRequest(Operation
                .createGet(host, volumeSelfLink)
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
                            ContainerVolumeState volumeState = o
                                    .getBody(ContainerVolumeState.class);
                            long nowMicrosUtc = Utils.getSystemNowMicrosUtc();

                            /*
                             * if volume hasn't been updated for a while, slow down the
                             * data-collection
                             */
                            if (volumeState.documentUpdateTimeMicros
                                    + MAINTENANCE_INTERVAL_SLOW_DOWN_PERIOD < nowMicrosUtc) {
                                /* check if the slow-down period has expired */
                                if (lastInspectMaintainanceInMicros +
                                        MAINTENANCE_INTERVAL_SLOW_DOWN_PERIOD < nowMicrosUtc) {
                                    /* set another slow-down period and perform collection */
                                    lastInspectMaintainanceInMicros = nowMicrosUtc +
                                            MAINTENANCE_INTERVAL_SLOW_DOWN_PERIOD;
                                    processVolumeInspect(post, volumeState);
                                } else {
                                    post.complete();
                                }

                                return;
                            }

                            if (lastInspectMaintainanceInMicros
                                    + MAINTENANCE_INTERVAL_INSPECT_MICROS < nowMicrosUtc) {
                                lastInspectMaintainanceInMicros = nowMicrosUtc;
                                processVolumeInspect(post, volumeState);
                            } else {
                                post.complete();
                            }
                        }));
    }

    private void processVolumeInspect(Operation post, ContainerVolumeState volumeState) {
        if (volumeState.adapterManagementReference == null) {
            // probably the volume hasn't finished provisioning
            Utils.log(getClass(), volumeSelfLink, Level.FINE,
                    "Can't perform maintenance because adapter reference is not set: %s",
                    volumeState.documentSelfLink);
            post.complete();
            return;
        }

        if (volumeState.powerState == null || volumeState.powerState.isUnmanaged()) {
            Utils.log(getClass(), volumeSelfLink, Level.FINE,
                    "Skipping maintenance for unmanaged volume: %s",
                    volumeState.documentSelfLink);
            post.complete();
            return;
        }

        requestVolumeInspection(post, volumeState);
    }

    private void requestVolumeInspection(Operation post, ContainerVolumeState volumeState) {
        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildPublicUri(host, volumeState.documentSelfLink);
        request.operationTypeId = VolumeOperationType.INSPECT.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        host.sendRequest(Operation
                .createPatch(host, volumeState.adapterManagementReference.toString())
                .setBody(request)
                .setReferer(UriUtils.buildUri(host, SERVICE_REFERRER_PATH))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        Utils.logWarning(
                                "Exception while inspect request for volume: %s. Error: %s",
                                volumeState.documentSelfLink, Utils.toString(ex));
                    }
                    post.complete();
                }));
    }
}
