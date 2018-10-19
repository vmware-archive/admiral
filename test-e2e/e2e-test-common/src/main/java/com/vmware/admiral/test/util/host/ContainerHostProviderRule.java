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

import org.apache.commons.lang3.exception.ExceptionUtils;

public class ContainerHostProviderRule extends AbstractContainerHostProviderRule {

    public ContainerHostProviderRule(boolean useServerCertificate, boolean useClientVerification) {
        super(useServerCertificate, useClientVerification);
    }

    @Override
    protected ContainerHostProvider getProvider() {
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
        try {
            return (ContainerHostProvider) obj;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

}
