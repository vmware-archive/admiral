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

package com.vmware.admiral.test.util.host;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public abstract class AbstractContainerHostProviderRule implements TestRule {

    private final Logger LOG = Logger.getLogger(getClass().getName());

    protected final boolean USE_SERVER_CERTIFICATE;
    protected final boolean USE_CLIENT_VERIFICATION;
    protected final ContainerHostProvider PROVIDER;
    private Future<ContainerHost> future;
    private boolean keepOnFailure = false;

    public AbstractContainerHostProviderRule(boolean useServerCertificate,
            boolean useClientVerification) {
        this.USE_SERVER_CERTIFICATE = useServerCertificate;
        this.USE_CLIENT_VERIFICATION = useClientVerification;
        PROVIDER = getProvider();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                future = Executors.newSingleThreadExecutor().submit(
                        () -> PROVIDER.provide(USE_SERVER_CERTIFICATE, USE_CLIENT_VERIFICATION));
                try {
                    base.evaluate();
                    killContainerHost();
                } catch (Throwable e) {
                    if (!keepOnFailure) {
                        killContainerHost();
                    }
                    throw e;
                }
            }
        };
    }

    public ContainerHost getHost() {
        if (!future.isDone()) {
            LOG.info("Container host is still deploying, waiting for deployment to finish...");
        }
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(
                    "Could not provide container host, error: " + ExceptionUtils.getStackTrace(e));
        }
    }

    private void killContainerHost() {
        try {
            getHost();
            PROVIDER.killContainerHost();
        } catch (Throwable e) {
            LOG.warning(
                    String.format("Could not kill container host, error: %s,%s",
                            e.getMessage(),
                            ExceptionUtils.getStackTrace(e)));
        }
    }

    protected abstract ContainerHostProvider getProvider();

}
