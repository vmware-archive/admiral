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

package com.vmware.admiral.adapter.docker.util;

import static org.junit.Assert.assertEquals;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterUtils.normalizeDockerError;

import org.junit.Test;

public class DockerAdapterUtilTest {

    @Test
    public void testNormalizeDockerErrorWithKnowInput() {

        String sampleInput = "{\"status\":\"Extracting\",\"progressDetail\":{\"current\":20414464,"
                + "\"total\":21686587},\"progress\":\"[=========================================="
                + "=====\\u003e   ]  20.41MB/21.69MB\",\"id\":\"c09556af9686\"}\n"
                + "{\"status\":\"Extracting\",\"progressDetail\":{\"current\":21331968,\"total\""
                + ":21686587},\"progress\":\"[=================================================\\"
                + "u003e ]  21.33MB/21.69MB\",\"id\":\"c09556af9686\"}\n"
                + "{\"status\":\"Extracting\",\"progressDetail\":{\"current\":21561344,\"total\":"
                + "21686587},\"progress\":\"[=================================================\\"
                + "u003e ]  21.56MB/21.69MB\",\"id\":\"c09556af9686\"}\n"
                + "{\"status\":\"Extracting\",\"progressDetail\":{\"current\":21686587,\"total\":"
                + "21686587},\"progress\":\"[==================================================\\"
                + "u003e]  21.69MB/21.69MB\",\"id\":\"c09556af9686\"}\n"
                + "{\"errorDetail\":{\"message\":\"failed to register layer: Error processing tar "
                + "file(exit status 1): write /usr/local/lib/python2.7/lib-dynload/_io.so: no space"
                + " left on device\"},\"error\":\"failed to register layer: Error processing tar "
                + "file(exit status 1): write /usr/local/lib/python2.7/lib-dynload/_io.so: no"
                + " space left on device\"}";

        String expectedOutput = "{\"errorDetail\":{\"message\":\"failed to register layer: Error "
                + "processing tar file(exit status 1): write /usr/local/lib/python2.7/lib-dynload/_"
                + "io.so: no space left on device\"},\"error\":\"failed to register layer: "
                + "Error processing tar file(exit status 1): write /usr/local/lib/python2.7/"
                + "lib-dynload/_io.so: no space left on device\"}";

        String actualOutput = normalizeDockerError(sampleInput);

        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testNormalizeDockerErrorWithUnknownInput() {
        String sampleInput = "Sample string that shouldn't be modified.";
        String expectedOutput = sampleInput;
        String actualOutput = normalizeDockerError(sampleInput);
        assertEquals(expectedOutput, actualOutput);
    }

}
