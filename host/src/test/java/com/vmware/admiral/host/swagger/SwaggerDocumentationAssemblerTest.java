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

import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import org.junit.Assert;
import org.junit.Test;

public class SwaggerDocumentationAssemblerTest extends SwaggerDocumentationBaseTestCase {

    @Test
    public void testAssembler() {
        Swagger actualSwagger = SwaggerDocumentationAssembler.create()
                .setBasePath("/")
                .setHost("host")
                .setSchemes(new Scheme[]{Scheme.HTTP})
                .setIncludePackages(new String[]{"com.vmware.admiral.host.swagger"})
                .build();

        Assert.assertEquals(expectedSwagger, actualSwagger);
    }

}