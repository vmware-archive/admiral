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

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.closures.drivers.docker.ClosureDockerClientFactoryImpl;
import com.vmware.admiral.closures.drivers.docker.DockerDriverBase;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.xenon.common.ServiceHost;

public class DockerDriverBaseTest {

    private DockerDriverBase dockerDriver;

    @Before
    public void setup() {
        ServiceHost host = mock(ServiceHost.class);
        dockerDriver = new DockerDriverBase(host, new ClosureDockerClientFactoryImpl(host)) {
            @Override
            public String getDockerImage() {
                return "test_image";
            }
        };
    }

    @Test
    public void cleanImageNoLinkTest() {
        final Object[] errors = { null };
        dockerDriver.cleanImage("test_image", null, (error) -> {
                    errors[0] = error;
                }
        );

        assertNotNull(errors[0]);
    }

    @Test
    public void cleanImageNoImageTest() {
        final Object[] errors = { null };
        dockerDriver.cleanImage("", "testLink", (error) -> {
                    errors[0] = error;
                }
        );

        assertNotNull(errors[0]);
    }

    @Test
    public void cleanClosureNoLinkTest() {
        final Object[] errors = { null };
        Closure closure = new Closure();
        dockerDriver.cleanClosure(closure, (error) -> {
                    errors[0] = error;
                }
        );

        assertNotNull(errors[0]);
    }

}
