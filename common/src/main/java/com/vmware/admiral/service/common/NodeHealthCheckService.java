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

package com.vmware.admiral.service.common;

import static com.vmware.admiral.common.util.ServiceUtils.addServiceRequestRoute;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;

/**
 * Service is meant to do a health check of every service of default node group. It checks if
 * services of target host are available and ready for replication. Every service which has
 * <b> ServiceOption.REPLICATION </b> option will be registered automatically for monitoring.
 *
 */
public class NodeHealthCheckService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.CONFIG + "/healthcheck";

    public Set<String> services = ConcurrentHashMap.newKeySet();

    @Override
    public void handleGet(Operation get) {

        if (services == null || services.isEmpty()) {
            getHost().log(Level.INFO, "No registered services for healthcheck found!");
            get.complete();
            return;
        }

        doHealthCheck(get);
    }

    @Override
    public void handlePatch(Operation patch) {
        NodeHealthCheckService patchState = patch.getBody(NodeHealthCheckService.class);
        if (patchState.services != null && !patchState.services.isEmpty()) {
            services.addAll(patchState.services);
        }
        patch.complete();
    }

    private void doHealthCheck(Operation get) {

        AtomicInteger numberOfServicesToCheck = new AtomicInteger(services.size());
        Set<String> unavailableServices = ConcurrentHashMap.newKeySet();

        Iterator<String> servicesIterator = services.iterator();

        servicesIterator.forEachRemaining(currentService -> {

            CompletionHandler ch = (o, e) -> {
                if (e != null) {
                    // Will fail if the service is not found or not started yet.
                    unavailableServices.add(currentService);
                } else {
                    getHost().log(Level.INFO, "Service: %s is AVAILABLE", currentService);
                }

                if (numberOfServicesToCheck.decrementAndGet() == 0) {
                    if (unavailableServices.isEmpty()) {
                        getHost().log(Level.INFO, "Node is in good health!");
                        get.complete();
                    } else {
                        get.fail(new Throwable(String.format("Unavailable services: %s", String.join(";", unavailableServices))));
                    }
                }
            };
            getHost().checkReplicatedServiceAvailable(ch, currentService);

        });

    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        addServiceRequestRoute(d, Action.GET,
                "Do health check of services in default node group if they are available and "
                        + "ready for replication.", null);
        addServiceRequestRoute(d, Action.PATCH,
                "Register services for health check.", NodeHealthCheckService.class);
        return d;
    }
}
