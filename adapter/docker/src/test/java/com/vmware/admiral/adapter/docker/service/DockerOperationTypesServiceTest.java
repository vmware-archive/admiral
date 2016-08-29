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

package com.vmware.admiral.adapter.docker.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.host.HostInitAdapterServiceConfig;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class DockerOperationTypesServiceTest extends BaseTestCase {

    @Before
    public void setUp() throws Throwable {
        HostInitAdapterServiceConfig.startServices(host, false);
        waitForServiceAvailability(DockerOperationTypesService.SELF_LINK);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetAllDockerOperationTypes() throws Throwable {
        List<String>[] result = new ArrayList[1];
        Operation get = Operation
                .createGet(UriUtils.buildUri(host, DockerOperationTypesService.SELF_LINK))
                .setReferer(host.getReferer())
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.failIteration(e);
                            } else {
                                result[0] = o.getBody(List.class);
                                host.completeIteration();
                            }
                        });

        host.testStart(1);
        host.send(get);
        host.testWait();

        List<String> operationIds = result[0];
        assertEquals(ContainerOperationType.values().length, operationIds.size());
        for (String oprId : operationIds) {
            assertNotNull(ContainerOperationType.instanceById(oprId));
        }
    }
}
