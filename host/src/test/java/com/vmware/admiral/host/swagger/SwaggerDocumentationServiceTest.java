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
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import io.swagger.models.Scheme;
import io.swagger.util.Json;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class SwaggerDocumentationServiceTest extends SwaggerDocumentationBaseTestCase {


    @Before
    public void setUp() {
        host.startService(new SwaggerDocumentationService()
                .setIncludePackages("com.vmware.admiral.host.swagger")
                .setSchemes(Scheme.HTTP)
        );
    }

    @Test
    @Ignore
    public void testHandleGet() throws IOException {

        String expected = Json.pretty(expectedSwagger);

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
