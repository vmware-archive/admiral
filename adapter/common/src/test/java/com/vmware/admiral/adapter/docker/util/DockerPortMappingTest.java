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

import com.vmware.admiral.adapter.docker.util.DockerPortMapping.Protocol;

/**
 * Test for DockerPortMapping parsing methods
 */
@RunWith(Parameterized.class)
public class DockerPortMappingTest {
    private final String description;
    private final String fullPortString;
    private final String expectedHostIp;
    private final String expectedHostPort;
    private final String expectedContainerPort;
    private final Protocol expectedProtocol;

    @Parameters
    public static List<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "all sections", "0.0.0.0:450:230/tcp", "0.0.0.0", "450", "230", Protocol.TCP },
                { "all sections, udp", "0.0.0.0:450:230/udp", "0.0.0.0", "450", "230",
                        Protocol.UDP },
                { "just container port", "230", null, null, "230", Protocol.TCP },
                { "host port and container port", "450:230", null, "450", "230", Protocol.TCP },
                { "host IP and container port", "0.0.0.0::230", "0.0.0.0", null, "230",
                        Protocol.TCP }
        });
    }

    /**
     * @param description
     * @param fullPortString
     * @param expectedHostIp
     * @param expectedHostPort
     * @param expectedContainerPort
     * @param expectedProtocol
     */
    public DockerPortMappingTest(String description, String fullPortString, String expectedHostIp,
            String expectedHostPort, String expectedContainerPort, Protocol expectedProtocol) {

        this.description = description;
        this.fullPortString = fullPortString;
        this.expectedHostIp = expectedHostIp;
        this.expectedHostPort = expectedHostPort;
        this.expectedContainerPort = expectedContainerPort;
        this.expectedProtocol = expectedProtocol;
    }

    @Test
    public void testDockerPortStringParsing() {
        DockerPortMapping portMapping = DockerPortMapping.fromString(fullPortString);
        assertEquals(description + ": hostIp", expectedHostIp, portMapping.getHostIp());
        assertEquals(description + ": hostPort", expectedHostPort, portMapping.getHostPort());
        assertEquals(description + ": containerPort", expectedContainerPort,
                portMapping.getContainerPort());

        assertEquals(description + ": protocol", expectedProtocol, portMapping.getProtocol());
    }

}
