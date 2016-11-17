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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;

/**
 * Test mapping function
 */
public class ContainerTemplateMapperTest {
    @Test
    public void testMapping() throws Exception {
        ObjectMapper mapper = YamlMapper.objectMapper();

        ContainerDescription cd = mapper.readValue("name: my_cont\n"
                + "ports:\n- host_port: 8000\n  container_port: 80\n"
                + "env:\n- var: somevar\n  value: somevalue\n"
                + "- var: DB_PASSWORD\n  value: pass@word01\n"
                + "links:\n- service: mysql\n  alias: DB\n"
                + "custom_prop_name: custom_prop_value\n",
                ContainerDescription.class);

        assertNotNull("ContainerDescription", cd);

        // verify simple string without any special mapping
        assertEquals("name", "my_cont", cd.name);

        // verify port binding
        assertNotNull("portBindings", cd.portBindings);
        assertEquals("portBindings.length", 1, cd.portBindings.length);
        assertEquals("portBindings[0].hostPort", "8000", cd.portBindings[0].hostPort);
        assertEquals("portBindings[0].containerPort", "80", cd.portBindings[0].containerPort);

        // verify env mapping
        assertNotNull("env", cd.env);
        assertEquals("env.length", 2, cd.env.length);
        assertEquals("env[0]", "somevar=somevalue", cd.env[0]);
        assertEquals("env[1]", "DB_PASSWORD=pass@word01", cd.env[1]);

        // verify service links mapping
        assertNotNull("serviceLinks", cd.links);
        assertEquals("serviceLinks.length", 1, cd.links.length);
        assertEquals("serviceLinks[0]", "mysql:DB", cd.links[0]);

        // verify custom properties
        assertNotNull("customProperties", cd.customProperties);
        assertEquals("customProperties.size", 1, cd.customProperties.size());
        assertEquals("customProperties[custom_prop_name]", "custom_prop_value",
                cd.customProperties.get("custom_prop_name"));
    }
}
