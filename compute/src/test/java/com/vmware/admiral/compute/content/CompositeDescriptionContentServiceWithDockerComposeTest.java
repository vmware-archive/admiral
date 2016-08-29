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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;

import java.net.URI;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.UriUtils;

/**
 * Test the CompositeDescriptionContentService
 */
public class CompositeDescriptionContentServiceWithDockerComposeTest extends ComputeBaseTest {

    private String compose;
    private String template;

    @Before
    public void setUp() throws Throwable {
        compose = getComposeContent();
        template = getTemplateContent();
        waitForServiceAvailability(CompositeDescriptionContentService.SELF_LINK);
        waitForServiceAvailability(CompositeDescriptionService.SELF_LINK);
    }

    @Test
    public void testImportAsDockerComposeAndExportAsCompositeTemplate() throws Throwable {
        this.host.testStart(1);
        this.host.send(validateImportExportOperation(compose, false));
        this.host.testWait();
    }

    @Test
    public void testImportAsDockerComposeAndExportAsDockerCompose() throws Throwable {
        this.host.testStart(1);
        this.host.send(validateImportExportOperation(compose, true));
        this.host.testWait();
    }

    @Test
    public void testImportAsCompositeTemplateAndExportAsCompositeTemplate() throws Throwable {
        this.host.testStart(1);
        this.host.send(validateImportExportOperation(template, false));
        this.host.testWait();
    }

    @Test
    public void testImportAsCompositeTemplateAndExportAsDockerCompose() throws Throwable {
        this.host.testStart(1);
        this.host.send(validateImportExportOperation(template, true));
        this.host.testWait();
    }

