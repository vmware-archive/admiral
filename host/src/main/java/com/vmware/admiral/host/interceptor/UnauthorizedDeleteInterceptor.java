/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host.interceptor;

import static com.vmware.admiral.service.common.AbstractTaskStatefulService.UNAUTHORIZED_ACCESS_FOR_ACTION_MESSAGE;

import java.util.logging.Level;

import com.vmware.admiral.auth.util.SecurityContextUtil;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.request.RequestStatusService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;

/**
 * This processing chain:
 * - Prevents deletion of a {@link ServiceDocument} if the user is not Cloud admin
 */
public class UnauthorizedDeleteInterceptor {

    public static final String UNAUTHORIZED_ACCESS_FOR_ACTION_OF_RESOURCES_MESSAGE =
            UNAUTHORIZED_ACCESS_FOR_ACTION_MESSAGE + " of %s";

    public static void register(OperationInterceptorRegistry registry) {
        registry.addServiceInterceptor(
                EventLogService.class, Action.DELETE, UnauthorizedDeleteInterceptor::handleDelete);
        registry.addServiceInterceptor(
                RequestStatusService.class, Action.DELETE, UnauthorizedDeleteInterceptor::handleDelete);
    }

    public static DeferredResult<Void> handleDelete(Service service, Operation op) {
        DeferredResult<Void> dr = new DeferredResult<>();
        ServiceDocument document = service.getState(op);
        if (document == null || document.documentSelfLink == null) {
            return DeferredResult.completed(null);
        }

        DeferredResult.completed(null)
                .thenCompose((ignore) -> {
                    // If authorization is disabled, skip security context check
                    if (!service.getHost().isAuthorizationEnabled()) {
                        return DeferredResult.completed(null);
                    }

                    // otherwise, check if the user has the privilege to the delete resources
                    return SecurityContextUtil.getSecurityContextForCurrentUser(service)
                            .thenCompose(sc -> {
                                // only cloud admins are permitted to delete resources
                                // project admins are not
                                if (!sc.isCloudAdmin()) {
                                    String errorMessage = String.format(
                                            UNAUTHORIZED_ACCESS_FOR_ACTION_OF_RESOURCES_MESSAGE,
                                            Action.DELETE,
                                            document.getClass().getSimpleName(),
                                            document.documentSelfLink);
                                    service.getHost().log(Level.SEVERE, errorMessage);
                                    return DeferredResult
                                            .failed(new IllegalAccessError(errorMessage));
                                }
                                return DeferredResult.completed(sc);
                            });
                }).whenComplete((v, ex) -> {
                    if (ex != null) {
                        service.getHost().log(Level.WARNING,"Failed to delete %s [%s]: %s",
                                document.getClass().getSimpleName(),
                                document.documentSelfLink, Utils.toString(ex));
                        dr.fail(ex);
                    } else {
                        dr.complete(null);
                    }
                });
        return dr;
    }
}
