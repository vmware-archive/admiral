/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.content;

import static com.vmware.admiral.compute.content.CompositeTemplateUtil.assertComponentTypes;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.deserializeCompositeTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.deserializeDockerCompose;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.fromCompositeTemplateToDockerCompose;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.fromDockerComposeToCompositeTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.getYamlType;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.isNullOrEmpty;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.serializeCompositeTemplate;
import static com.vmware.admiral.compute.content.CompositeTemplateUtil.serializeDockerCompose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription.Status;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.content.CompositeTemplateUtil.YamlType;
import com.vmware.admiral.compute.content.compose.DockerCompose;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.xenon.common.LocalizableValidationException;

public class CompositeTemplateUtilTest {

    @Before
    public void beforeForCompositeTemplateUtilTest() throws Throwable {
        HostInitComputeServicesConfig.initCompositeComponentRegistry();
    }

    @Test
    public void testConvertDockerComposeToCompositeTemplate() throws IOException {
        CompositeTemplate expectedTemplate = deserializeCompositeTemplate(
                getContent("composite.wordpress.yaml"));

        String expectedTemplateYaml = serializeCompositeTemplate(expectedTemplate);

        // Docker Compose with environment values as array

        DockerCompose compose1 = deserializeDockerCompose(
                getContent("docker.wordpress.1.yaml"));

        CompositeTemplate template1 = fromDockerComposeToCompositeTemplate(compose1);
        template1.name = expectedTemplate.name; // because of the timestamp

        assertComponentTypes(template1.components);

        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 2,
                template1.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 0,
                template1.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 0,
                template1.components);

        String template1Yaml = serializeCompositeTemplate(template1);

        assertEqualsYamls(expectedTemplateYaml, template1Yaml, true);

        // Docker Compose with environment values as dictionary

        DockerCompose compose2 = deserializeDockerCompose(
                getContent("docker.wordpress.2.yaml"));

        CompositeTemplate template2 = fromDockerComposeToCompositeTemplate(compose2);
        template2.name = expectedTemplate.name; // because of the timestamp

        assertComponentTypes(template2.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 2,
                template1.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 0,
                template1.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 0,
                template1.components);

        String template2Yaml = serializeCompositeTemplate(template2);

