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

package com.vmware.admiral.closures.drivers.nashorn;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.closures.drivers.ExecutionDriver;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public abstract class LocalDriverBase implements ExecutionDriver {

    private static final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(10,
            new ThreadPoolExecutor.AbortPolicy());

    private final Map<String, Future<?>> submittedTasks = new HashMap<>();

    @Override
    public void executeClosure(Closure closureRequest, ClosureDescription taskDef, String token, Consumer<Throwable>
            errorHandler) {
        // Lease the closure and proceed
        Closure leasedClosure = new Closure();
        closureRequest.copyTo(leasedClosure);
        leasedClosure.state = TaskStage.STARTED;
        leasedClosure.inputs = closureRequest.inputs;
        leasedClosure.outputs = closureRequest.outputs;

        URI uri = UriUtils.buildUri(getServiceHost(), leasedClosure.documentSelfLink);
        logInfo("Leasing closure with uri: " + uri + " -> " + leasedClosure.state);
        getServiceHost().sendRequest(Operation
                .createPatch(uri)
                .setReferer(getServiceHost().getUri())
                .setBody(leasedClosure)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        Utils.logWarning("Closure execution aborted! Unable to lease closure with URI: {}, Reason: {} ",
                                uri.toString(),
                                Utils.toString(ex));
                        errorHandler.accept(ex);
                        return;
                    }

                    proceedWithExecution(closureRequest, taskDef);
                }));

    }

    @Override
    public void cleanClosure(Closure closure, Consumer<Throwable> errorHandler) {
        logInfo("Cancelling execution of closure : " + closure.documentSelfLink);

        String documentSelfLink = closure.documentSelfLink;

        Future<?> futureTask = submittedTasks.get(documentSelfLink);
        if (futureTask == null) {
            Utils.logWarning("Unable to cancel closure: " + documentSelfLink);
            return;
        }

        futureTask.cancel(true);

        submittedTasks.remove(documentSelfLink);

    }

    private void proceedWithExecution(Closure closureRequest, ClosureDescription taskDef) {
        logInfo("Fetching leased closure: " + closureRequest.documentSelfLink);
        getServiceHost().sendRequest(Operation
                .createGet(getServiceHost(), closureRequest.documentSelfLink)
                .setReferer(getServiceHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        Utils.logWarning("Failed to fetch closure before execution! Reason:" + ex.getMessage());
                        o.fail(new Exception("Unable to fetch closure."));
                    } else {
                        Closure closure = o.getBody(Closure.class);
                        logInfo("Closure leased. state: {} {}", closure.state, closure.inputs);
                        executeLocal(closure, taskDef);
                        o.complete();
                    }
                }));
    }

    private void executeLocal(Closure closureRequest, ClosureDescription taskDef) {
        String taskSelfLink = buildSelfLink(closureRequest);
        Future<?> futureTask = executor.submit(() -> {
            Closure result = doExecute(closureRequest, taskDef);
            result.inputs = closureRequest.inputs;
            result.closureSemaphore = closureRequest.closureSemaphore;
            result.documentSelfLink = taskSelfLink;
            sendSelfPatch(result);
        });

        submittedTasks.put(taskSelfLink, futureTask);
    }

    private String buildSelfLink(Closure closureRequest) {
        try {
            return UriUtils.buildUriPath(ClosureFactoryService.FACTORY_LINK,
                    UriUtils.getLastPathSegment(new URI(closureRequest.documentSelfLink)));
        } catch (URISyntaxException e) {
            logError("Exception while building self link:", e);
            throw new RuntimeException("Wrong URI provided");
        }
    }

    private void sendSelfPatch(Closure body) {
        URI uri = UriUtils.buildUri(getServiceHost(), body.documentSelfLink);
        logInfo("Executing self patching of: " + uri);
        getServiceHost().sendRequest(Operation
                .createPatch(uri)
                .setReferer(getServiceHost().getUri())
                .setBody(body)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        Utils.logWarning("Self patch failed: {}", Utils.toString(ex));
                    }
                }));
    }

    protected abstract Closure doExecute(Closure runnerRequest, ClosureDescription closureDescription);

    protected void logInfo(String message, Object... values) {
        Utils.log(getClass(), getClass().getSimpleName(), Level.INFO, message, values);
    }

    protected void logError(String message, Object... values) {
        Utils.log(getClass(), getClass().getSimpleName(), Level.SEVERE, message, values);
    }
}
