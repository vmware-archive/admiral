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

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.QueryTaskClientHelper;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Abstract class providing initial boot functionality to create system default documents if not
 * already created in the system.
 */
public abstract class AbstractInitialBootService extends StatelessService {
    private static final int RETRIES_COUNT = Integer.getInteger(
            "com.vmware.admiral.service.initial.boot.retries", 3);
    private static final long RETRIES_WAIT = Long.getLong(
            "com.vmware.admiral.service.initial.boot.retries.wait.millis", 1000);

    /**
     * Initialize a list of service default states if not already created. This method could be
     * called only once per instance initialization.
     */
    protected void initInstances(Operation post, ServiceDocument... states) {
        initInstances(post, true, true, states);
    }

    /**
     * Initialize a list of service default states if not already created. This method could be
     * called only once per instance initialization.
     *
     * @param checkIfExists
     *            flag to indicate whether to check if exists first before create or override
     *            existing entries.
     */
    protected void initInstances(Operation post, boolean checkIfExists, boolean selfDelete,
            ServiceDocument... states) {
        final AtomicInteger countDown = new AtomicInteger(states.length);
        final Consumer<Throwable> callback = (e) -> countDown(countDown, selfDelete, post, e);
        for (ServiceDocument state : states) {
            initInstance(state, checkIfExists, callback);
        }
    }

    private void countDown(AtomicInteger countDown, boolean selfDelete, Operation post,
            Throwable e) {
        if (e != null) {
            post.fail(e);
        } else if (countDown.decrementAndGet() == 0) {
            post.complete();
            logInfo("Finish initial boot service: %s", getSelfLink());
            if (selfDelete) {
                logInfo("Stopping initial boot service: %s", getSelfLink());
                sendRequest(Operation.createDelete(getUri()));
            }
        }
    }

    private void initInstance(ServiceDocument state, boolean checkIfExists,
            Consumer<Throwable> callback) {
        String factoryPath = UriUtils.getParentPath(state.documentSelfLink);
        try {
            getHost().registerForServiceAvailability((o, e) -> {
                if (e != null) {
                    logSevere("Error waiting for service: %s. Error: %s",
                            factoryPath, Utils.toString(e));
                    callback.accept(e);
                    return;
                }
                if (checkIfExists) {
                    ensureInstanceExists(state, callback);
                } else {
                    createDefaultInstance(state, callback, RETRIES_COUNT);
                }
            }, factoryPath);
        } catch (Throwable t) {
            logSevere("Error registering for service availability: %s. Error: %s",
                    state.documentSelfLink, (t instanceof CancellationException)
                            ? t.getClass().getName() : Utils.toString(t));
            callback.accept(t);
            return;
        }
    }

    /**
     * Make sure the given instance exists, if not create it using the state supplier
     *
     * The callback will be called when done, whether the instance was created or already existed
     * (will not be called in case of failure to query or create the instance)
     *
     * @param selfLink
     * @param factorySelfLink
     * @param type
     * @param instanceBuilder
     * @param callback
     */
    private void ensureInstanceExists(ServiceDocument state, Consumer<Throwable> callback) {
        final AtomicBoolean documentExists = new AtomicBoolean();
        QueryTaskClientHelper.create(state.getClass())
                .setDocumentLink(state.documentSelfLink)
                .setResultHandler((r, e) -> {
                    if (e != null) {
                        logSevere("Can't query for system document: %s. Error: %s",
                                state.documentSelfLink, (e instanceof CancellationException)
                                        ? e.getClass().getName() : Utils.toString(e));
                        callback.accept(e);
                        return;
                    } else if (r.hasResult()) {
                        documentExists.set(true);
                        logFine("Document %s already exists.", state.documentSelfLink);
                    } else {
                        if (!documentExists.get()) {
                            createDefaultInstance(state, callback, RETRIES_COUNT);
                        } else {
                            callback.accept(null);
                        }
                    }
                }).sendWith(getHost());
    }

    /**
     * create the given instance
     *
     * @param selfLink
     * @param factorySelfLink
     * @param state
     * @param callback
     */
    private void createDefaultInstance(ServiceDocument state, Consumer<Throwable> callback,
            int retryCount) {
        String factoryPath = UriUtils.getParentPath(state.documentSelfLink);
        logInfo("Creating Default instance for %s", state.documentSelfLink);
        sendRequest(Operation
                .createPost(this, factoryPath)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(state)
                .setCompletion(
                        (op, ex) -> {
                            if (ex != null) {
                                if (retryCount > 0 && !(ex instanceof CancellationException)) {
                                    logWarning(
                                            "Retrying with count %s after error creating default %s instance for factory %s. Error: %s",
                                            retryCount, state.documentSelfLink, factoryPath,
                                            Utils.toString(ex));
                                    try {
                                        /* The factory should be up an available since we
                                           registerForServiceAvailability before that.
                                           However, it often fails with service not found.
                                           Hence, the retries and the wait here. */
                                        Thread.sleep(RETRIES_WAIT);
                                        createDefaultInstance(state, callback, retryCount - 1);
                                    } catch (Exception e) {
                                        logWarning("Sleep interrupted %s", Utils.toString(e));
                                        callback.accept(ex);
                                    }
                                } else {
                                    logWarning(
                                            "Error creating default %s instance for factory %s. Error: %s",
                                            state.documentSelfLink, factoryPath,
                                            (ex instanceof CancellationException) ? ex.getClass()
                                                    .getName() : Utils.toString(ex));
                                    callback.accept(ex);
                                }
                                return;
                            }

                            logInfo("Default instance created: %s", state.documentSelfLink);
                            callback.accept(null);
                        }));

    }
}
