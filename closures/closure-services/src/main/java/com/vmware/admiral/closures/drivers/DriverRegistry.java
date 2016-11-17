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

import java.util.Map;

/**
 * Registry for execution drivers.
 */
public interface DriverRegistry {

    /**
     * Register an execution driver.
     *
     * @param driver the driver to register.
     */
    void register(String runtime, ExecutionDriver driver);

    /**
     * Returns appropriate execution driver according to provided runtime.
     *
     * @param runtime runtime environment required by the code snippet.
     * @return the appropriate execution driver according to the runtime environment.
     */
    ExecutionDriver getDriver(String runtime);

    /**
     * Returns arbitrary execution driver
     *
     * @return
     */
    ExecutionDriver getDriver();

    /**
     * Returns supported runtime info..
     *
     * @return map with supported runtimes info.
     */
    Map<String, String> getSupportedRuntimes();

}
