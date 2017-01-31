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

package com.vmware.admiral.closures.drivers;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of execution driver registry.
 *
 * <DriverRegistry, ExecutionDriver>
 */
public final class DriverRegistryImpl implements DriverRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DriverRegistryImpl.class);

    private final Map<String, String> supportedRuntimes = new HashMap<>();
    private final Map<String, ExecutionDriver> executionDrivers = new HashMap<>();

    public DriverRegistryImpl() {
        supportedRuntimes.put(DriverConstants.RUNTIME_NODEJS_4, DriverConstants.NODEJS_4_IMAGE);
        supportedRuntimes.put(DriverConstants.RUNTIME_PYTHON_3, DriverConstants.PYTHON_3_IMAGE);
        supportedRuntimes.put(DriverConstants.RUNTIME_NASHORN, null);
    }

    public void register(String runtime, ExecutionDriver driver) {
        logger.info("Registering execution driver for supported runtimes: {} ",
                String.join(", ", getSupportedRuntimes().keySet()));
        executionDrivers.put(runtime, driver);
    }

    public ExecutionDriver getDriver(String runtime) {
        if (executionDrivers.containsKey(runtime)) {
            return executionDrivers.get(runtime);
        }

        throw new IllegalArgumentException("No available driver for runtime: " + runtime);
    }

    @Override
    public ExecutionDriver getDriver() {
        if (executionDrivers.size() > 0) {
            return executionDrivers.get(DriverConstants.RUNTIME_NODEJS_4);
        }

        throw new IllegalArgumentException("No available execution driver!" + executionDrivers.size());
    }

    @Override
    public Map<String, String> getSupportedRuntimes() {
        return supportedRuntimes;
    }

}

