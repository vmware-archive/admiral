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

package com.vmware.admiral.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.host.interceptor.OperationInterceptorRegistry;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.test.VerificationHost;

public class HostInitCommonServiceConfigTest {

    private VerificationHost host;

    private static Object classes;
    private static Object intTimeout;

    @BeforeClass
    public static void beforeClass() throws Exception {
        // store original values of the class and replace them
        classes = getOldAndSetNewValue("servicesToStart", new Class[0]);
        intTimeout = getOldAndSetNewValue("COMMON_SERVICES_AVAILABILITY_TIMEOUT", 5);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        // restore original values of the class
        getOldAndSetNewValue("servicesToStart", classes);
        getOldAndSetNewValue("COMMON_SERVICES_AVAILABILITY_TIMEOUT", intTimeout);
    }

    @Before
    public void before() throws Throwable {
        host = createHost();
    }

    @After
    public void after() throws Throwable {
        try {
            host.tearDown();
        } catch (CancellationException e) {
            host.log(Level.FINE, e.getClass().getName());
        }
        host = null;
    }

    @Test
    public void testStartServices() throws Throwable {
        MockCommonInitialBootService mockCommonInitialBootService =
                new MockCommonInitialBootService();
        host.startServiceAndWait(mockCommonInitialBootService,
                MockCommonInitialBootService.SELF_LINK,
                new ServiceDocument());

        mockCommonInitialBootService.behaviour = 0;
        HostInitCommonServiceConfig.startServices(host);
    }

    @Test
    public void testStartServicesWithError() throws Throwable {
        MockCommonInitialBootService mockCommonInitialBootService =
                new MockCommonInitialBootService();
        host.startServiceAndWait(mockCommonInitialBootService,
                MockCommonInitialBootService.SELF_LINK,
                new ServiceDocument());

        try {
            mockCommonInitialBootService.behaviour = 1;
            HostInitCommonServiceConfig.startServices(host);
            fail("should not get here");
        } catch (Exception e) {
            assertEquals("test-error", e.getCause().getMessage());
        }
    }

    @Test
    public void testStartServicesWithTimeout() throws Throwable {
        MockCommonInitialBootService mockCommonInitialBootService =
                new MockCommonInitialBootService();
        host.startServiceAndWait(mockCommonInitialBootService,
                MockCommonInitialBootService.SELF_LINK,
                new ServiceDocument());

        try {
            mockCommonInitialBootService.behaviour = 2;
            HostInitCommonServiceConfig.startServices(host);
            fail("should not get here");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof TimeoutException);
            assertTrue(e.getCause().getMessage().contains("common services timed out"));
        }
    }

    private VerificationHost createHost() throws Throwable {
        OperationInterceptorRegistry interceptors = new OperationInterceptorRegistry();

        ServiceHost.Arguments args = new ServiceHost.Arguments();
        args.sandbox = null; // ask runtime to pick a random storage location
        args.port = 0; // ask runtime to pick a random port
        args.isAuthorizationEnabled = false;

        VerificationHost h = new VerificationHost();

        h = VerificationHost.initialize(h, args);
        h.start();

        return h;
    }

    private static Object getOldAndSetNewValue(String fieldName, Object newValue) throws Exception {
        Field f = HostInitCommonServiceConfig.class.getDeclaredField(fieldName);
        f.setAccessible(true);

        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);

        Object o = f.get(null);
        f.set(null, newValue);
        return o;
    }

    class MockCommonInitialBootService extends StatelessService {

        public static final String SELF_LINK = ManagementUriParts.CONFIG + "/common-initial-boot";
        /**
         * 0 - complete successful
         * 1 - throw error immediately
         * 2 - wait too long, so operation times out
         */
        int behaviour = 0;

        @Override
        public String getSelfLink() {
            return SELF_LINK;
        }

        @Override
        public void handlePost(Operation post) {
            if (behaviour == 1) {
                throw new IllegalArgumentException("test-error");
            } else if (behaviour == 2) {
                // do nothing, so operation will time out
            } else {
                post.complete();
            }
        }

    }

}