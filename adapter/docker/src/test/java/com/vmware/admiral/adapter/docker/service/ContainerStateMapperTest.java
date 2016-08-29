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

import java.time.Instant;

import org.junit.Test;

public class ContainerStateMapperTest {

    @Test
    public void testParseDate() {
        String iso8601WithMicroseconds = "2015-08-26T20:57:44.715343657Z";
        String iso8601WithMilliseconds = "2015-08-26T20:57:44.715Z";
        String vicDateTime = "2015-08-26 20:57:44 +0000 UTC";

        Long parsed = ContainerStateMapper.parseDate(iso8601WithMicroseconds);
        Instant fromEpochMilli = Instant.ofEpochMilli(parsed);

        // Assert that microseconds are ignored in the context of epoch millisecond
        assertEquals(iso8601WithMilliseconds, fromEpochMilli.toString());

        // test workaround datetime parser for VIC host
        Long vic = ContainerStateMapper.parseDate(vicDateTime);
        assertEquals(715, parsed - vic);
    }
}
