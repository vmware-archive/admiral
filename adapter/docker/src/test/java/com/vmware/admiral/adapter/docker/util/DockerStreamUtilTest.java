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
import static org.junit.Assert.fail;

import java.io.EOFException;

import org.junit.Test;

public class DockerStreamUtilTest {

    @Test
    public void testDecodeFullRawResponce() throws EOFException {
        byte[] raw = new byte[] { 1, 0, 0, 0, 0, 0, 0, 4, 98, 105, 110, 10, 1, 0, 0, 0, 0, 0, 0,
                125, 99, 111, 112, 121, 45, 99, 101, 114, 116, 105, 102, 105, 99, 97, 116, 101, 46,
                115, 104, 10, 100, 101, 118, 10, 101, 116, 99, 10, 103, 111, 10, 104, 111, 109,
                101, 10, 108, 105, 98, 10, 108, 105, 110, 117, 120, 114, 99, 10, 109, 101, 100,
                105, 97, 10, 109, 110, 116, 10, 112, 114, 111, 99, 10, 114, 111, 111, 116, 10, 114,
                117, 110, 10, 115, 98, 105, 110, 10, 115, 121, 115, 10, 116, 101, 114, 109, 105,
                110, 97, 108, 46, 112, 110, 103, 10, 116, 109, 112, 10, 117, 115, 114, 10, 118, 97,
                114, 10, 119, 104, 105, 116, 101, 45, 111, 110, 45, 98, 108, 97, 99, 107, 46, 99,
                115, 115, 10 };

        String decoded = DockerStreamUtil.decodeFullRawResponce(raw);

        String expected = "bin\n"
                + "copy-certificate.sh\n"
                + "dev\n"
                + "etc\n"
                + "go\n"
                + "home\n"
                + "lib\n"
                + "linuxrc\n"
                + "media\n"
                + "mnt\n"
                + "proc\n"
                + "root\n"
                + "run\n"
                + "sbin\n"
                + "sys\n"
                + "terminal.png\n"
                + "tmp\n"
                + "usr\n"
                + "var\n"
                + "white-on-black.css\n";

        assertEquals(expected, decoded);
    }

    @Test
    public void testDecodePartialResponse() throws EOFException {
        byte[] raw = new byte[] { 1, 0, 0, 0, 0, 0, 0, 4, 98, 105, 110, 10 };

        String decoded = DockerStreamUtil.decodeFullRawResponce(raw);

        String expected = "bin\n";
        assertEquals(expected, decoded);
    }

    @Test(expected = EOFException.class)
    public void testDecodeWithShorterFrame() throws EOFException {
        byte[] raw = new byte[] { 1, 0, 0, 0, 0, 0, 0, 4, 98, 105, 110 };

        DockerStreamUtil.decodeFullRawResponce(raw);
        fail("expected to throw exception");
    }

    @Test(expected = EOFException.class)
    public void testDecodeWithLongerFrame() throws EOFException {
        byte[] raw = new byte[] { 1, 0, 0, 0, 0, 0, 0, 4, 98, 105, 110, 10, 110 };

        DockerStreamUtil.decodeFullRawResponce(raw);
    }

}
