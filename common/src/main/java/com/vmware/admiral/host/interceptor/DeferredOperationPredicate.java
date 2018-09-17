/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host.interceptor;

import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.logging.Level;

import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.OperationProcessingChain.Filter;
import com.vmware.xenon.common.OperationProcessingChain.FilterReturnCode;
import com.vmware.xenon.common.OperationProcessingChain.OperationProcessingContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;

/**
 * A predicate with deferred result that can be added to an {@link OperationProcessingChain}.
 */
public class DeferredOperationPredicate implements Filter {
    private final Service service;
    private final Action action;
    private final BiFunction<Service, Operation, DeferredResult<Void>> predicate;

    /**
     * Constructs a new instance.
     *
     * @param service   the service that is intercepted
     * @param action    the action that is intercepted
     * @param predicate the predicate code which should return {@code null} if not applicable, or
     *                  a DeferredResult; a failed DeferredResult cancels the operation
     */
    public DeferredOperationPredicate(Service service, Action action,
            BiFunction<Service, Operation, DeferredResult<Void>> predicate) {
        this.service = service;
        this.action = action;
        this.predicate = predicate;
    }

    @Override
    public FilterReturnCode processRequest(Operation operation,
            OperationProcessingContext context) {
        if (operation.isSynchronize() || operation.isFromReplication()) {
            // do not process synchronization or replication operations
            return FilterReturnCode.CONTINUE_PROCESSING;
        }

        if (action != null && action != operation.getAction()) {
            return FilterReturnCode.CONTINUE_PROCESSING;
        }

        DeferredResult<Void> dr;
        try {
            dr = this.predicate.apply(this.service, operation);
            if (dr == null) {
                return FilterReturnCode.CONTINUE_PROCESSING;
            }
        } catch (Exception e) {
            dr = DeferredResult.failed(e);
        }

        dr.whenComplete((ignore, e) -> {
            if (e != null) {
                this.service.getHost().log(Level.INFO,
                        "Operation interceptor %s: Action: %s returned error: %s",
                        this.service.getClass().getCanonicalName(), this.action,
                        e.toString());

                if (e instanceof CompletionException
                        && e.getCause().getMessage().toLowerCase().contains("forbidden")
                        || e.getMessage().toLowerCase().contains("forbidden")) {
                    operation.fail(Operation.STATUS_CODE_FORBIDDEN, e, e);
                    return;
                }
                operation.fail(e);
            } else {
                this.service.getOperationProcessingChain().resumeProcessingRequest(operation,
                        context, FilterReturnCode.CONTINUE_PROCESSING, null);
            }
        });
        return FilterReturnCode.SUCCESS_STOP_PROCESSING;
    }
}
