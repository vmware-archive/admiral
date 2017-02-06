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

package com.vmware.admiral.service.common.util;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import com.vmware.admiral.common.util.YamlMapper;

public class YamlMapperTest {

    @Test
    public void testConvertFromYamlToJson() throws IOException {
        String yamlInput = "---\n"
                + "person:\n"
                + "  name: test-name\n"
                + "  age: 14\n";

        String expectedJsonOutput = "{\"person\":{\"name\":\"test-name\",\"age\":14}}";

        String actualJsonOutput = YamlMapper.fromYamlToJson(yamlInput);

        assertEquals(expectedJsonOutput, actualJsonOutput);
    }

    @Test
    public void testConvertFromJsonToYaml() throws IOException {
        String jsonInput = "{\"person\":{\"name\":\"test-name\",\"age\":14}}";

        String expectedYamlOutput = "---\n"
                + "person:\n"
                + "  name: \"test-name\"\n"
                + "  age: 14\n";

        String actualYamlOutput = YamlMapper.fromJsonToYaml(jsonInput);

        assertEquals(expectedYamlOutput, actualYamlOutput);
    }
}
