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

package com.vmware.admiral.adapter.docker.util;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test the toString function of DockerPortMapping
 */
@RunWith(Parameterized.class)
public class DockerPortMappingToStringTest {
    private final String description;
    private final String portBinding;


    public DockerPortMappingToStringTest(String description, String portBinding) {
        this.description = description;
        this.portBinding = portBinding;
    }

    @Parameters
    public static List<String[]> data() {
        return Arrays.asList(new String[][] {
                { "all segments", "0.0.0.0:6666:7777/udp" },
                { "host ip and container port", "0.0.0.0::7777/tcp" },
                { "host port and container port", "6666:7777/tcp" },
                { "container port only", "7777/tcp" }
        });
    }

    @Test
    public void testToString() {
        DockerPortMapping portMapping = DockerPortMapping.fromString(portBinding);
        assertEquals(description, portBinding, portMapping.toString());

    }
}
