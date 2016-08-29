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

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import com.vmware.admiral.adapter.docker.util.CommandUtil;

/**
 * Test for CommandUtil spread method
 */
public class CommandUtilTest {

    @Test
    public void testSpreadCommand() throws Throwable {
        assertArrayEquals(null,
                CommandUtil.spread(null));
        assertArrayEquals(new String[] { "sh" },
                CommandUtil.spread(new String[] { "sh" }));
        assertArrayEquals(new String[] { "sh", "-c" },
                CommandUtil.spread(new String[] { "sh -c" }));
        assertArrayEquals(new String[] { "sh", "--verbose" },
                CommandUtil.spread(new String[] { "sh  --verbose" }));
        assertArrayEquals(new String[] { "./script.sh", "-name", "\"test\"" },
                CommandUtil.spread(new String[] { "./script.sh -name \"test\"" }));
        assertArrayEquals(new String[] { "./script.sh", "-name=\"test\"" },
                CommandUtil.spread(new String[] { "./script.sh -name=\"test\"" }));
        assertArrayEquals(new String[] { "./script.sh", "-name", "\"te st\"", "\\ foo" },
                CommandUtil.spread(new String[] { "./script.sh -name \"te st\" \\ foo"}));
        assertArrayEquals(new String[] { "./script.sh", "-name", "'te st'", "\\ foo" },
                CommandUtil.spread(new String[] { "./script.sh -name 'te st' \\ foo"}));
        assertArrayEquals(new String[] { "echo", "'test'" },
                CommandUtil.spread(new String[] { "echo 'test'"}));
        assertArrayEquals(new String[] { "sh", "-c", "echo", "\"test\"" },
                CommandUtil.spread(new String[] { "sh -c echo \"test\""}));
    }
}
