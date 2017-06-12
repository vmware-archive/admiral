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

package com.vmware.admiral.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.Utils;

public final class ServiceUtils {
    private static final Logger logger = Logger.getLogger(ServiceUtils.class.getName());

    public static final long EXPIRATION_MICROS = Long.getLong(
            "com.vmware.admiral.common.util.ServiceUtils.expiration.micros",
            TimeUnit.HOURS.toMicros(5));

    private ServiceUtils() {
    }

    /**
     * The default expiration time for all Task Services.
     */
    public static long getDefaultTaskExpirationTimeInMicros() {
        return getExpirationTimeFromNowInMicros(EXPIRATION_MICROS);
    }

    /**
     * Adds the supplied argument to the value from
     * {@link com.vmware.xenon.common.Utils#getSystemNowMicrosUtc()} and returns an absolute
     * expiration time in the future
     */
    public static long getExpirationTimeFromNowInMicros(long microsFromNow) {
        return Utils.fromNowMicrosUtc(microsFromNow);
    }

    /**
     * Sends {@code DELETE} request to service {@code s}. Result is ignored.
     */
    public static void sendSelfDelete(Service s) {
        s.sendRequest(Operation.createDelete(s.getUri()).setBody(new ServiceDocument()));
    }

    public static void addServiceRequestRoute(ServiceDocument serviceDocument, Action action,
            String description, Class responseType) {
        if (serviceDocument == null) {
            return;
        }

        if (serviceDocument.documentDescription == null) {
            serviceDocument.documentDescription = new ServiceDocumentDescription();
        }

        if (serviceDocument.documentDescription.serviceRequestRoutes == null) {
            serviceDocument.documentDescription.serviceRequestRoutes = new HashMap<>();
        }

        RequestRouter.Route route = new RequestRouter.Route();
        route.action = action;
        route.description = description;
        route.responseType = responseType;

        if (!serviceDocument.documentDescription.serviceRequestRoutes.containsKey(route.action)) {
            serviceDocument.documentDescription.serviceRequestRoutes
                    .put(route.action, new ArrayList<>());
        }
        serviceDocument.documentDescription.serviceRequestRoutes.get(route.action).add(route);
    }

    /**
     * Run safely runnable function. If operation is passed it will be failed in case of exception.
     */
    public static void handleExceptions(Operation o, Runnable function) {
        try {
            function.run();
        } catch (Throwable e) {
            logger.severe("Unexpected error: " + Utils.toString(e));
            if (o != null) {
                o.fail(e);
            }
        }
    }

}