    private Operation validateImportExportOperation(String yaml, boolean exportAsDocker) {
        // import YAML to Container Description
        return Operation.createPost(UriUtils.buildUri(host,
                CompositeDescriptionContentService.SELF_LINK))
                .setContentType(MEDIA_TYPE_APPLICATION_YAML)
                .setBody(yaml)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    } else {
                        String location = o.getResponseHeader(Operation.LOCATION_HEADER);

                        try {
                            assertEquals(Operation.STATUS_CODE_OK, o.getStatusCode());
                            assertNotNull(location);
                        } catch (Throwable t) {
                            host.failIteration(t);
                            return;
                        }

                        // export Container Description as Docker Compose or Composite Template
                        Operation export = createExportOperation(location, exportAsDocker);
                        // get Container Description raw
                        Operation get = createGetDescriptionOperation(location);

                        OperationJoin.create(export, get).setCompletion((os, es) -> {
                            if (es != null && !es.isEmpty()) {
                                host.failIteration(es.values().iterator().next());
                                return;
                            }

                            try {
                                Operation op1 = os.get(export.getId());
                                assertEquals(Operation.STATUS_CODE_OK, op1.getStatusCode());
                                String content = op1.getBody(String.class);
                                assertNotNull(content);
                                if (exportAsDocker) {
                                    // export result as Docker Compose
                                    validateDockerYaml(content);
                                } else {
                                    // export result as Composite Template
                                    validateCompositeYaml(content);
                                }

                                Operation op2 = os.get(get.getId());
                                assertEquals(Operation.STATUS_CODE_OK, op2.getStatusCode());
                                CompositeDescription description = op2
                                        .getBody(CompositeDescription.class);
                                validateCompositeDescription(description);
                            } catch (Throwable t) {
                                host.failIteration(t);
                                return;
                            }

                            host.completeIteration();
                        }).sendWith(host);
                    }
                });
    }

    private Operation createExportOperation(String selfLink, boolean docker) {
        URI uri = UriUtils.buildUri(host, CompositeDescriptionContentService.SELF_LINK);
        uri = UriUtils.extendUriWithQuery(uri,
                CompositeDescriptionContentService.SELF_LINK_PARAM_NAME, selfLink);
        if (docker) {
            uri = UriUtils.extendUriWithQuery(uri,
                    CompositeDescriptionContentService.FORMAT_PARAM_NAME,
                    CompositeDescriptionContentService.FORMAT_DOCKER_COMPOSE_TYPE);
        }
        return Operation.createGet(uri).setReferer(host.getReferer());
    }

    private Operation createGetDescriptionOperation(String selfLink) {
        return Operation.createGet(UriUtils.buildUri(host, selfLink)).setReferer(host.getReferer());
    }

    @Test
    public void testValidateBadRequestOnImport() throws Throwable {
        this.host.testStart(1);
        this.host.send(validateBadRequestOnImportOperation((String) null, "body is required"));
        this.host.testWait();

        this.host.testStart(1);
        this.host.send(validateBadRequestOnImportOperation("", "'yaml' cannot be empty"));
        this.host.testWait();

        this.host.testStart(1);
        this.host.send(validateBadRequestOnImportOperation("abc",
                "Error processing YAML content: Can not instantiate value of type [simple type, class com.vmware.admiral.compute.content.compose.CommonDescriptionEntity] from String value ('abc'); no single-String constructor/factory method"));
        this.host.testWait();

        this.host.testStart(1);
        this.host.send(validateBadRequestOnImportOperation(getContent("docker.redis.v1.yaml"),
                "Unknown YAML content type! Only Blueprint and Docker Compose v2 formats are supported."));
        this.host.testWait();

        this.host.testStart(1);
        this.host.send(validateBadRequestOnImportOperation(getContent("composite.2.5.yaml"),
                "Unsupported type 'Compute'!"));
        this.host.testWait();

        this.host.testStart(1);
        this.host.send(validateBadRequestOnImportOperation(getContent("composite.bad.yaml"),
                "Can not deserialize instance of java.lang.String out of START_OBJECT token"));
        this.host.testWait();

        this.host.testStart(1);
        this.host.send(validateBadRequestOnImportOperation(getContent("docker.bad.yaml"),
                "Error processing Docker Compose v2 YAML content: Can not instantiate value of type [simple type, class com.vmware.admiral.compute.content.compose.Logging] from String value (''); no single-String constructor/factory method"));
        this.host.testWait();

        this.host.testStart(1);
        this.host.send(validateBadRequestOnImportOperation(new Date(),
                "Failed to deserialize CompositeTemplate serialized content!"));
        this.host.testWait();
    }

    private Operation validateBadRequestOnImportOperation(Object body, String expectedMsg) {
        // import YAML to Container Description
        return Operation.createPost(UriUtils.buildUri(host,
                CompositeDescriptionContentService.SELF_LINK))
                .setContentType((body instanceof String) ? MEDIA_TYPE_APPLICATION_YAML
                        : Operation.MEDIA_TYPE_APPLICATION_JSON)
                .setBody(body)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (e instanceof IllegalArgumentException) {
                            try {
                                assertEquals(Operation.STATUS_CODE_BAD_REQUEST, o.getStatusCode());
                                assertTrue(e.getMessage().contains(expectedMsg));
                                assertTrue(o.getBody(ServiceErrorResponse.class).message
                                        .contains(expectedMsg));
                            } catch (Throwable t) {
                                host.failIteration(t);
                                return;
                            }
                            host.completeIteration();
                            return;
                        }
                        host.failIteration(e);
                    } else {
                        host.failIteration(new IllegalStateException("Test should have failed!"));
                    }
                });
    }

    private static String getComposeContent() {
        return getContent("docker.wordpress.1.yaml");
    }

    private static String getTemplateContent() {
        return getContent("composite.wordpress.yaml");
    }

    private static String getContent(String filename) {
        return FileUtil.getResourceAsString("/compose/" + filename, true);
    }

    private static void validateDockerYaml(String yaml) {
        assertNotNull(yaml);
        assertTrue(yaml.contains("version:"));
        assertTrue(yaml.contains("services:"));
        assertFalse(yaml.contains("name:"));
        assertFalse(yaml.contains("components:"));
        validateContent(yaml);
    }

    private static void validateCompositeYaml(String yaml) {
        assertNotNull(yaml);
        assertTrue(yaml.contains("name:"));
        assertTrue(yaml.contains("components:"));
        assertFalse(yaml.contains("version:"));
        assertFalse(yaml.contains("services:"));
        validateContent(yaml);
    }

    private static void validateContent(String yaml) {
        assertTrue(yaml.contains("db:"));
        assertTrue(yaml.contains("mysql:5.7"));
        assertTrue(yaml.contains("MYSQL_ROOT_PASSWORD"));
        assertTrue(yaml.contains("MYSQL_DATABASE"));
        assertTrue(yaml.contains("MYSQL_USER"));
        assertTrue(yaml.contains("MYSQL_PASSWORD"));
        assertTrue(yaml.contains("wordpress:"));
        assertTrue(yaml.contains("wordpress:latest"));
        assertTrue(yaml.contains("WORDPRESS_DB_HOST"));
        assertTrue(yaml.contains("WORDPRESS_DB_PASSWORD"));
    }

    private static void validateCompositeDescription(CompositeDescription description) {
        assertNotNull(description);
        assertNotNull(description.name);
        assertTrue(description.name.startsWith("Docker Compose"));
        assertNotNull(description.descriptionLinks);
        assertEquals(2, description.descriptionLinks.size());
        assertNull(description.tenantLinks); // default tenant / standalone mode
    }
}
