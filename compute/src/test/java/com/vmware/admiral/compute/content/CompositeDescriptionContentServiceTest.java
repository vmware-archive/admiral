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
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
@RunWith(Parameterized.class)
public class CompositeDescriptionContentServiceTest extends ComputeBaseTest {
    private final BiConsumer<Operation, List<String>> verifyTemplate;
    private String templateFileName;
    private String template;

    public static final String MEDIA_TYPE_APPLICATION_YAML_WITH_CHARSET = "application/yaml; charset=utf-8";

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "WordPress_with_MySQL_containers.yaml", verifyContainerTemplate },
                { "WordPress_with_MySQL_compute.yaml", verifyComputeTemplate }
        });

    }

    public CompositeDescriptionContentServiceTest(String templateFileName,
            BiConsumer<Operation, List<String>> verifier) {
        this.templateFileName = templateFileName;
        this.verifyTemplate = verifier;
    }

    private static BiConsumer<Operation, List<String>> verifyContainerTemplate = (o, descLinks) -> {
        CompositeDescription cd = o.getBody(CompositeDescription.class);
        assertEquals("name", "wordPressWithMySql", cd.name);
        assertEquals("descriptionLinks.size", 2, cd.descriptionLinks.size());
        assertNotNull("customProperties", cd.customProperties);
        assertEquals("customProperties.size", 1, cd.customProperties.size());
        assertEquals("customProperties[_leaseDays]", "3",
                cd.customProperties.get("_leaseDays"));

        descLinks.addAll(cd.descriptionLinks);
    };

    private static BiConsumer<Operation, List<String>> verifyComputeTemplate = (o, descLinks) -> {
        CompositeDescription cd = o.getBody(CompositeDescription.class);
        assertEquals("name", "wordPressWithMySqlCompute", cd.name);
        assertEquals("descriptionLinks.size", 2, cd.descriptionLinks.size());
        assertNotNull("customProperties", cd.customProperties);
        assertEquals("customProperties.size", 1, cd.customProperties.size());
        assertEquals("customProperties[_leaseDays]", "3",
                cd.customProperties.get("_leaseDays"));

        descLinks.addAll(cd.descriptionLinks);
    };

    @Before
    public void setUp() throws Throwable {
        this.template = CommonTestStateFactory.getFileContent(templateFileName);
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
                "Error processing YAML content: Can not construct instance of com.vmware.admiral.compute.content.compose.CommonDescriptionEntity: no String-argument constructor/factory method to deserialize from String value ('abc')"));
        this.host.testWait();

        this.host.testStart(1);
        this.host.send(validateBadRequestOnImportOperation(getContent("docker.redis.v1.yaml"),
                "Unknown YAML content type! Only Blueprint and Docker Compose v2 formats are supported."));
        this.host.testWait();

        this.host.testStart(1);
        this.host.send(validateBadRequestOnImportOperation(getContent("composite.bad.yaml"),
                "Can not deserialize instance of java.lang.String out of START_OBJECT token\n at [Source: N/A; line: -1, column: -1] (through reference chain: com.vmware.admiral.compute.container.ContainerDescriptionService$CompositeTemplateContainerDescription[\"logConfig\"]) (through reference chain: com.vmware.admiral.compute.content.CompositeTemplate[\"components\"]->java.util.LinkedHashMap[\"db\"])"));
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
                                System.out.println(expectedMsg);
                                System.out.println(e.getMessage());
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

        List<String> descriptionLinks = new ArrayList<>();

        Consumer<Operation> verifier = (o) -> verifyTemplate
                .accept(o, descriptionLinks);
        verifyOperation(getCompositeDescOp, verifier);

        verifyDescriptions(descriptionLinks);

        verifyExport(selfLink);
    }

    private void verifyDescriptions(List<String> containerDescriptionLinks) throws Throwable {
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