        assertEqualsYamls(expectedTemplateYaml, template2Yaml, true);
    }

    @Test
    public void testConvertDockerComposeToCompositeTemplateWithNetwork() throws IOException {

        // Docker Compose with simple network entities

        CompositeTemplate expectedTemplate = deserializeCompositeTemplate(
                getContent("composite.simple.network.yaml"));

        String expectedTemplateYaml = serializeCompositeTemplate(expectedTemplate);

        DockerCompose compose1 = deserializeDockerCompose(
                getContent("docker.simple.network.yaml"));

        CompositeTemplate template1 = fromDockerComposeToCompositeTemplate(compose1);
        template1.name = expectedTemplate.name; // because of the timestamp

        assertComponentTypes(template1.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 3,
                template1.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 2,
                template1.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 0,
                template1.components);

        String template1Yaml = serializeCompositeTemplate(template1);

        assertEqualsYamls(toUnixLineEnding(expectedTemplateYaml),
                toUnixLineEnding(getContent("composite.simple.network.expected2.yaml")), true);
        assertEqualsYamls(toUnixLineEnding(template1Yaml),
                toUnixLineEnding(getContent("composite.simple.network.yaml")), true);

        // Docker Compose with complex network entities

        expectedTemplate = deserializeCompositeTemplate(
                getContent("composite.complex.network.yaml"));

        expectedTemplateYaml = serializeCompositeTemplate(expectedTemplate);

        DockerCompose compose2 = deserializeDockerCompose(
                getContent("docker.complex.network.yaml"));

        CompositeTemplate template2 = fromDockerComposeToCompositeTemplate(compose2);
        template2.name = expectedTemplate.name; // because of the timestamp

        assertComponentTypes(template2.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 3,
                template2.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 3,
                template2.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 0,
                template2.components);

        String template2Yaml = serializeCompositeTemplate(template2);

        assertEqualsYamls(toUnixLineEnding(getContent("composite.simple.network.expected.yaml")),
                toUnixLineEnding(template2Yaml), true);
    }

    @Test
    public void testConvertDockerComposeToCompositeTemplateWithVolume() throws IOException {
        // Docker Compose with simple volume entities

        CompositeTemplate expectedTemplate = deserializeCompositeTemplate(
                getContent("composite.simple.volume.yaml"));

        String expectedTemplateYaml = serializeCompositeTemplate(expectedTemplate);

        DockerCompose compose1 = deserializeDockerCompose(
                getContent("docker.simple.volume.yaml"));

        CompositeTemplate template1 = fromDockerComposeToCompositeTemplate(compose1);
        template1.name = expectedTemplate.name; // because of the timestamp

        assertComponentTypes(template1.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 2,
                template1.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 0,
                template1.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 2,
                template1.components);

        String template1Yaml = serializeCompositeTemplate(template1);

        assertEqualsYamls(expectedTemplateYaml, template1Yaml, true);

        // Docker Compose with complex volume entities

        expectedTemplate = deserializeCompositeTemplate(
                getContent("composite.complex.volume.yaml"));

        expectedTemplateYaml = serializeCompositeTemplate(expectedTemplate);

        DockerCompose compose2 = deserializeDockerCompose(
                getContent("docker.complex.volume.yaml"));

        CompositeTemplate template2 = fromDockerComposeToCompositeTemplate(compose2);
        template2.name = expectedTemplate.name; // because of the timestamp

        assertComponentTypes(template2.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 3,
                template2.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 0,
                template2.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 3,
                template2.components);

        String template2Yaml = serializeCompositeTemplate(template2);

        assertEqualsYamls(expectedTemplateYaml, template2Yaml, true);
    }

    @Test
    public void testConvertCompositeTemplateToDockerCompose() throws IOException {

        DockerCompose expectedCompose = deserializeDockerCompose(
                getContent("docker.wordpress.1.yaml"));

        String expectedComposeYaml = serializeDockerCompose(expectedCompose);

        CompositeTemplate template = deserializeCompositeTemplate(
                getContent("composite.wordpress.yaml"));

        DockerCompose compose = fromCompositeTemplateToDockerCompose(template);

        String composeYaml = serializeDockerCompose(compose);

        assertEqualsYamls(expectedComposeYaml, composeYaml);
    }

    @Test
    public void testConvertCompositeTemplateToDockerComposeWithNetwork() throws IOException {

        // To Docker Compose with simple network entities

        DockerCompose expectedCompose = deserializeDockerCompose(
                getContent("docker.simple.network.yaml"));

        String expectedComposeYaml = serializeDockerCompose(expectedCompose);

        CompositeTemplate template = deserializeCompositeTemplate(
                getContent("composite.simple.network.yaml"));

        DockerCompose compose = fromCompositeTemplateToDockerCompose(template);

        String composeYaml = serializeDockerCompose(compose);

        assertEqualsYamls(toUnixLineEnding(expectedComposeYaml),
                toUnixLineEnding(getContent("docker.simple.network.yaml")));
        assertEqualsYamls(toUnixLineEnding(composeYaml),
                toUnixLineEnding(getContent("docker.simple.network.expected.yaml")));

        // To Docker Compose with complex network entities

        expectedCompose = deserializeDockerCompose(
                getContent("docker.complex.network.yaml"));

        expectedComposeYaml = serializeDockerCompose(expectedCompose);

        CompositeTemplate template2 = deserializeCompositeTemplate(
                getContent("composite.complex.network.yaml"));

        DockerCompose compose2 = fromCompositeTemplateToDockerCompose(template2);

        String compose2Yaml = serializeDockerCompose(compose2);

        assertEqualsYamls(expectedComposeYaml, compose2Yaml);
    }

    @Test
    public void testConvertCompositeTemplateToDockerComposeWithVolume() throws IOException {

        // To Docker Compose with simple volume entities

        DockerCompose expectedCompose = deserializeDockerCompose(
                getContent("docker.simple.volume.yaml"));

        String expectedComposeYaml = serializeDockerCompose(expectedCompose);

        CompositeTemplate template = deserializeCompositeTemplate(
                getContent("composite.simple.volume.yaml"));

        DockerCompose compose = fromCompositeTemplateToDockerCompose(template);

        String composeYaml = serializeDockerCompose(compose);

        assertEqualsYamls(expectedComposeYaml, composeYaml);

        // To Docker Compose with complex network entities

        expectedCompose = deserializeDockerCompose(
                getContent("docker.complex.volume.yaml"));

        expectedComposeYaml = serializeDockerCompose(expectedCompose);

        CompositeTemplate template2 = deserializeCompositeTemplate(
                getContent("composite.complex.volume.yaml"));

        DockerCompose compose2 = fromCompositeTemplateToDockerCompose(template2);

        String compose2Yaml = serializeDockerCompose(compose2);

        assertEqualsYamls(expectedComposeYaml, compose2Yaml);
    }

    public static void assertEqualsYamls(String expected, String actual) throws IOException {
        assertEqualsYamls(expected, actual, false);
    }

    @SuppressWarnings("rawtypes")
    public static void assertEqualsYamls(String expected, String actual, boolean ignoreEmptyValues) throws IOException {
        Map expectedMap = YamlMapper.objectMapper().readValue(expected, Map.class);
        Map actualMap = YamlMapper.objectMapper().readValue(actual, Map.class);

        if (ignoreEmptyValues) {
            removeEmptyValues(expectedMap);
            removeEmptyValues(actualMap);
        }
        Logger.getLogger(CompositeTemplateUtilTest.class.getName()).log(Level.INFO,
                "ignoreEmptyValues: " + ignoreEmptyValues +
                      " \nassertEqualsYamls : expected=<" + YamlMapper.objectMapper().writeValueAsString(expectedMap) +
                      "> and actual=<" + YamlMapper.objectMapper().writeValueAsString(actualMap) + ">");
        assertEquals(expectedMap, actualMap);
    }

    private static void removeEmptyValues(Map<String, Object> map) {
        for (String key : map.keySet()) {
            Object value = map.get(key);
            if (value instanceof Map) {
                removeEmptyValues((Map) value);
            }
            if (value instanceof Collection && ((Collection) value).isEmpty()) {
                map.remove(key);
            }
        }
    }

    @Test
    public void testWrongDeserializationExceptions() throws IOException {
        try {
            deserializeCompositeTemplate(getContent("composite.bad.yaml"));
            fail("wrong content!");
        } catch (IllegalArgumentException e) {
            assertTrue(
                    e.getMessage().startsWith(
                            "Cannot deserialize instance of `java.lang.String` out of START_OBJECT token"));
        }

        try {
            deserializeDockerCompose(getContent("composite.wordpress.yaml"));
            fail("wrong content!");
        } catch (LocalizableValidationException e) {
            // Docker Compose YAMLs have no top-level property 'name'
            assertTrue(
                    e.getMessage().startsWith("Error processing Docker Compose v2 YAML content:"));
        }

        try {
            deserializeDockerCompose(getContent("docker.redis.v1.yaml"));
            fail("wrong content!");
        } catch (LocalizableValidationException e) {
            // Docker Compose YAMLs is not version 2
            assertTrue(
                    e.getMessage().startsWith("Error processing Docker Compose v2 YAML content:"));
        }

        try {
            deserializeCompositeTemplate("");
            fail("wrong content!");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("cannot be empty"));
        }

        try {
            deserializeCompositeTemplate(null);
            fail("wrong content!");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("cannot be empty"));
        }

        try {
            deserializeCompositeTemplate("abc");
            fail("wrong content!");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().startsWith("Error processing Blueprint YAML content:"));
        }

        try {
            deserializeDockerCompose("");
            fail("wrong content!");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("cannot be empty"));
        }

        try {
            deserializeDockerCompose(null);
            fail("wrong content!");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("cannot be empty"));
        }

        try {
            deserializeDockerCompose("abc");
            fail("wrong content!");
        } catch (LocalizableValidationException e) {
            assertTrue(
                    e.getMessage().startsWith("Error processing Docker Compose v2 YAML content:"));
        }
    }

    @Test
    public void testSerializeDeserializeEmptyTemplate() throws IOException {
        CompositeTemplate template = new CompositeTemplate();
        template.name = "empty";
        String templateSerialized = serializeCompositeTemplate(template);
        assertNotNull(templateSerialized);
        assertTrue(templateSerialized.contains("empty"));

        CompositeTemplate templateDeserialized = deserializeCompositeTemplate(templateSerialized);
        assertNotNull(templateDeserialized);
        assertEquals(template.name, templateDeserialized.name);
        assertTrue(isNullOrEmpty(template.components));
        assertTrue(isNullOrEmpty(templateDeserialized.components));
    }

    @Test
    public void testWrongSerializationExceptions() throws IOException {
        try {
            serializeCompositeTemplate(null);
            fail("wrong content!");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("is required"));
        }

        try {
            serializeDockerCompose(null);
            fail("wrong content!");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("is required"));
        }
    }

    @Test
    public void testConvertSmallDockerCompose() throws IOException {

        // YAML with 'command' as string

        DockerCompose compose1 = deserializeDockerCompose(getContent("docker.django.yaml"));

        CompositeTemplate template1 = fromDockerComposeToCompositeTemplate(compose1);

        String template1Yaml = serializeCompositeTemplate(template1);

        assertTrue((template1Yaml != null) && (!template1Yaml.isEmpty()));

        // YAML with 'command' as list

        DockerCompose compose2 = deserializeDockerCompose(getContent("docker.rails.yaml"));

        CompositeTemplate template2 = fromDockerComposeToCompositeTemplate(compose2);

        String template2Yaml = serializeCompositeTemplate(template2);

        assertTrue((template2Yaml != null) && (!template2Yaml.isEmpty()));
    }

    @Test
    public void testGetYamlType() throws IOException {
        assertEquals(YamlType.DOCKER_COMPOSE, getYamlType(getContent("docker.wordpress.1.yaml")));
        assertEquals(YamlType.COMPOSITE_TEMPLATE,
                getYamlType(getContent("composite.wordpress.yaml")));

        assertEquals(YamlType.UNKNOWN, getYamlType(getContent("docker.redis.v1.yaml")));

        try {
            getYamlType(getContent("../docker-host-private-key.PEM"));
            fail("With invalid content should fail!");
        } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().startsWith("Provided yaml is invalid"));
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().startsWith("Error processing YAML content:"));
        }
    }

    @Test
    public void testDeserializeSerializeComplexCompositeTemplate() throws IOException {

        String expectedContent = getContent("composite.complex.yaml");

        CompositeTemplate template = deserializeCompositeTemplate(expectedContent);

        assertNull(template.id);
        assertNull(template.status);
        assertComponentTypes(template.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 5,
                template.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 0,
                template.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 0,
                template.components);

        String content = serializeCompositeTemplate(template);

        expectedContent = expectedContent.replace("h5-name", "h5");

        assertEqualsYamls(toUnixLineEnding(expectedContent), toUnixLineEnding(content), true);

        DockerCompose compose = fromCompositeTemplateToDockerCompose(template);

        String contentCompose = serializeDockerCompose(compose);

        assertTrue((contentCompose != null) && (!contentCompose.isEmpty()));
    }

    @Test
    public void testSeriaizeDeserializeCompositeTemplateWithHealthCheck() throws IOException {

        // Assert that healthConfig.ignoreOnProvision is serialized when false
        String expectedContent = getContent("composite.simple.health.yaml");

        CompositeTemplate template = deserializeCompositeTemplate(expectedContent);

        ContainerDescription data = (ContainerDescription) template.components.get("hello").data;
        assertNotNull(data.healthConfig);
        assertEquals(false, data.healthConfig.ignoreOnProvision);

        // Assert that healthConfig.ignoreOnProvision is serialized when true
        data.healthConfig.ignoreOnProvision = true;
        String content = serializeCompositeTemplate(template);

        template = deserializeCompositeTemplate(content);

        data = (ContainerDescription) template.components.get("hello").data;
        assertNotNull(data.healthConfig);
        assertEquals(true, data.healthConfig.ignoreOnProvision);

        // Assert that healthConfig.ignoreOnProvision is not serialized when null
        data.healthConfig = null;
        content = serializeCompositeTemplate(template);
        assertFalse(content.contains("health_config"));
    }

    @Test
    public void testDeserializeSerializeComplexCompositeTemplateWithNetwork() throws IOException {

        String expectedContent = getContent("composite.complex.network.yaml");

        CompositeTemplate template = deserializeCompositeTemplate(expectedContent);

        assertNull(template.id);
        assertNull(template.status);
        assertComponentTypes(template.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 3,
                template.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 3,
                template.components);

        String content = serializeCompositeTemplate(template);

        expectedContent = expectedContent.replace("h5-name", "h5");

        assertEqualsYamls(toUnixLineEnding(getContent("composite.complex.network.expected.yaml")),
                toUnixLineEnding(content), true);

        DockerCompose compose = fromCompositeTemplateToDockerCompose(template);

        String contentCompose = serializeDockerCompose(compose);

        assertTrue((contentCompose != null) && (!contentCompose.isEmpty()));
    }

    @Test
    public void testDeserializeSerializeComplexCompositeTemplateWithVolume() throws IOException {

        String expectedContent = getContent("composite.complex.volume.yaml");

        CompositeTemplate template = deserializeCompositeTemplate(expectedContent);

        assertNull(template.id);
        assertNull(template.status);
        assertComponentTypes(template.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 3,
                template.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 0,
                template.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 3,
                template.components);

        String content = serializeCompositeTemplate(template);

        assertEqualsYamls(toUnixLineEnding(expectedContent), toUnixLineEnding(content), true);

        DockerCompose compose = fromCompositeTemplateToDockerCompose(template);

        String contentCompose = serializeDockerCompose(compose);

        assertTrue((contentCompose != null) && (!contentCompose.isEmpty()));
    }

    @Test
    public void testDeserializeSerializeComplexDockerCompose() throws IOException {

        String expectedContent = getContent("docker.complex.yaml");

        DockerCompose compose = deserializeDockerCompose(expectedContent);

        String content = serializeDockerCompose(compose);

        assertEqualsYamls(toUnixLineEnding(expectedContent), toUnixLineEnding(content));

        CompositeTemplate template = fromDockerComposeToCompositeTemplate(compose);

        assertNull(template.id);
        assertNull(template.status);
        assertComponentTypes(template.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 4,
                template.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 0,
                template.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 0,
                template.components);

        String contentTemplate = serializeCompositeTemplate(template);

        assertTrue((contentTemplate != null) && (!contentTemplate.isEmpty()));
    }

    @Test
    public void testDeserializeSerializeSimpleDockerComposeWithNetwork() throws IOException {

        String expectedContent = getContent("docker.simple.network.yaml");

        DockerCompose compose = deserializeDockerCompose(expectedContent);

        String content = serializeDockerCompose(compose);

        assertEquals(toUnixLineEnding(expectedContent), toUnixLineEnding(content));

        CompositeTemplate template = fromDockerComposeToCompositeTemplate(compose);

        assertNull(template.id);
        assertNull(template.status);
        assertComponentTypes(template.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 3,
                template.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 2,
                template.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 0,
                template.components);

        String contentTemplate = serializeCompositeTemplate(template);

        assertTrue((contentTemplate != null) && (!contentTemplate.isEmpty()));
    }

    @Test
    public void testDeserializeSerializeSimpleDockerComposeWithVolume() throws IOException {

        String expectedContent = getContent("docker.simple.volume.yaml");

        DockerCompose compose = deserializeDockerCompose(expectedContent);

        String content = serializeDockerCompose(compose);

        assertEqualsYamls(toUnixLineEnding(expectedContent), toUnixLineEnding(content));

        CompositeTemplate template = fromDockerComposeToCompositeTemplate(compose);

        assertNull(template.id);
        assertNull(template.status);
        assertComponentTypes(template.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 2,
                template.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 0,
                template.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 2,
                template.components);

        String contentTemplate = serializeCompositeTemplate(template);

        assertTrue((contentTemplate != null) && (!contentTemplate.isEmpty()));
    }

    @Test
    public void testDeserializeSerializeComplexDockerComposeWithNetwork() throws IOException {

        String expectedContent = getContent("docker.complex.network.yaml");

        DockerCompose compose = deserializeDockerCompose(expectedContent);

        String content = serializeDockerCompose(compose);

        assertEqualsYamls(toUnixLineEnding(expectedContent), toUnixLineEnding(content));

        CompositeTemplate template = fromDockerComposeToCompositeTemplate(compose);

        assertNull(template.id);
        assertNull(template.status);
        assertComponentTypes(template.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 3,
                template.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 3,
                template.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 0,
                template.components);

        String contentTemplate = serializeCompositeTemplate(template);

        assertTrue((contentTemplate != null) && (!contentTemplate.isEmpty()));
    }

    @Test
    public void testDeserializeSerializeComplexDockerComposeWithVolume() throws IOException {

        String expectedContent = getContent("docker.complex.volume.yaml");

        DockerCompose compose = deserializeDockerCompose(expectedContent);

        String content = serializeDockerCompose(compose);

        assertEqualsYamls(toUnixLineEnding(expectedContent), toUnixLineEnding(content));

        CompositeTemplate template = fromDockerComposeToCompositeTemplate(compose);

        assertNull(template.id);
        assertNull(template.status);
        assertComponentTypes(template.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 3,
                template.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 0,
                template.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 3,
                template.components);

        String contentTemplate = serializeCompositeTemplate(template);

        assertTrue((contentTemplate != null) && (!contentTemplate.isEmpty()));
    }

    @Test
    public void testDeserializeSerializeSimpleCompositeTemplate() throws IOException {

        String expectedContent = getContent("composite.simple.yaml");

        CompositeTemplate template = deserializeCompositeTemplate(expectedContent);

        assertNull(template.id);
        assertNull(template.status);
        assertComponentTypes(template.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 1,
                template.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 0,
                template.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 0,
                template.components);

        template.id = "new-id";
        template.status = Status.RETIRED;

        ContainerDescription helloData = (ContainerDescription) template.components
                .get("hello").data;
        helloData.healthConfig = new HealthConfig();
        helloData.logConfig = new LogConfig();

        String content = serializeCompositeTemplate(template);

        assertNotNull(content);
        assertFalse(content.contains("new-id"));
        assertFalse(content.contains(Status.RETIRED.toString()));
        assertFalse(content.contains("health_config"));
        assertFalse(content.contains("log_config"));
    }

    @Test
    public void testDeserializeSerializeSimpleCompositeTemplateWithNetwork() throws IOException {

        String expectedContent = getContent("composite.simple.network.yaml");

        CompositeTemplate template = deserializeCompositeTemplate(expectedContent);

        assertNull(template.id);
        assertNull(template.status);
        assertComponentTypes(template.components);
        assertContainersComponents(ResourceType.CONTAINER_TYPE.getContentType(), 3,
                template.components);
        assertContainersComponents(ResourceType.NETWORK_TYPE.getContentType(), 2,
                template.components);
        assertContainersComponents(ResourceType.VOLUME_TYPE.getContentType(), 0,
                template.components);

        ContainerDescription appData = (ContainerDescription) template.components.get("app").data;

        assertNotNull(appData);
        assertTrue(appData.networks.containsKey("front"));
        assertTrue(appData.networks.containsKey("back"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDeserializeCompositeTemplateWithBindings() throws IOException {
        String expectedContent = getContent("composite.bindings.yaml");

        CompositeTemplate compositeTemplate = deserializeCompositeTemplate(expectedContent);

        ContainerDescription wpData = (ContainerDescription) compositeTemplate.components
                .get("wordpress").data;
        assertEquals(null, wpData._cluster);
        assertFalse(wpData.customProperties.containsKey("mysql_user"));

        assertEquals(1, compositeTemplate.bindings.size());

        List<Binding> bindings = compositeTemplate.bindings.iterator().next().bindings;
        assertEquals(3, bindings.size());

        Map<Boolean, List<Binding>> partitionedBindings = bindings.stream()
                .collect(Collectors.partitioningBy(b -> b.isProvisioningTimeBinding()));

        assertEquals(1, partitionedBindings.get(false).size());

        Binding binding = partitionedBindings.get(false).get(0);
        assertFalse(binding.isProvisioningTimeBinding());
        assertEquals("db~_cluster", binding.placeholder.bindingExpression);

        // provisioning time bindings
        assertEquals(2, partitionedBindings.get(true).size());

        binding = partitionedBindings.get(true).get(0);
        assertTrue(binding.isProvisioningTimeBinding());
        assertEquals("_resource~db~address", binding.placeholder.bindingExpression);

        binding = partitionedBindings.get(true).get(1);
        assertTrue(binding.isProvisioningTimeBinding());
        assertEquals("_resource~db~env~MYSQL_USER", binding.placeholder.bindingExpression);

        // validate "normalizeBindings"

        String expectedContentSerialized = serializeCompositeTemplate(compositeTemplate);

        assertFalse(expectedContent.contains("bindings:"));
        assertFalse(expectedContentSerialized.contains("bindings:"));

        assertTrue(expectedContent.contains("${db~_cluster}"));
        assertTrue(expectedContentSerialized.contains("${db~_cluster}"));

        assertTrue(expectedContent.contains("${_resource~db~env~MYSQL_USER}"));
        assertTrue(expectedContentSerialized.contains("${_resource~db~env~MYSQL_USER}"));

        assertTrue(expectedContent.contains("${_resource~db~address}:3306"));
        assertTrue(expectedContentSerialized.contains("${_resource~db~address}:3306"));

        Map<String, Object> originalMap = YamlMapper.objectMapper().readValue(
                expectedContent.trim(), Map.class);
        Map<String, Object> serializedMap = YamlMapper.objectMapper().readValue(
                expectedContentSerialized.trim(), Map.class);
        assertEquals(originalMap.get("components"), serializedMap.get("components"));
    }

    @Test
    public void testGetYamlTypeWithValidInputs() throws IOException {
        String compositeTemplate = getContent("composite.simple.yaml");
        String dockerCompose = getContent("docker.simple.network.yaml");
        String kubernetesTemplate = getContent("kubernetes.wordpress.deployment.yaml");
        String multiKubernetesTemplate = kubernetesTemplate + "\n" + kubernetesTemplate;

        String[] input = new String[] {
                compositeTemplate, dockerCompose, kubernetesTemplate, multiKubernetesTemplate };

        YamlType[] expectedTypes = new YamlType[] {
                YamlType.COMPOSITE_TEMPLATE,
                YamlType.DOCKER_COMPOSE,
                YamlType.KUBERNETES_TEMPLATE,
                YamlType.KUBERNETES_TEMPLATE
        };

        for (int i = 0; i < input.length; i++) {
            YamlType actualType = getYamlType(input[i]);
            assertEquals(expectedTypes[i], actualType);
        }
    }

    /**
     * The test should verify the case where we try to get the YAML type of
     * multi yaml docker compose or composite template.
     */
    @Test
    public void testGetYamlTypeWithMultiYamlsShouldFail() throws IOException {
        String compositeTemplate = getContent("composite.simple.yaml");
        String dockerCompose = getContent("docker.simple.network.yaml");
        String[] multiYamls = new String[] {
                compositeTemplate + "\n" + compositeTemplate,
                dockerCompose + "\n" + dockerCompose };
        String expectedFailMsg = "Multiple YAML definitions are not supported "
                + "for Docker Compose and YAML Blueprint.";

        for (String multiYaml : multiYamls) {
            try {
                getYamlType(multiYaml);
            } catch (Throwable ex) {
                assertEquals(expectedFailMsg, ex.getMessage());
            }
        }
    }

    public static String getContent(String filename) {
        return FileUtil.getResourceAsString("/compose/" + filename, true);
    }

    private static String toUnixLineEnding(String s) {
        if (s == null) {
            return null;
        }

        return s.replace("\r\n", "\n");
    }

    public static void assertContainersComponents(String type, int expected,
            Map<String, ComponentTemplate<?>> components) {
        assertNotNull(components);

        int actual = 0;

        for (ComponentTemplate<?> component : components.values()) {
            if (type.equals(component.type)) {
                actual++;
            }
        }

        assertEquals("Expected " + expected + " elements of type '" + type + "', but found "
                + actual + "!", expected, actual);
    }

}
