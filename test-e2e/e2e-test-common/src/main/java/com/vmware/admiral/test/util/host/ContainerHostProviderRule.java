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

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class ContainerHostProviderRule implements TestRule {

    private final Logger LOG = Logger.getLogger(getClass().getName());

    private final boolean USE_SERVER_CERTIFICATE;
    private final boolean USE_CLIENT_VERIFICATION;
    private final ContainerHostProvider PROVIDER;
    private Future<ContainerHost> future;
    private boolean keepOnFailure = false;

    public ContainerHostProviderRule(boolean useServerCertificate, boolean useClientVerification) {
        String className = System.getProperty("container.host.provider");
        Objects.requireNonNull(className,
                "The System property 'container.host.provider' must be set");
        Class<?> providerClass;
        try {
            providerClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    String.format("Could not find class with name '%s', error: %s",
                            className, ExceptionUtils.getStackTrace(e)));
        }
        Object obj;
        try {
            obj = providerClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(
                    String.format(
                            "Could not instantiate container host provider, error: %s",
                            ExceptionUtils.getStackTrace(e)));
        }
        ContainerHostProvider provider;
        try {
            provider = (ContainerHostProvider) obj;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        this.PROVIDER = provider;
        this.USE_SERVER_CERTIFICATE = useServerCertificate;
        this.USE_CLIENT_VERIFICATION = useClientVerification;
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
                } finally {
                    future.get();
                    if (!keepOnFailure) {
                        PROVIDER.killContainerHost();
                    }
                }
            }
        };
    }

    public ContainerHostProviderRule keepOnFailure(boolean keepOnFailure) {
        this.keepOnFailure = keepOnFailure;
        return this;
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

}
