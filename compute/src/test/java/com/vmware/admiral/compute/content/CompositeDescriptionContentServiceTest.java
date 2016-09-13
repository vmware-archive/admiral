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
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.UriUtils;

/**
 * Test the CompositeDescriptionContentService
 */
public class CompositeDescriptionContentServiceTest extends ComputeBaseTest {
    private String template;

    public static final String MEDIA_TYPE_APPLICATION_YAML_WITH_CHARSET = "application/yaml; charset=utf-8";

    @Before
    public void setUp() throws Throwable {
        this.template = CommonTestStateFactory.getFileContent("WordPress_with_MySQL.yaml");
        waitForServiceAvailability(CompositeDescriptionContentService.SELF_LINK);
    }

    @Test
    public void testCompositeDescriptionContentServices() throws Throwable {
        Operation createOp = Operation.createPost(UriUtils.buildUri(host,
                CompositeDescriptionContentService.SELF_LINK))
                .setContentType(MEDIA_TYPE_APPLICATION_YAML)
                .forceRemote()
                .setBody(template);

        AtomicReference<String> location = new AtomicReference<>();
        verifyOperation(createOp, (o) -> {
            assertEquals("status code", Operation.STATUS_CODE_OK, o.getStatusCode());

            location.set(o.getResponseHeader(Operation.LOCATION_HEADER));
            assertNotNull("location header", location);
        });

        verifyCompositeDescription(location.get());
    }

    @Test
    public void testCompositeDescriptionContentServicesWithCharset() throws Throwable {
        Operation createOp = Operation.createPost(UriUtils.buildUri(host,
                CompositeDescriptionContentService.SELF_LINK))
                .setContentType(MEDIA_TYPE_APPLICATION_YAML_WITH_CHARSET)
                .forceRemote()
                .setBody(template);

        AtomicReference<String> location = new AtomicReference<>();
        verifyOperation(createOp, (o) -> {
            assertEquals("status code", Operation.STATUS_CODE_OK, o.getStatusCode());

            location.set(o.getResponseHeader(Operation.LOCATION_HEADER));
            assertNotNull("location header", location);
        });

        verifyCompositeDescription(location.get());
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

    private static String getContent(String filename) {
        return FileUtil.getResourceAsString("/compose/" + filename, true);
    }

    private void verifyCompositeDescription(String location) throws Throwable {
        String selfLink = location.substring(location.lastIndexOf(UriUtils.URI_PATH_CHAR) + 1);
        assertNotNull("documentSelfLink", selfLink);

        Operation getCompositeDescOp = Operation.createGet(UriUtils.buildUri(host, location));

        List<String> containerDescriptionLinks = new ArrayList<>();
        verifyOperation(getCompositeDescOp, (o) -> {
            CompositeDescription cd = o.getBody(CompositeDescription.class);
            assertEquals("name", "wordPressWithMySql", cd.name);
            assertEquals("descriptionLinks.size", 2, cd.descriptionLinks.size());
            assertNotNull("customProperties", cd.customProperties);
            assertEquals("customProperties.size", 1, cd.customProperties.size());
            assertEquals("customProperties[_leaseDays]", "3",
                    cd.customProperties.get("_leaseDays"));

            containerDescriptionLinks.addAll(cd.descriptionLinks);
        });

        verifyContainerDescriptions(containerDescriptionLinks);

        verifyExport(selfLink);
    }

    private void verifyContainerDescriptions(List<String> containerDescriptionLinks)
            throws Throwable {
        for (String link : containerDescriptionLinks) {
            verifyOperation(Operation.createGet(UriUtils.buildUri(host, link)), (o) -> {
                ContainerDescription cd = o.getBody(ContainerDescription.class);
                assertTrue("unexpected name",
                        Arrays.asList("wordpress", "mysql").contains(cd.name));
            });
        }
    }

    private void verifyExport(String selfLink) throws Throwable {
        URI uri = UriUtils.buildUri(host, CompositeDescriptionContentService.SELF_LINK);
        uri = UriUtils.extendUriWithQuery(
                uri, CompositeDescriptionContentService.SELF_LINK_PARAM_NAME, selfLink);

        verifyOperation(Operation.createGet(uri), (o) -> {
            String resultYaml = o.getBody(String.class);
            assertEquals("YAML content differs",
                    toUnixLineEnding(template.trim()),
                    toUnixLineEnding(resultYaml.trim()));

        });
    }

    private static String toUnixLineEnding(String s) {
        if (s == null) {
            return null;
        }

        return s.replace("\r\n", "\n");
    }

}
