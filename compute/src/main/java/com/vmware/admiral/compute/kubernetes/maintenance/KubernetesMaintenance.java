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

package com.vmware.admiral.compute.kubernetes.maintenance;

import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.getStateTypeFromSelfLink;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.KubernetesOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class KubernetesMaintenance {

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
    private final String kubernetesEntitySelfLink;
    private long lastInspectMaintenanceInMicros;

    public static KubernetesMaintenance create(ServiceHost host, String kubernetesEntitySelfLink) {
        return new KubernetesMaintenance(host, kubernetesEntitySelfLink);
    }

    private KubernetesMaintenance(ServiceHost host, String kubernetesEntitySelfLink) {
        this.host = host;
        this.kubernetesEntitySelfLink = kubernetesEntitySelfLink;
    }

    public void handlePeriodicMaintenance(Operation post) {
        if (DeploymentProfileConfig.getInstance().isTest()) {
            host.log(Level.FINE,
                    "Skipping scheduled maintenance in test mode: %s", kubernetesEntitySelfLink);
            post.complete();
            return;
        }

        host.log(Level.FINE, "Performing maintenance for: %s", kubernetesEntitySelfLink);
        host.sendRequest(Operation
                .createGet(host, kubernetesEntitySelfLink)
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

                            BaseKubernetesState containerState = o
                                    .getBody(getStateTypeFromSelfLink(kubernetesEntitySelfLink));

                            // inspect the container or update its stats (if needed)
                            if (!inspectEntityIfNeeded(containerState, post)) {
                                post.complete();

                            }
                        }));
    }

    /**
     * Checks whether it is time to inspect this entity and sends an inspect request if needed
     *
     * @return whether an inspect request was sent or not
     */
    private boolean inspectEntityIfNeeded(BaseKubernetesState kubernetesState, Operation post) {
        long nowMicrosUtc = Utils.getSystemNowMicrosUtc();
        long updatePeriod = isUpdatedRecently(kubernetesState, nowMicrosUtc)
                ? MAINTENANCE_PERIOD_MICROS : MAINTENANCE_SLOW_DOWN_PERIOD_MICROS;

        // check whether the update period has passed
        if (lastInspectMaintenanceInMicros + updatePeriod < nowMicrosUtc) {
            // schedule next period and request inspect
            lastInspectMaintenanceInMicros = nowMicrosUtc;
            requestEntityInspection(post, kubernetesState);
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return whether the specified {@link BaseKubernetesState} has been updated in the previous
     * MAINTENANCE_SLOW_DOWN_AGE_MICROS microseconds.
     */
    private boolean isUpdatedRecently(BaseKubernetesState containerState, long nowMicrosUtc) {
        return containerState.documentUpdateTimeMicros
                + MAINTENANCE_SLOW_DOWN_AGE_MICROS > nowMicrosUtc;
    }

    private void requestEntityInspection(Operation post, BaseKubernetesState kubernetesState) {
        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildPublicUri(host,
                kubernetesState.documentSelfLink);

        request.operationTypeId = KubernetesOperationType.INSPECT.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        host.sendRequest(Operation
                .createPatch(host, ManagementUriParts.ADAPTER_KUBERNETES)
                .setBody(request)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        Utils.logWarning(
                                "Exception while inspect request for kubernetes entity: %s. "
                                        + "Error: %s", kubernetesState.documentSelfLink,
                                Utils.toString(ex));
                    }
                    post.complete();
                }));
    }
}
