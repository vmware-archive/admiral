/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.closures.services;

import static org.junit.Assert.assertNotNull;

import java.util.Map;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.closures.drivers.DriverConstants;
import com.vmware.admiral.closures.drivers.DriverRegistry;
import com.vmware.admiral.closures.drivers.DriverRegistryImpl;
import com.vmware.admiral.closures.drivers.ExecutionDriver;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.xenon.common.ServiceHost;

public class DriverRegistryImplTest {

    private DriverRegistry registry;

    @Before
    public void setUp() throws Exception {
        registry = new DriverRegistryImpl();

        Map<String, String> runtimes = registry.getSupportedRuntimes();
        runtimes.forEach((r, image) -> registry
                .register(r, new ExecutionDriver() {
                    @Override public void executeClosure(Closure closure,
                            ClosureDescription closureDescription,
                            String token, Consumer<Throwable> errorHandler) {

                    }

                    @Override public void cleanClosure(Closure closure,
                            Consumer<Throwable> errorHandler) {

                    }

                    @Override public void cleanImage(String imageName, String computeStateLink,
                            Consumer<Throwable> errorHandler) {

                    }

                    @Override public void inspectImage(String imageName, String computeStateLink,
                            Consumer<Throwable> errorHandler) {

                    }

                    @Override public ServiceHost getServiceHost() {
                        return null;
                    }
                }));
    }

    @Test
    public void testSupportedRuntime() {
        assertNotNull(registry.getDriver(DriverConstants.RUNTIME_NODEJS_4));
        assertNotNull(registry.getDriver(DriverConstants.RUNTIME_JAVA_8));
        assertNotNull(registry.getDriver(DriverConstants.RUNTIME_POWERSHELL_6));
        assertNotNull(registry.getDriver(DriverConstants.RUNTIME_PYTHON_3));
    }

    @Test
    public void testSupportedRuntimeVersion() {
        assertNotNull(registry.getImageVersion(DriverConstants.RUNTIME_NODEJS_4));
        assertNotNull(registry.getImageVersion(DriverConstants.RUNTIME_JAVA_8));
        assertNotNull(registry.getImageVersion(DriverConstants.RUNTIME_POWERSHELL_6));
        assertNotNull(registry.getImageVersion(DriverConstants.RUNTIME_PYTHON_3));

        assertNotNull(registry.getBaseImageVersion(DriverConstants.RUNTIME_NODEJS_4));
        assertNotNull(registry.getBaseImageVersion(DriverConstants.RUNTIME_JAVA_8));
        assertNotNull(registry.getBaseImageVersion(DriverConstants.RUNTIME_POWERSHELL_6));
        assertNotNull(registry.getBaseImageVersion(DriverConstants.RUNTIME_PYTHON_3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRuntime() {
        assertNotNull(registry.getDriver("bash"));
    }

}
