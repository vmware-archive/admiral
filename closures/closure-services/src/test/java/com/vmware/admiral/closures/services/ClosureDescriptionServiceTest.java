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

package com.vmware.admiral.closures.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.closures.drivers.DriverConstants;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescriptionFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ResourceConstraints;
import com.vmware.admiral.closures.util.ClosureProps;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class ClosureDescriptionServiceTest extends BasicReusableHostTestCase {

    @Before
    public void setUp() throws Exception {
        try {
            if (this.host.getServiceStage(ClosureDescriptionFactoryService.FACTORY_LINK) != null) {
                return;
            }
            // Start a closure definition factory service
            this.host.startServiceAndWait(ClosureDescriptionFactoryService.class,
                    ClosureDescriptionFactoryService.FACTORY_LINK);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void invalidLanguageNegativeTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.documentSelfLink = UUID.randomUUID().toString();
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertOperationFailed(e);
                }));
        this.host.send(post);
        this.host.testWait();
    }

    @Test
    public void unsupportedLanguageNegativeTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.runtime = "haskell";
        initialState.documentSelfLink = UUID.randomUUID().toString();
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertOperationFailed(e);
                }));
        this.host.send(post);
        this.host.testWait();
    }

    @Test
    public void invalidLangSourceNegativeTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.documentSelfLink = UUID.randomUUID().toString();
        initialState.runtime = DriverConstants.RUNTIME_NODEJS_4;
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertOperationFailed(e);
                }));
        this.host.send(post);
        this.host.testWait();
    }

    @Test
    public void invalidNodeJsDependenciesFormatNegativeTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.documentSelfLink = UUID.randomUUID().toString();
        initialState.runtime = DriverConstants.RUNTIME_NODEJS_4;
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        initialState.dependencies = "invalid json";
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertOperationFailed(e);
                }));
        this.host.send(post);
        this.host.testWait();
    }

    @Test
    public void invalidRuntimeNegativeTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        initialState.documentSelfLink = UUID.randomUUID().toString();
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertOperationFailed(e);
                }));
        this.host.send(post);
        this.host.testWait();
    }

    @Test
    public void invalidEntrypointNegativeTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.runtime = DriverConstants.RUNTIME_NODEJS_4;
        initialState.entrypoint = "invalid";
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        initialState.documentSelfLink = UUID.randomUUID().toString();
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertOperationFailed(e);
                }));
        this.host.send(post);
        this.host.testWait();
    }

    @Test
    public void addDefaultClosureDefinitionTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.runtime = DriverConstants.RUNTIME_NODEJS_4;
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        initialState.entrypoint = "modulename.handlername";
        initialState.documentSelfLink = UUID.randomUUID().toString();
        ClosureDescription[] responses = new ClosureDescription[1];
        URI childURI = UriUtils.buildPublicUri(this.host, ClosureDescriptionFactoryService.FACTORY_LINK,
                initialState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);

                    assertEquals(initialState.source, responses[0].source);
                    assertEquals(initialState.runtime, responses[0].runtime);
                    assertEquals(initialState.entrypoint, responses[0].entrypoint);
                    assertNotNull(responses[0].resources);
                    assertEquals(ClosureProps.DEFAULT_CPU_SHARES, responses[0].resources.cpuShares);
                    assertEquals(ClosureProps.DEFAULT_MEMORY_MB_RES_CONSTRAINT, responses[0].resources.ramMB);
                    assertEquals(ClosureProps.DEFAULT_EXEC_TIMEOUT_SECONDS, responses[0].resources
                            .timeoutSeconds);
                }));

        this.host.send(post);
        this.host.testWait();

        // delete instance
        clean(childURI);
    }

    @Test
    public void addClosureDescriptionWithDefaultInputsTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.runtime = DriverConstants.RUNTIME_NODEJS_4;
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(10));
        initialState.inputs = inputs;
        initialState.entrypoint = "modulename.handlername";
        initialState.documentSelfLink = UUID.randomUUID().toString();
        ClosureDescription[] responses = new ClosureDescription[1];
        URI childURI = UriUtils.buildPublicUri(this.host, ClosureDescriptionFactoryService.FACTORY_LINK,
                initialState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);

                    assertEquals(initialState.source, responses[0].source);
                    assertEquals(initialState.runtime, responses[0].runtime);
                    assertEquals(initialState.entrypoint, responses[0].entrypoint);
                    assertNotNull(initialState.inputs);
                    assertEquals(10, initialState.inputs.get("a").getAsInt());
                    assertNotNull(responses[0].resources);
                    assertEquals(ClosureProps.DEFAULT_CPU_SHARES, responses[0].resources.cpuShares);
                    assertEquals(ClosureProps.DEFAULT_MEMORY_MB_RES_CONSTRAINT, responses[0].resources.ramMB);
                    assertEquals(ClosureProps.DEFAULT_EXEC_TIMEOUT_SECONDS, responses[0].resources
                            .timeoutSeconds);
                }));

        this.host.send(post);
        this.host.testWait();

        // delete instance
        clean(childURI);
    }

    @Test
    public void editClosureDescriptionTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.runtime = DriverConstants.RUNTIME_NODEJS_4;
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(10));
        initialState.inputs = inputs;
        initialState.entrypoint = "modulename.handlername";
        initialState.documentSelfLink = UUID.randomUUID().toString();
        ClosureDescription[] responses = new ClosureDescription[1];
        URI childURI = UriUtils.buildUri(this.host, ClosureDescriptionFactoryService.FACTORY_LINK + "/" + initialState
                .documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);

                    assertEquals(initialState.source, responses[0].source);
                    assertEquals(initialState.runtime, responses[0].runtime);
                    assertEquals(initialState.entrypoint, responses[0].entrypoint);
                    assertNotNull(initialState.inputs);
                    assertEquals(10, initialState.inputs.get("a").getAsInt());
                    assertNotNull(responses[0].resources);
                    assertEquals(ClosureProps.DEFAULT_CPU_SHARES, responses[0].resources.cpuShares);
                    assertEquals(ClosureProps.DEFAULT_MEMORY_MB_RES_CONSTRAINT, responses[0].resources.ramMB);
                    assertEquals(ClosureProps.DEFAULT_EXEC_TIMEOUT_SECONDS, responses[0].resources
                            .timeoutSeconds);
                }));

        this.host.send(post);
        this.host.testWait();

        this.host.testStart(1);
        // edit created description
        ClosureDescription newState = new ClosureDescription();
        newState.name = "test-changed";
        newState.runtime = DriverConstants.RUNTIME_NODEJS_4;
        newState.source = "var a = 1; print(\"Hello \" + a);";
        inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(20));
        newState.inputs = inputs;
        newState.entrypoint = "modulename.handlername";
        Operation patch = Operation
                .createPatch(this.host,
                        ClosureDescriptionFactoryService.FACTORY_LINK + "/" + initialState.documentSelfLink)
                .setBody(newState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);

                    assertEquals(newState.name, responses[0].name);
                    assertEquals(newState.source, responses[0].source);
                    assertEquals(newState.runtime, responses[0].runtime);
                    assertEquals(newState.entrypoint, responses[0].entrypoint);
                    assertNotNull(newState.inputs);
                    assertEquals(20, newState.inputs.get("a").getAsInt());
                    assertNotNull(responses[0].resources);
                    assertEquals(ClosureProps.MIN_CPU_SHARES, responses[0].resources.cpuShares);
                    assertEquals(ClosureProps.DEFAULT_MEMORY_MB_RES_CONSTRAINT, responses[0].resources.ramMB);
                    assertEquals(ClosureProps.DEFAULT_EXEC_TIMEOUT_SECONDS, responses[0].resources
                            .timeoutSeconds);
                }));

        this.host.send(patch);
        this.host.testWait();

        // delete instance
        clean(childURI);
    }

    @Test
    public void editInvalidClosureDescriptionTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.runtime = DriverConstants.RUNTIME_NODEJS_4;
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(10));
        initialState.inputs = inputs;
        initialState.entrypoint = "modulename.handlername";
        initialState.documentSelfLink = UUID.randomUUID().toString();
        ClosureDescription[] responses = new ClosureDescription[1];
        URI childURI = UriUtils.buildUri(this.host, ClosureDescriptionFactoryService.FACTORY_LINK + "/" + initialState
                .documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);

                    assertEquals(initialState.source, responses[0].source);
                    assertEquals(initialState.runtime, responses[0].runtime);
                    assertEquals(initialState.entrypoint, responses[0].entrypoint);
                    assertNotNull(initialState.inputs);
                    assertEquals(10, initialState.inputs.get("a").getAsInt());
                    assertNotNull(responses[0].resources);
                    assertEquals(ClosureProps.DEFAULT_CPU_SHARES, responses[0].resources.cpuShares);
                    assertEquals(ClosureProps.DEFAULT_MEMORY_MB_RES_CONSTRAINT, responses[0].resources.ramMB);
                    assertEquals(ClosureProps.DEFAULT_EXEC_TIMEOUT_SECONDS, responses[0].resources
                            .timeoutSeconds);
                }));

        this.host.send(post);
        this.host.testWait();

        this.host.testStart(1);
        // edit created description
        ClosureDescription newState = new ClosureDescription();
        newState.name = "test-changed";
        newState.runtime = "INVALID RUNTIME";
        newState.source = "var a = 1; print(\"Hello \" + a);";
        inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(20));
        newState.inputs = inputs;
        newState.entrypoint = "modulename.handlername";
        Operation patch = Operation
                .createPatch(this.host,
                        ClosureDescriptionFactoryService.FACTORY_LINK + "/" + initialState.documentSelfLink)
                .setBody(newState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNotNull(e);
                }));

        this.host.send(patch);
        this.host.testWait();

        // delete instance
        clean(childURI);
    }

    @Test
    public void addTaskDefinitionWithLogConfigurationTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.runtime = DriverConstants.RUNTIME_NODEJS_4;
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        initialState.runtime = "nashorn";
        initialState.logConfiguration = new JsonObject();
        ((JsonObject)initialState.logConfiguration).addProperty("type", "json-file");
        JsonObject configuration = new JsonObject();
        configuration.addProperty("max-size", "200k");
        ((JsonObject)initialState.logConfiguration).add("config", configuration);

        initialState.documentSelfLink = UUID.randomUUID().toString();
        ClosureDescription[] responses = new ClosureDescription[1];
        URI childURI = UriUtils.buildPublicUri(this.host, ClosureDescriptionFactoryService.FACTORY_LINK,
                initialState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);

                    assertEquals(initialState.source, responses[0].source);
                    assertEquals(initialState.runtime, responses[0].runtime);
                    assertEquals(((JsonObject)initialState.logConfiguration).get("type").getAsString(), "json-file");

                    assertEquals(
                            ((JsonObject)initialState.logConfiguration).get("config").getAsJsonObject().get("max-size").getAsString(),
                            "200k");
                    assertNotNull(responses[0].resources);
                    assertEquals(ClosureProps.DEFAULT_CPU_SHARES, responses[0].resources.cpuShares);
                    assertEquals(ClosureProps.DEFAULT_MEMORY_MB_RES_CONSTRAINT, responses[0].resources.ramMB);
                    assertEquals(ClosureProps.DEFAULT_EXEC_TIMEOUT_SECONDS, responses[0].resources
                            .timeoutSeconds);
                }));

        this.host.send(post);
        this.host.testWait();

        // delete instance
        clean(childURI);
    }

    @Test
    public void addTaskWithInputsResourcesDefinitionTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        initialState.runtime = "nashorn";
        ResourceConstraints resources = new ResourceConstraints();
        resources.ramMB = ClosureProps.MAX_MEMORY_MB_RES_CONSTRAINT;
        resources.timeoutSeconds = 10;
        initialState.resources = resources;
        initialState.documentSelfLink = UUID.randomUUID().toString();
        ClosureDescription[] responses = new ClosureDescription[1];
        URI childURI = UriUtils.buildPublicUri(this.host, ClosureDescriptionFactoryService.FACTORY_LINK, initialState
                .documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);

                    assertEquals(initialState.source, responses[0].source);
                    assertEquals(initialState.runtime, responses[0].runtime);
                    assertNotNull(responses[0].resources);
                    assertEquals(ClosureProps.DEFAULT_CPU_SHARES, responses[0].resources.cpuShares);
                    assertEquals(ClosureProps.MAX_MEMORY_MB_RES_CONSTRAINT, responses[0].resources.ramMB);
                    assertEquals(initialState.resources.timeoutSeconds, responses[0].resources.timeoutSeconds);
                }));

        this.host.send(post);
        this.host.testWait();

        // delete instance
        clean(childURI);
    }

    @Test
    public void addTaskWithTooLowResourcesConstraintsTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        initialState.runtime = "nashorn";
        ResourceConstraints resources = new ResourceConstraints();
        resources.ramMB = -1;
        resources.timeoutSeconds = -1;
        initialState.resources = resources;
        initialState.documentSelfLink = UUID.randomUUID().toString();
        ClosureDescription[] responses = new ClosureDescription[1];
        URI childURI = UriUtils.buildPublicUri(this.host, ClosureDescriptionFactoryService.FACTORY_LINK, initialState
                .documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);

                    assertEquals(initialState.source, responses[0].source);
                    assertEquals(initialState.runtime, responses[0].runtime);
                    assertNotNull(responses[0].resources);
                    assertEquals(ClosureProps.MIN_CPU_SHARES, responses[0].resources.cpuShares);
                    assertEquals(ClosureProps.MIN_MEMORY_MB_RES_CONSTRAINT, responses[0].resources.ramMB);
                    assertEquals(ClosureProps.MIN_EXEC_TIMEOUT_SECONDS, responses[0].resources.timeoutSeconds);
                }));

        this.host.send(post);
        this.host.testWait();

        // delete instance
        clean(childURI);
    }

    @Test
    public void addTaskWithTooHighResourcesConstraintsTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        initialState.runtime = "nashorn";
        ResourceConstraints resources = new ResourceConstraints();
        resources.ramMB = 10000;
        resources.timeoutSeconds = 10000;
        initialState.resources = resources;
        initialState.documentSelfLink = UUID.randomUUID().toString();
        ClosureDescription[] responses = new ClosureDescription[1];
        URI childURI = UriUtils.buildPublicUri(this.host, ClosureDescriptionFactoryService.FACTORY_LINK, initialState
                .documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);

                    assertEquals(initialState.source, responses[0].source);
                    assertEquals(initialState.runtime, responses[0].runtime);
                    assertNotNull(responses[0].resources);
                    assertEquals(ClosureProps.DEFAULT_CPU_SHARES, responses[0].resources.cpuShares);
                    assertEquals(ClosureProps.MAX_MEMORY_MB_RES_CONSTRAINT, responses[0].resources.ramMB);
                    assertEquals(ClosureProps.MAX_EXEC_TIMEOUT_SECONDS, responses[0].resources
                            .timeoutSeconds);
                }));

        this.host.send(post);
        this.host.testWait();

        // delete instance
        clean(childURI);
    }

    @Test
    public void addTaskWithResourcesConstraintsTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        initialState.runtime = "nashorn";
        ResourceConstraints resources = new ResourceConstraints();
        resources.ramMB = 768;
        resources.timeoutSeconds = 5;
        initialState.resources = resources;
        initialState.documentSelfLink = UUID.randomUUID().toString();
        ClosureDescription[] responses = new ClosureDescription[1];
        URI childURI = UriUtils.buildPublicUri(this.host, ClosureDescriptionFactoryService.FACTORY_LINK, initialState
                .documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);

                    assertEquals(initialState.source, responses[0].source);
                    assertEquals(initialState.runtime, responses[0].runtime);
                    assertEquals(initialState.runtime, responses[0].runtime);
                    assertNotNull(responses[0].resources);
                    assertEquals(ClosureProps.DEFAULT_CPU_SHARES / 2, (int) responses[0].resources
                            .cpuShares);
                    assertEquals(resources.ramMB, responses[0].resources.ramMB);
                    assertEquals(resources.timeoutSeconds, responses[0].resources.timeoutSeconds);
                }));

        this.host.send(post);
        this.host.testWait();

        // delete instance
        clean(childURI);
    }

    @Test
    public void addTaskWithLowResourcesConstraintsTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription initialState = new ClosureDescription();
        initialState.name = "test";
        initialState.source = "var a = 1; print(\"Hello \" + a);";
        initialState.runtime = "nashorn";
        ResourceConstraints resources = new ResourceConstraints();
        resources.ramMB = 1;
        resources.timeoutSeconds = 1;
        initialState.resources = resources;
        initialState.documentSelfLink = UUID.randomUUID().toString();
        ClosureDescription[] responses = new ClosureDescription[1];
        URI childURI = UriUtils.buildPublicUri(this.host, ClosureDescriptionFactoryService.FACTORY_LINK, initialState
                .documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);

                    assertEquals(initialState.source, responses[0].source);
                    assertEquals(initialState.runtime, responses[0].runtime);
                    assertNotNull(responses[0].resources);
                    assertEquals(ClosureProps.MIN_CPU_SHARES, responses[0].resources.cpuShares);
                    assertEquals(ClosureProps.MIN_MEMORY_MB_RES_CONSTRAINT, responses[0].resources.ramMB);
                    assertEquals(resources.timeoutSeconds, responses[0].resources.timeoutSeconds);
                }));

        this.host.send(post);
        this.host.testWait();

        // delete instance
        clean(childURI);
    }

    private void assertOperationFailed(Throwable e) {
        Assert.assertNotNull("Operation should fail: ", e);
    }

    private void clean(URI childURI) throws Throwable {
        this.host.testStart(1);
        Operation delete = Operation
                .createDelete(childURI)
                .setCompletion(getSafeHandler((o, e) -> {
                    assertNull(e);
                }));
        this.host.send(delete);
        this.host.testWait();
    }
}
