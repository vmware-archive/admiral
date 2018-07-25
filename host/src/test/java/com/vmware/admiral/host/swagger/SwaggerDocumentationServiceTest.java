/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host.swagger;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class SwaggerDocumentationServiceTest extends BaseTestCase {

    @Before
    public void setUp() {
        host.startService(new SwaggerDocumentationService());
    }

    @Test
    public void testHandleGet() throws IOException {

        URL swaggerJsonFile = Resources.getResource("swagger-ui.json");
        String expected = Resources.toString(swaggerJsonFile, Charsets.UTF_8);

        List<String> actual = new LinkedList<>();

        Operation getOp = Operation.createGet(UriUtils.buildUri(host, SwaggerDocumentationService.SELF_LINK))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE, "Can't retrieve swagger-ui.json content");
                        host.failIteration(e);
                    } else {
                        actual.add(o.getBody(String.class));
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(getOp);
        host.testWait();

        assertEquals(expected, actual.get(0));
    }
}