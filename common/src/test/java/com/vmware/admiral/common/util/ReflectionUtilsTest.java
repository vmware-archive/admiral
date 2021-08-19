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

package com.vmware.admiral.common.util;

import static org.junit.Assert.assertEquals;

import javax.ws.rs.Path;

import org.junit.Test;

import com.vmware.admiral.common.util.ReflectionUtils.CustomPath;

public class ReflectionUtilsTest {

    @Path("/foo")
    class DummyClass {
    }

    @Test
    public void testSetPathAnnotation() {

        assertEquals("/foo", DummyClass.class.getAnnotation(Path.class).value());

        ReflectionUtils.setAnnotation(DummyClass.class, Path.class, new CustomPath("/bar"));

        assertEquals("/bar", DummyClass.class.getAnnotation(Path.class).value());

        ReflectionUtils.setAnnotation(DummyClass.class, Path.class, new CustomPath("/foo"));
    }

}
