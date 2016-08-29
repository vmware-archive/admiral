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

package com.vmware.admiral.compute.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.admiral.compute.content.CompositeDescriptionContentService.TEMPLATE_CONTAINER_TYPE;

import java.net.URL;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

/**
 * Test CompositeTemplate mapping
 */
public class CompositeTemplateMapperTest {
    @Test
    public void testMapper() throws Exception {
        URL templateUrl = getClass().getClassLoader().getResource("WordPress_with_MySQL.yaml");
        ObjectMapper mapper = YamlMapper.objectMapper();

        CompositeTemplate bp = mapper.readValue(templateUrl, CompositeTemplate.class);
        assertEquals("id", null, bp.id);
        assertEquals("name", "wordPressWithMySql", bp.name);
        assertNotNull("properties", bp.properties);
        assertEquals("proerties.size", 1, bp.properties.size());

        assertNotNull("components", bp.components);
        assertEquals("components.size", 2, bp.components.size());

        ComponentTemplate<?> mysqlComponent = bp.components.get("mysql");
        assertNotNull("components[mysql]", mysqlComponent);
        assertEquals("components[mysql].type", TEMPLATE_CONTAINER_TYPE, mysqlComponent.type);
    }
}
