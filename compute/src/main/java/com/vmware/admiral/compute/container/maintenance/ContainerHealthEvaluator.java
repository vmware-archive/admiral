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

import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.log.EventLogService.EventLogState.EventLogType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerHealthEvaluator {
    public static final int DEFAULT_HEALTHY_THRESHOLD = Integer.getInteger(
            "com.vmware.admiral.compute.container.default.healthy.threshold", 3);
    public static final int DEFAULT_UNHEALTHY_THRESHOLD = Integer.getInteger(
            "com.vmware.admiral.compute.container.default.unhealthy.threshold", 3);

    private final ServiceHost host;
    private final ContainerState containerState;

    public static ContainerHealthEvaluator create(ServiceHost host, ContainerState containerState) {
        return new ContainerHealthEvaluator(host, containerState);
    }

    private ContainerHealthEvaluator(ServiceHost host, ContainerState containerState) {
        this.host = host;
        this.containerState = containerState;
    }

    public boolean calculateHealthStatus(ContainerStats currentState, ContainerStats patchBody) {
        if (patchBody.healthCheckSuccess == null) {
            return false;
        }
        // do not 'degrade' if status is running and check is successful
        // or if state is error and check is not successful
        boolean skipDegraded;
        if (patchBody.healthCheckSuccess) {
            patchBody.healthSuccessCount = currentState.healthSuccessCount + 1;
            patchBody.healthFailureCount = 0;
            skipDegraded = ContainerState.CONTAINER_RUNNING_STATUS.equals(containerState.status);
        } else {
            patchBody.healthFailureCount = currentState.healthFailureCount + 1;
            patchBody.healthSuccessCount = 0;
            skipDegraded = ContainerState.CONTAINER_ERROR_STATUS.equals(containerState.status);
        }

        patchContainerStatus(patchBody, null, skipDegraded);
        return true;
    }

    private void patchContainerStatus(ContainerStats patchHealth, HealthConfig healthConfig,
            boolean skipDegraded) {
        if (containerState.descriptionLink == null) {
            healthConfig = createDefaultHealthConfig();
        } else if (healthConfig == null) {
            getHealthConfig(containerState.descriptionLink,
                    (h) -> patchContainerStatus(patchHealth, h, skipDegraded));
            return;
        }

        String status = null;
        PowerState powerState = null;
        if (healthConfig.healthyThreshold == null) {
            healthConfig.healthyThreshold = DEFAULT_HEALTHY_THRESHOLD;
        }
        if (healthConfig.unhealthyThreshold == null) {
            healthConfig.unhealthyThreshold = DEFAULT_UNHEALTHY_THRESHOLD;
        }
        if (patchHealth.healthFailureCount == healthConfig.unhealthyThreshold) {
            status = ContainerState.CONTAINER_UNHEALTHY_STATUS;
            powerState = PowerState.ERROR;
            publishEventLog(patchHealth);
        } else if (!skipDegraded
                && patchHealth.healthFailureCount < healthConfig.unhealthyThreshold
                && patchHealth.healthSuccessCount < healthConfig.healthyThreshold) {
            status = ContainerState.CONTAINER_DEGRADED_STATUS;
        } else if (patchHealth.healthSuccessCount >= healthConfig.healthyThreshold) {
            status = ContainerState.CONTAINER_RUNNING_STATUS;
            // do not set container to RUNNING if we know it is stopped
            if (patchHealth.containerStopped == null || !patchHealth.containerStopped) {
                powerState = PowerState.RUNNING;
            }
        }

        if (status != null) {
            ContainerState state = new ContainerState();
            state.status = status;
            if (powerState != null) {
                state.powerState = powerState;
            }

            host.sendRequest(Operation
                    .createPatch(host, containerState.documentSelfLink)
                    .setBodyNoCloning(state)
                    .setReferer(host.getUri())
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            host.log(Level.WARNING, "Failed to update container health state after"
                                    + " periodic maintenance: %s", o.getUri());
                        } else {
                            host.log(Level.FINE, "Container health updated successfully after"
                                    + " periodic maintenance: %s", o.getUri());
                        }
                    }));
        }
    }

    private void publishEventLog(ContainerStats patchHealth) {
        EventLogState eventLog = new EventLogState();
        eventLog.description = String.format("Health check failed for container %s after %d tries,"
                        + " container state will be set to ERROR.",
                containerState.documentSelfLink, patchHealth.healthFailureCount);

        eventLog.eventLogType = EventLogType.ERROR;
        eventLog.resourceType = getClass().getName();
        eventLog.tenantLinks = containerState.tenantLinks;

        host.log(Level.WARNING, eventLog.description);

        host.sendRequest(Operation.createPost(host, EventLogService.FACTORY_LINK)
                .setBodyNoCloning(eventLog)
                .setReferer(ContainerFactoryService.SELF_LINK)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.WARNING, "Failed to create event log: %s",
                                Utils.toString(e));
                    }
                }));
    }

    private void getHealthConfig(String containerDescriptionLink,
            Consumer<HealthConfig> callback) {

        ServiceDocumentQuery<ContainerDescription> query =
                new ServiceDocumentQuery<ContainerDescription>(
                        host,
                        ContainerDescription.class);
        String healthConfigUriPath = UriUtils.buildUriPath(containerDescriptionLink);
        query.queryDocument(healthConfigUriPath,
                (r) -> {
                    if (r.hasException()) {
                        host.log(Level.FINE,
                                "Failed to retrieve container's health config: %s - %s",
                                containerState.documentSelfLink, r.getException());
                    } else if (r.hasResult() && r.getResult().healthConfig != null) {
                        callback.accept(r.getResult().healthConfig);
                    } else {
                        host.log(Level.FINE,
                                "Container's health config: %s not found.",
                                containerState.documentSelfLink);
                        HealthConfig defaultConfig = createDefaultHealthConfig();
                        callback.accept(defaultConfig);
                    }
                });
    }

    private HealthConfig createDefaultHealthConfig() {
        HealthConfig defaultConfig = new HealthConfig();
        defaultConfig.healthyThreshold = DEFAULT_HEALTHY_THRESHOLD;
        defaultConfig.unhealthyThreshold = DEFAULT_UNHEALTHY_THRESHOLD;
        return defaultConfig;
    }

}
