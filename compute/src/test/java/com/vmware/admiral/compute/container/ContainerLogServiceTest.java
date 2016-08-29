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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.common.LogService.LogServiceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class ContainerLogServiceTest extends ComputeBaseTest {
    private static final String TEST_LOG_CONTENT = "Test-file234";
    private List<String> documentLinksForDeletion;
    private ContainerState container;
    private LogServiceState logState;

    @Before
    public void setUp() throws Throwable {
        documentLinksForDeletion = new ArrayList<>();
        waitForServiceAvailability(LogService.FACTORY_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainerLogService.SELF_LINK);

        container = new ContainerState();
        container.id = UUID.randomUUID().toString();
        container.names = new ArrayList<>(Arrays.asList("test-name"));
        container = doPost(container, ContainerFactoryService.SELF_LINK);
        documentLinksForDeletion.add(container.documentSelfLink);

        logState = new LogServiceState();
        logState.logs = TEST_LOG_CONTENT.getBytes();
        logState.documentSelfLink = extractId(container.documentSelfLink);
    }

    @After
    public void tearDown() throws Throwable {
        for (String selfLink : documentLinksForDeletion) {
            delete(selfLink);
        }
    }

    @Test
    public void testNoLog() throws Throwable {
        logState = getContainerLog();
        assertEquals(new String("--"), new String(logState.logs));
    }

    @Test
    public void testLog() throws Throwable {
        logState = doPost(logState, LogService.FACTORY_LINK);
        documentLinksForDeletion.add(logState.documentSelfLink);

        LogServiceState currentLogState = getContainerLog();
        assertEquals(TEST_LOG_CONTENT, new String(currentLogState.logs));
    }

    private LogServiceState getContainerLog() throws Throwable {
        LogServiceState[] result = new LogServiceState[] { null };

        host.testStart(1);
        host.send(Operation.createGet(
                UriUtils.buildUri(host, ContainerLogService.SELF_LINK,
                        ContainerLogService.CONTAINER_ID_QUERY_PARAM + "="
                                + extractId(container.documentSelfLink)))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    result[0] = o.getBody(LogServiceState.class);
                    host.completeIteration();
                }));
        host.testWait();

        assertNotNull(result[0]);
        return result[0];
    }

}
