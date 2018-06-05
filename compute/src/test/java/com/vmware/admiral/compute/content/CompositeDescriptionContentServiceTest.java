/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerDescriptionService.ContainerLoadBalancerDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.content.Binding.ComponentBinding;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Test the CompositeDescriptionContentService
 */
@RunWith(Enclosed.class)
public class CompositeDescriptionContentServiceTest extends ComputeBaseTest {
    public static final String MEDIA_TYPE_APPLICATION_YAML_WITH_CHARSET = "application/yaml; charset=utf-8";

    @RunWith(Parameterized.class)
    public static class CompositeDescriptionContentServiceParameterized extends ComputeBaseTest {
        @Parameterized.Parameters
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    { "WordPress_with_MySQL_containers.yaml", verifyContainerTemplate },
                    { "WordPress_with_MySQL_bindings.yaml", verifyBindingsTemplate },
                    { "WordPress_with_MySQL_with_container_load_balancer.yaml", verifyContainerLoadBalancerTemplate },
                    { "WordPress_with_MySQL_kubernetes.yaml", verifyKubernetesTemplate }
            });
        }

        private static BiConsumer<Operation, List<String>> verifyContainerTemplate = (o, descLinks) -> {
            CompositeDescription cd = o.getBody(CompositeDescription.class);
            assertEquals("name", "wordPressWithMySql", cd.name);
            assertEquals("descriptionLinks.size", 2, cd.descriptionLinks.size());
            assertNotNull("customProperties", cd.customProperties);
            assertEquals("customProperties.size", 1, cd.customProperties.size());
            assertEquals("customProperties[_leaseDays]", "3", cd.customProperties.get("_leaseDays"));

            descLinks.addAll(cd.descriptionLinks);

            CompositeDescriptionExpanded cdExpanded = o.getBody(CompositeDescriptionExpanded.class);
            for (ComponentDescription component : cdExpanded.componentDescriptions) {
                ContainerDescription containerDescription = Utils.fromJson(component.componentJson, ContainerDescription.class);
                assertNotNull(containerDescription.image);
                assertNotNull(containerDescription.imageReference);
            }
        };
        private static BiConsumer<Operation, List<String>> verifyBindingsTemplate = (o, descLinks) -> {
            CompositeDescription cd = o.getBody(CompositeDescription.class);

            // verify bindings are present in the CompositeDescription
            assertNotNull(cd.bindings);
            assertEquals(1, cd.bindings.size());
            ComponentBinding componentBinding = cd.bindings.get(0);
            assertEquals("wordpress", componentBinding.componentName);
            assertNotNull(componentBinding.bindings);
            assertEquals(2, componentBinding.bindings.size());
            assertTrue(hasBindingExpression(componentBinding.bindings, "${mysql~restart_policy}"));
            assertTrue(hasBindingExpression(componentBinding.bindings, "${_resource~mysql~address}:3306"));

            descLinks.addAll(cd.descriptionLinks);
        };
        private static BiConsumer<Operation, List<String>> verifyContainerLoadBalancerTemplate = (o, descLinks) -> {
            CompositeDescriptionExpanded cd = o.getBody(CompositeDescriptionExpanded.class);
            assertEquals("name", "wordPressWithMySqlContainerLoadBalancer", cd.name);
            assertEquals("descriptionLinks.size", 4, cd.descriptionLinks.size());

            ContainerLoadBalancerDescription containerLoadBalancerDescription = (ContainerLoadBalancerDescription) cd.componentDescriptions
                    .stream().filter(c -> c.type.equals(ResourceType.CONTAINER_LOAD_BALANCER_TYPE.getName())).findFirst().get().getServiceDocument();

            assertNotNull(containerLoadBalancerDescription.dependsOn);
            assertNotNull(containerLoadBalancerDescription.portBindings);
            assertNotNull(
                    containerLoadBalancerDescription.frontends.stream().filter(health -> health.healthConfig != null).findAny().get());
            assertFalse(containerLoadBalancerDescription.networks.isEmpty());
            assertFalse(containerLoadBalancerDescription.frontends.isEmpty());
            assertFalse(containerLoadBalancerDescription.frontends.get(0).backends.isEmpty());
            assertEquals(containerLoadBalancerDescription.networks.get(0).name, "wpnet");

            descLinks.addAll(cd.descriptionLinks);
        };
        private static BiConsumer<Operation, List<String>> verifyKubernetesTemplate = (o, descLinks) -> {
            CompositeDescription cd = o.getBody(CompositeDescription.class);
            assertEquals("descriptionLinks.size", 6, cd.descriptionLinks.size());

            descLinks.addAll(cd.descriptionLinks);
        };
        private final BiConsumer<Operation, List<String>> verifyTemplate;
        private String templateFileName;
        private String template;

        public CompositeDescriptionContentServiceParameterized(String templateFileName, BiConsumer<Operation, List<String>> verifier) {
            this.templateFileName = templateFileName;
            this.verifyTemplate = verifier;
        }

        private static boolean hasBindingExpression(List<Binding> bindings, String expression) {
            for (Binding binding : bindings) {
                if (expression.equals(binding.originalFieldExpression)) {
                    return true;
                }
            }
            return false;
        }

        @Before
        public void setUp() throws Throwable {
            this.template = CommonTestStateFactory.getFileContent(templateFileName);
            waitForServiceAvailability(CompositeDescriptionContentService.SELF_LINK);
        }

        @Test
        public void testCompositeDescriptionContentServices() throws Throwable {
            Operation createOp = Operation
                    .createPost(UriUtils.buildUri(host, CompositeDescriptionContentService.SELF_LINK))
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
            Operation createOp = Operation
                    .createPost(UriUtils.buildUri(host, CompositeDescriptionContentService.SELF_LINK))
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
                    "Error processing YAML content: Cannot construct instance of `com.vmware.admiral.compute.content.compose.CommonDescriptionEntity` (although at least one Creator exists): no String-argument constructor/factory method to deserialize from String value ('abc')"));
            this.host.testWait();

            this.host.testStart(1);
            this.host.send(validateBadRequestOnImportOperation(getContent("docker.redis.v1.yaml"),
                    "Unknown YAML content type! Only Blueprint and Docker Compose v2 formats are supported."));
            this.host.testWait();

            this.host.testStart(1);
            this.host.send(validateBadRequestOnImportOperation(getContent("composite.bad.yaml"),
                    "Cannot deserialize instance of `java.lang.String` out of START_OBJECT token\n at [Source: UNKNOWN; line: -1, column: -1] (through reference chain: com.vmware.admiral.compute.container.ContainerDescriptionService$CompositeTemplateContainerDescription[\"logConfig\"])"));
            this.host.testWait();

            this.host.testStart(1);
            this.host.send(validateBadRequestOnImportOperation(new Date(),
                    "Failed to deserialize CompositeTemplate serialized content!"));
            this.host.testWait();

            this.host.testStart(1);
            this.host.send(validateBadRequestOnImportOperation(
                    getContent("docker.invalid.network.yaml"),
                    "Error processing Docker Compose v2 YAML content"));
            this.host.testWait();
        }

        @Test
        public void testValidateNetworkAlias() throws Throwable {
            this.host.testStart(1);
            this.host.send(
                    validateSuccessfulImportOperation(getContent("docker.network.alias.yaml")));
            this.host.testWait();
        }

        private Operation validateBadRequestOnImportOperation(Object body, String expectedMsg) {
            // import YAML to Container Description
            return Operation.createPost(UriUtils.buildUri(host, CompositeDescriptionContentService.SELF_LINK))
                    .setContentType((body instanceof String) ? MEDIA_TYPE_APPLICATION_YAML : Operation.MEDIA_TYPE_APPLICATION_JSON).setBody(body).setCompletion((o, e) -> {
                        if (e != null) {
                            try {
                                assertEquals(Operation.STATUS_CODE_BAD_REQUEST, o.getStatusCode());
                                assertTrue(e.getMessage().contains(expectedMsg));
                                assertTrue(o.getBody(ServiceErrorResponse.class).message.contains(expectedMsg));
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

        private Operation validateSuccessfulImportOperation(Object body) {
            // import YAML to Container Description
            return Operation
                    .createPost(
                            UriUtils.buildUri(host, CompositeDescriptionContentService.SELF_LINK))
                    .setContentType((body instanceof String) ? MEDIA_TYPE_APPLICATION_YAML
                            : Operation.MEDIA_TYPE_APPLICATION_JSON)
                    .setBody(body).setCompletion((o, e) -> {
                        if (e == null) {
                            assertEquals(Operation.STATUS_CODE_OK, o.getStatusCode());
                            host.completeIteration();
                        } else {
                            host.failIteration(
                                    new IllegalStateException("Test should have succeeded!"));
                        }
                    });
        }

        private void verifyCompositeDescription(String location) throws Throwable {
            String selfLink = location.substring(location.lastIndexOf(UriUtils.URI_PATH_CHAR) + 1);
            assertNotNull("documentSelfLink", selfLink);

            URI uri = UriUtils.buildUri(host, location);
            URI expandUri = UriUtils.extendUriWithQuery(uri, UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());

            Operation getCompositeDescOp = Operation.createGet(expandUri);

            List<String> descriptionLinks = new ArrayList<>();

            Consumer<Operation> verifier = (o) -> verifyTemplate.accept(o, descriptionLinks);
            verifyOperation(getCompositeDescOp, verifier);

            verifyDescriptions(descriptionLinks);

            verifyExport(selfLink);
        }

        private void verifyDescriptions(List<String> containerDescriptionLinks) throws Throwable {
            for (String link : containerDescriptionLinks) {
                verifyOperation(Operation.createGet(UriUtils.buildUri(host, link)), (o) -> {
                    ResourceState cd = o.getBody(ResourceState.class);
                    assertTrue("unexpected component name: " + cd.name,
                            Arrays.asList("wordpress", "mysql", "public-wpnet", "wpnet", "wordpress-mysql-svc",
                                    "wordpress-mysql-dpl", "wordpress-svc", "wordpress-dpl", "wordpress-lb",
                                    "wordpress-container-lb", "wordpress-secret", "wordpress-mysql-replcontr",
                                    "wordpress-mysql-pod").contains(cd.name));
                });
            }
        }

        private void verifyExport(String selfLink) throws Throwable {
            URI uri = UriUtils.buildUri(host, CompositeDescriptionContentService.SELF_LINK);
            uri = UriUtils.extendUriWithQuery(uri, CompositeDescriptionContentService.SELF_LINK_PARAM_NAME, selfLink);

            verifyOperation(Operation.createGet(uri), (o) -> {
                String resultYaml = o.getBody(String.class);

                // Skip this validation for now.
                if (isKubernetesYaml(resultYaml)) {
                    return;
                }

                // Can't compare the strings as the order of the components may not be the same.
                try {
                    CompositeTemplate original = CompositeTemplateUtil.deserializeCompositeTemplate(template);

                    CompositeTemplate result = CompositeTemplateUtil.deserializeCompositeTemplate(resultYaml);

                    assertEquals(original.components.size(), result.components.size());
                    assertEquals(original.id, result.id);
                    assertEquals(original.description, result.description);
                    assertEquals(original.name, result.name);
                    assertEquals(original.status, result.status);
                    assertEquals(original.properties, result.properties);

                    if (isBindingYaml(resultYaml)) {
                        assertBindings(original, result, template, resultYaml);
                    }
                } catch (IOException e) {
                    fail(e.getMessage());
                }
            });
        }

        private static String getContent(String filename) {
            return FileUtil.getResourceAsString("/compose/" + filename, true);
        }

        private static boolean isKubernetesYaml(String template) {
            return (template != null) && (template.contains("apiVersion: v1"));
        }

        private static boolean isBindingYaml(String template) {
            return (template != null) && (template.contains("wordPressWithMySqlBindings"));
        }

        @SuppressWarnings("unchecked")
        private static void assertBindings(CompositeTemplate original, CompositeTemplate result,
                String originalYaml, String resultYaml) throws IOException {

            // Verify that bindings are not present in the exported YAML blueprint...
            assertFalse(originalYaml.contains("bindings:"));
            assertFalse(resultYaml.contains("bindings:"));

            // ...but the values are where they are supposed to be...
            assertTrue(originalYaml.contains("${mysql~restart_policy}"));
            assertTrue(resultYaml.contains("${mysql~restart_policy}"));

            assertTrue(originalYaml.contains("${_resource~mysql~address}:3306"));
            assertTrue(resultYaml.contains("${_resource~mysql~address}:3306"));

            Map<String, Object> templateMap = YamlMapper.objectMapper().readValue(originalYaml.trim(), Map.class);
            Map<String, Object> resultMap = YamlMapper.objectMapper().readValue(resultYaml.trim(), Map.class);
            assertEquals(templateMap.get("components"), resultMap.get("components"));

            // ...and also when deserialized to CompositeTemplate.
            assertNotNull(original.bindings);
            assertEquals(1, original.bindings.size());
            ComponentBinding originalComponentBinding = original.bindings.get(0);
            assertEquals("wordpress", originalComponentBinding.componentName);
            assertNotNull(originalComponentBinding.bindings);
            assertEquals(2, originalComponentBinding.bindings.size());
            assertTrue(hasBindingExpression(originalComponentBinding.bindings, "${mysql~restart_policy}"));
            assertTrue(hasBindingExpression(originalComponentBinding.bindings, "${_resource~mysql~address}:3306"));

            assertNotNull(result.bindings);
            assertEquals(1, result.bindings.size());
            ComponentBinding resultComponentBinding = result.bindings.get(0);
            assertEquals("wordpress", resultComponentBinding.componentName);
            assertNotNull(resultComponentBinding.bindings);
            assertEquals(2, resultComponentBinding.bindings.size());
            assertTrue(hasBindingExpression(resultComponentBinding.bindings, "${mysql~restart_policy}"));
            assertTrue(hasBindingExpression(resultComponentBinding.bindings, "${_resource~mysql~address}:3306"));
        }
    }

    public static class CompositeDescriptionContentServiceNotParameterized extends ComputeBaseTest {

        @Before
        public void setUp() throws Throwable {
            waitForServiceAvailability(CompositeDescriptionContentService.SELF_LINK);
        }

        @Test
        public void testFailedImport() throws Throwable {
            String template = CommonTestStateFactory.getFileContent("VotingApp_no_image_provided.yaml");

            this.host.testStart(1);
            this.host.send(Operation
                    .createPost(UriUtils.buildUri(host, CompositeDescriptionContentService.SELF_LINK))
                    .setContentType(MEDIA_TYPE_APPLICATION_YAML)
                    .setBody(template)
                    .setReferer(host.getUri())
                    .setCompletion((o, ex) -> {
                        if (ex == null) {
                            host.failIteration(new Throwable("The operation should have failed."));
                            return;
                        }

                        final long timoutInMillis = 5000; // 5sec

                        try {
                            long startContainerDescTime = System.currentTimeMillis();
                            waitFor(() -> {
                                long count = getDocumentsOfType(ContainerDescription.class).stream()
                                        .filter(cs -> !SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK.equals(cs.documentSelfLink))
                                        .count();
                                if (System.currentTimeMillis() - startContainerDescTime > timoutInMillis) {
                                    throw new Throwable(String.format("Should not have any container descriptions. Count: %s", count));
                                }

                                return count == 0;
                            });

                            long startContainerNetworkDescTime = System.currentTimeMillis();
                            waitFor(() -> {
                                long count = getDocumentsOfType(ContainerNetworkDescription.class).stream().count();
                                if (System.currentTimeMillis() - startContainerNetworkDescTime > timoutInMillis) {
                                    throw new Throwable(String.format("Should not have any container network descriptions. Count: %s", count));
                                }

                                return count == 0;
                            });

                            long startContainerVolumeDescTime = System.currentTimeMillis();
                            waitFor(() -> {
                                long count = getDocumentsOfType(ContainerVolumeDescription.class).stream().count();
                                if (System.currentTimeMillis() - startContainerVolumeDescTime > timoutInMillis) {
                                    throw new Throwable(String.format("Should not have any container volume descriptions. Count: %s", count));
                                }

                                return count == 0;
                            });

                            host.completeIteration();
                        } catch (Throwable throwable) {
                            host.failIteration(throwable);
                        }
                    }));
            this.host.testWait();
        }
    }

}
