/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import com.vmware.admiral.auth.idm.AuthConfigProvider;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * One-time node group setup (bootstrap) for the auth settings.
 *
 * This service is guaranteed to be performed only once within entire node group, in a consistent
 * safe way. Durable for restarting the owner node or even complete shutdown and restarting of all
 * nodes. Following the SampleBootstrapService.
 */
public class AuthBootstrapService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.CONFIG + "/auth-bootstrap";

    public static final long AUTH_INIT_TIMEOUT_MICROS = TimeUnit.SECONDS.toMicros(Integer
            .getInteger("com.vmware.admiral.service.common.auth.bootstrap.timeout.seconds",
                    180));

    public static FactoryService createFactory() {
        return FactoryService.create(AuthBootstrapService.class);
    }

    public static CompletionHandler startTask(ServiceHost host) {
        return (o, e) -> {
            if (e != null) {
                host.log(Level.SEVERE, Utils.toString(e));
                return;
            }
            // create service with fixed link
            // POST will be issued multiple times but will be converted to PUT after the first one.
            ServiceDocument doc = new ServiceDocument();
            doc.documentSelfLink = "auth-preparation-task";
            Operation.createPost(host, AuthBootstrapService.FACTORY_LINK)
                    .setBody(doc)
                    .setReferer(host.getUri())
                    .setCompletion((oo, ee) -> {
                        if (ee != null) {
                            host.log(Level.SEVERE, Utils.toString(ee));
                            return;
                        }
                        host.log(Level.INFO, "auth-preparation-task triggered");
                    })
                    .sendWith(host);
        };
    }

    public AuthBootstrapService() {
        super(ServiceDocument.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {
        // Initialize authentication provider, and as this could be a slow operation (in case of
        // PSC integration in VIC) the operation is completed immediately.
        // Then, later if there's error during initialization it is logged and the host is stopped.

        logInfo("Auth bootstrap starting");

        AuthConfigProvider provider = AuthUtil.getPreferredAuthConfigProvider();

        boolean executeBootConfig = ServiceHost.isServiceCreate(post);
        CountDownLatch latch = new CountDownLatch(executeBootConfig ? 2 : 1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        // initBootConfig
        if (executeBootConfig) {
            // do not perform bootstrap logic when the post is NOT from direct client, eg: node
            // restart
            Operation op = Operation.createGet(this, "/")
                    .setExpiration(Utils.fromNowMicrosUtc(AUTH_INIT_TIMEOUT_MICROS))
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            logSevere("Auth bootstrap init boot error: %s", Utils.toString(e));
                            error.set(e);
                        }
                        latch.countDown();
                    });
            provider.initBootConfig(getHost(), op, null);
        }

        // initConfig
        Operation op = Operation.createGet(this, "/")
                .setExpiration(Utils.fromNowMicrosUtc(AUTH_INIT_TIMEOUT_MICROS))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Auth bootstrap init error: %s", Utils.toString(e));
                        error.set(e);
                    }
                    latch.countDown();
                });
        provider.initConfig(getHost(), op, null);

        // wait for results and stop the host in case of an error
        new Thread(() -> {
            try {
                latch.await();
                if (error.get() != null) {
                    logSevere("Auth bootstrap error detected, stopping host!");
                    getHost().stop();
                } else {
                    logInfo("Auth bootstrap finished successfully");
                }
            } catch (InterruptedException e) {
                logSevere("Auth bootstrap start interrupted while waiting for initialization,"
                                + " stopping host! Error: %s", Utils.toString(e));
                getHost().stop();
            }
        }).start();

        // complete the operation
        post.complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            // converted PUT due to IDEMPOTENT_POST option
            logInfo("Task has already started. Ignoring converted PUT.");
            put.complete();
            return;
        }
        // normal PUT is not supported
        put.fail(Operation.STATUS_CODE_BAD_METHOD);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

}
