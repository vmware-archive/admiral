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

package com.vmware.admiral.common.serialization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.common.serialization.ReleaseConstants.API_VERSION_0_9_1;
import static com.vmware.admiral.common.serialization.ReleaseConstants.CURRENT_API_VERSION;
import static com.vmware.admiral.common.serialization.ReleaseConstants.VERSION_PREFIX;
import static com.vmware.xenon.common.Operation.MEDIA_TYPE_APPLICATION_JSON;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.xenon.common.Operation;

public class ThreadLocalVersionHolderTest {

    private ExecutorService executor;

    @Before
    public void before() throws Exception {
        executor = Executors.newFixedThreadPool(2);
    }

    @After
    public void after() throws Exception {
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    public void testSetVersion() throws Exception {

        Callable<Boolean> task1 = createTask(API_VERSION_0_9_1, false);
        Callable<Boolean> task2 = createTask(CURRENT_API_VERSION, false);

        List<Future<Boolean>> futures = executor.invokeAll(Arrays.asList(task1, task2));
        assertEquals(2, futures.size());

        for (Future<Boolean> future : futures) {
            assertTrue("Version holder should work OK!", future.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testSetVersionFromOperation() throws Exception {

        Callable<Boolean> task1 = createTask(API_VERSION_0_9_1, true);
        Callable<Boolean> task2 = createTask(CURRENT_API_VERSION, true);

        List<Future<Boolean>> futures = executor.invokeAll(Arrays.asList(task1, task2));
        assertEquals(2, futures.size());

        for (Future<Boolean> future : futures) {
            assertTrue("Version holder should work OK!", future.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testVersionNotSet() {

        String currentVersion = ThreadLocalVersionHolder.getVersion();
        assertNull(currentVersion);

        Operation op = new Operation();
        ThreadLocalVersionHolder.setVersion(op);
        assertNull(currentVersion);

        op = new Operation();
        op.addRequestHeader(Operation.ACCEPT_HEADER, MEDIA_TYPE_APPLICATION_JSON);

        ThreadLocalVersionHolder.setVersion(op);
        assertNull(currentVersion);

        op = new Operation();
        op.addRequestHeader(Operation.ACCEPT_HEADER, MEDIA_TYPE_APPLICATION_JSON + ";foo=bar");

        ThreadLocalVersionHolder.setVersion(op);
        assertNull(currentVersion);
    }

    private Callable<Boolean> createTask(String version, boolean createOperation) {
        return new Callable<Boolean>() {
            @Override
            public Boolean call() {
                String currentVersion = ThreadLocalVersionHolder.getVersion();
                assertNull(currentVersion);

                if (createOperation) {
                    ThreadLocalVersionHolder.setVersion(createOperation(version));
                } else {
                    ThreadLocalVersionHolder.setVersion(version);
                }

                currentVersion = ThreadLocalVersionHolder.getVersion();
                assertNotNull(currentVersion);
                assertEquals(version, currentVersion);

                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                } catch (InterruptedException e) {
                    return false;
                }

                currentVersion = ThreadLocalVersionHolder.getVersion();
                assertNotNull(currentVersion);
                assertEquals(version, currentVersion);

                ThreadLocalVersionHolder.clearVersion();

                currentVersion = ThreadLocalVersionHolder.getVersion();
                assertNull(currentVersion);

                return true;
            }
        };
    }

    private Operation createOperation(String version) {
        Operation op = new Operation();
        String acceptWithVersion = MEDIA_TYPE_APPLICATION_JSON + ";" + VERSION_PREFIX + version;
        op.addRequestHeader(Operation.ACCEPT_HEADER, acceptWithVersion);
        return op;
    }

}
