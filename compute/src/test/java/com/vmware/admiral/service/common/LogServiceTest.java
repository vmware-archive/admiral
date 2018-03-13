/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.service.common.LogService.LogServiceState;
import com.vmware.xenon.common.Operation;

public class LogServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(LogService.FACTORY_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
    }

    @Test
    public void testLog() throws Throwable {
        LogServiceState logState = new LogServiceState();
        logState.logs = "Test-file234".getBytes();

        LogServiceState newLogState = doPost(logState, LogService.FACTORY_LINK);

        assertEquals(new String(logState.logs), new String(newLogState.logs));
    }

    @Test
    public void testMaxLogSize() {
        int maxLogSize = LogService.MAX_LOG_SIZE;
        assertEquals(LogService.DEFAULT_MAX_LOG_SIZE_VALUE, maxLogSize);
    }

    @Test
    public void testHandleMaintenance() throws Throwable {
        LogServiceStub logService = new LogServiceStub();
        logService.setHost(host);

        host.startFactory(logService);
        waitForServiceAvailability(LogServiceStub.FACTORY_LINK);

        LogServiceState logServiceState = doPost(new LogServiceState(),
                LogServiceStub.FACTORY_LINK);

        logService.doMaintenance(logServiceState.documentSelfLink);

        logServiceState = getDocumentNoWait(LogServiceState.class,
                logServiceState.documentSelfLink);
        assertNull(logServiceState);
    }

    public static class LogServiceStub extends LogService {

        private static final long EXPIRATION_TIME = Long.MIN_VALUE;
        public static final String FACTORY_LINK = ManagementUriParts.LOGS + "-stub";

        public void doMaintenance(String selfLink) throws Throwable {
            Operation post = Operation.createPost(getHost(), FACTORY_LINK);
            AtomicBoolean completed = new AtomicBoolean();
            post.nestCompletion(op -> completed.set(true));
            super.doMaintenance(post, selfLink, EXPIRATION_TIME);

            waitFor(completed::get);
            logInfo("Maintenance completed. Operation status = %d", post.getStatusCode());
        }

    }

}
