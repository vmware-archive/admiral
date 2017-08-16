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

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.closures.drivers.docker.ClosureDockerClientFactoryImpl;
import com.vmware.admiral.closures.drivers.docker.DockerDriverBase;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class DockerDriverBaseTest {

    private DockerDriverBase dockerDriver;

    @Before
    public void setup() {
        ServiceHost host = mock(ServiceHost.class);
        when(host.getUri()).thenReturn(UriUtils.buildUri("http://test_uri"));
        DriverRegistry driverRegistry = mock(DriverRegistry.class);
        dockerDriver = new TestDriver(host, driverRegistry, new
                ClosureDockerClientFactoryImpl(host)) {
            @Override
            public String getDockerImage() {
                return "test_image";
            }
        };
    }

    @Test
    public void cleanImageNoLinkTest() {
        final Object[] errors = { null };
        dockerDriver.cleanImage("test_image", null, (error) -> errors[0] = error
        );

        assertNotNull(errors[0]);
    }

    @Test
    public void cleanImageNoImageTest() {
        final Object[] errors = { null };
        dockerDriver.cleanImage("", "testLink", (error) -> errors[0] = error
        );

        assertNotNull(errors[0]);
    }

    @Test
    public void cleanClosureNoLinkTest() {
        final Object[] errors = { null };
        Closure closure = new Closure();
        dockerDriver.cleanClosure(closure, (error) -> errors[0] = error
        );

        assertNotNull(errors[0]);
    }

    @Test
    public void inspectImageClosureTest() {
        final Object[] errors = { null };
        Closure closure = new Closure();
        dockerDriver.inspectImage("test_image", "image_link", (error) -> errors[0] = error
        );

        assertNull(errors[0]);
    }

    @Test
    public void inspectImageNoImageLinkTest() {
        final Object[] errors = { null };
        Closure closure = new Closure();
        dockerDriver.inspectImage("test_image", null, (error) -> errors[0] = error
        );

        assertNotNull(errors[0]);
    }

    @Test
    public void buildConfiguredCallbackUriTest() {
        assertEquals("http://callbackUri/testLink", dockerDriver.buildConfiguredCallbackUri
                ("http://callbackUri", "/testLink").toString());

        assertEquals("http://callbackUri/testLink", dockerDriver.buildConfiguredCallbackUri
                ("http://callbackUri/", "/testLink").toString());
    }

    @Test
    public void buildConfiguredCallbackInvalidUriTest() {
        assertNull(dockerDriver.buildConfiguredCallbackUri("http://callbackUri", "/ invalid ;;;"));
    }

    @Test
    public void buildConfiguredCallbackSecondInvalidUriTest() {
        assertNull(dockerDriver.buildConfiguredCallbackUri(null, "/testLink"));
    }

    private static class TestDriver extends DockerDriverBase {

        public TestDriver(ServiceHost serviceHost,
                DriverRegistry driverRegistry,
                ClosureDockerClientFactory dockerClientFactory) {
            super(serviceHost, driverRegistry, dockerClientFactory);
        }

        @Override public String getDockerImage() {
            return "test_image";
        }
    }

}
