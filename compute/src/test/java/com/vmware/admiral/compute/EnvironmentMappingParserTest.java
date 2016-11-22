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

package com.vmware.admiral.compute;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.EnvironmentMappingService.EnvironmentMappingState;
import com.vmware.xenon.common.FileUtils;

public class EnvironmentMappingParserTest {

    @Test
    public void testParse() throws IllegalArgumentException, URISyntaxException, IOException {
        ObjectMapper mapper = YamlMapper.objectMapper();
        List<EnvironmentMappingState> mappings = FileUtils
                .findResources(EnvironmentMappingState.class, "mappings").stream()
                .filter(r -> r.url != null).map(r -> {
                    EnvironmentMappingState mappingState = null;
                    try (InputStream is = r.url.openStream()) {
                        mappingState = mapper.readValue(is,
                                EnvironmentMappingState.class);
                    } catch (Exception e) {
                        return null;
                    }
                    return mappingState;
                }).filter(obj -> obj != null).collect(Collectors.toList());
        assertNotNull(mappings);
        validateInstanceType(mappings);
    }

    @SuppressWarnings("unchecked")
    private void validateInstanceType(List<EnvironmentMappingState> mappings) {
        mappings.forEach(env -> {
            Object value = env.getMappingValue("instanceType", "small");
            if (value instanceof String) {
                System.out.println(String.valueOf(value));
            } else if (value instanceof Map) {
                Map<String, Integer> map = (Map<String, Integer>) value;
                Integer cpu = map.get("cpu");
                assertNotNull(cpu);
                Integer mem = map.get("mem");
                assertNotNull(mem);
                Integer disk = map.get("disk");
                assertNotNull(disk);

            } else {
                fail("Unsuported instance type definition");
            }
        });
    }
}
