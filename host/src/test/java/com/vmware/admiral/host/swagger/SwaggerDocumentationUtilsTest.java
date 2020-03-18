/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host.swagger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import io.swagger.models.Model;
import io.swagger.models.Tag;
import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.common.SwaggerDocumentation;

public class SwaggerDocumentationUtilsTest extends SwaggerDocumentationBaseTestCase {

    @Test
    public void testGetApiTagsAsList() {
        List<Tag> actualTags = SwaggerDocumentationUtils.getApiTagsAsList(AnnotatedServiceMock.class);
        List<Tag> expectedTags = new ArrayList<>();
        expectedTags.add(new Tag().name(API_TAG));

        Assert.assertEquals(expectedTags, actualTags);
    }

    @Test
    public void testGetApiMethods() throws NoSuchMethodException {
        List<Method> actualMethods = SwaggerDocumentationUtils.getApiMethods(AnnotatedServiceMock.class).collect(Collectors.toList());
        List<Method> expectedMethods = new LinkedList<>();
        expectedMethods.add(AnnotatedServiceMock.class.getMethod("handleGet"));
        expectedMethods.add(AnnotatedServiceMock.class.getMethod("handlePost"));

        Assert.assertTrue(expectedMethods.containsAll(actualMethods));
    }

    @Test
    public void testMethodPath() throws NoSuchMethodException {
        String actualPath = SwaggerDocumentationUtils.methodPath(AnnotatedServiceMock.class.getMethod("handleGet"));

        Assert.assertEquals(SwaggerDocumentation.INSTANCE_PATH, actualPath);
    }

    @Test
    public void testModel() {
        Model actualModel = SwaggerDocumentationUtils.model(AnnotatedServiceMock.AnnotatedServiceDocumentMock.class);

        Assert.assertEquals(model, actualModel);
    }
}
