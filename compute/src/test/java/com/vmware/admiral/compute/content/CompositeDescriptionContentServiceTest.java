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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

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
        CompositeDescriptionExpanded cd = o.getBody(CompositeDescriptionExpanded.class);
        assertEquals("name", "wordPressWithMySqlCompute", cd.name);
        assertEquals("descriptionLinks.size", 3, cd.descriptionLinks.size());
        assertNotNull("customProperties", cd.customProperties);
        assertEquals("customProperties.size", 1, cd.customProperties.size());
        assertEquals("customProperties[_leaseDays]", "3",
                cd.customProperties.get("_leaseDays"));

        //assert network was persisted
        Optional<ComponentDescription> networkComponentOpt = cd.componentDescriptions.stream()
                .filter(c -> c.type.equals(
                        ResourceType.COMPUTE_NETWORK_TYPE.getName())).findFirst();

        assertTrue(networkComponentOpt.isPresent());

        ComponentDescription networkComponent = networkComponentOpt.get();

        ComputeNetworkDescription networkDescription = Utils
                .fromJson(networkComponent.componentJson, ComputeNetworkDescription.class);

        ComponentDescription wordpress = cd.componentDescriptions.stream()
                .filter(c -> c.name.equals("wordpress")).findFirst().get();

        ComputeDescription wordpressComputeDescription = (ComputeDescription) wordpress
                .getServiceDocument();

        assertEquals(1, wordpressComputeDescription.networkInterfaceDescLinks.size());
        assertEquals(1, wordpressComputeDescription.tagLinks.size());

        assertEquals("public-wpnet", networkDescription.name);
        assertNull(networkDescription.assignment);

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
        this.host.send(validateBadRequestOnImportOperation("", "yaml cannot be empty"));
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
                "Can not deserialize instance of java.lang.String out of START_OBJECT token\n at [Source: N/A; line: -1, column: -1] (through reference chain: com.vmware.admiral.compute.container.ContainerDescriptionService$CompositeTemplateContainerDescription[\"logConfig\"])"));
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

        URI uri = UriUtils.buildUri(host, location);
        URI expandUri = UriUtils.extendUriWithQuery(uri, UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());

        Operation getCompositeDescOp = Operation.createGet(expandUri);

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
                ResourceState cd = o.getBody(ResourceState.class);
                assertTrue("unexpected name",
                        Arrays.asList("wordpress", "mysql", "public-wpnet", "wpnet")
                                .contains(cd.name));
            });
        }
    }

    private void verifyExport(String selfLink) throws Throwable {
        URI uri = UriUtils.buildUri(host, CompositeDescriptionContentService.SELF_LINK);
        uri = UriUtils.extendUriWithQuery(
                uri, CompositeDescriptionContentService.SELF_LINK_PARAM_NAME, selfLink);

        verifyOperation(Operation.createGet(uri), (o) -> {
            String resultYaml = o.getBody(String.class);

            //cant compare the strings as the order of the components may not be the same
            try {
                CompositeTemplate original = CompositeTemplateUtil
                        .deserializeCompositeTemplate(template);

                CompositeTemplate result = CompositeTemplateUtil
                        .deserializeCompositeTemplate(resultYaml);

                assertEquals(original.components.size(), result.components.size());
                assertEquals(original.id, result.id);
                assertEquals(original.description, result.description);
                assertEquals(original.name, result.name);
                assertEquals(original.status, result.status);
                assertEquals(original.properties, result.properties);
            } catch (IOException e) {
                fail(e.getMessage());
            }

        });
    }

}
