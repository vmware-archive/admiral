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
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.vmware.admiral.common.util.YamlMapper;

public class YamlMapperTest {

    private String sampleYamlDefinition = "---\n"
            + "apiVersion: v1\n"
            + "kind: Service\n"
            + "metadata:\n"
            + "  name: wordpress\n"
            + "  labels:\n"
            + "    app: wordpress\n"
            + "spec:\n"
            + "  ports:\n"
            + "  - port: 80\n"
            + "  selector:\n"
            + "    app: wordpress\n"
            + "    tier: frontend";

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

    @Test
    public void testIsMultiYaml() {
        String[] in = new String[] { sampleYamlDefinition,
                sampleYamlDefinition + "\n" + sampleYamlDefinition };

        boolean[] out = new boolean[] { false, true };

        assertEquals(out[0], YamlMapper.isMultiYaml(in[0]));
        assertEquals(out[1], YamlMapper.isMultiYaml(in[1]));
    }

    @Test
    public void testSplitYamlWithSingleYaml() {
        String yamlInput = sampleYamlDefinition;

        List<String> expectedOutput = new ArrayList<>();
        expectedOutput.add(sampleYamlDefinition);

        List<String> actualOutput = YamlMapper.splitYaml(yamlInput);

        assertEquals(1, actualOutput.size());

        assertEquals(expectedOutput.get(0), actualOutput.get(0));
    }

    @Test
    public void testSplitYamlWithMultipleYamls() {
        String yamlInput = sampleYamlDefinition + "\n" + sampleYamlDefinition;

        List<String> expectedOutput = new ArrayList<>();
        expectedOutput.add(sampleYamlDefinition);
        expectedOutput.add(sampleYamlDefinition);

        List<String> actualOutput = YamlMapper.splitYaml(yamlInput);

        assertEquals(2, actualOutput.size());

        assertEquals(expectedOutput.get(0), actualOutput.get(0));
        assertEquals(expectedOutput.get(1), actualOutput.get(1));
    }

}
