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

package com.vmware.admiral.adapter.registry.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ImageOperationType;
import com.vmware.admiral.adapter.common.service.mock.MockTaskFactoryService;
import com.vmware.admiral.adapter.common.service.mock.MockTaskService.MockTaskState;
import com.vmware.admiral.adapter.registry.mock.BaseMockRegistryTestCase;
import com.vmware.admiral.adapter.registry.service.RegistryAdapterService.RegistryPingResponse;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse.Result;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.ApiVersion;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

/**
 * Test registry adapter service
 */
public class RegistryAdapterServiceTest extends BaseMockRegistryTestCase {

    private URI registryAdapterServiceUri;
    private String provisioningTaskLink;
    private String dockerHubRegistryStateLink;
    private String defaultRegistryStateLink;
    private String v2RegistryStateLink;

    @Before
    public void startServices() throws URISyntaxException {
        HostInitTestDcpServicesConfig.startServices(host);
        HostInitCommonServiceConfig.startServices(host);

        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockTaskFactoryService.SELF_LINK)),
                new MockTaskFactoryService());

        registryAdapterServiceUri = UriUtils.buildUri(host, RegistryAdapterService.class);
        host.startService(
                Operation.createPost(registryAdapterServiceUri),
                new RegistryAdapterService());
    }

    @Before
    public void createProvisioningTask() throws Throwable {
        MockTaskState provisioningTask = new MockTaskState();
        provisioningTaskLink = doPost(provisioningTask,
                MockTaskFactoryService.SELF_LINK).documentSelfLink;
    }

    @Before
    public void createDockerHubRegistryState() throws Throwable {
        RegistryState registryState = new RegistryState();
        registryState.address = getDockerHubRegistryUri().toString();
        registryState.customProperties = new HashMap<>();
        registryState.customProperties.put(RegistryService.API_VERSION_PROP_NAME,
                ApiVersion.V1.toString());

        dockerHubRegistryStateLink = doPost(registryState,
                RegistryFactoryService.SELF_LINK).documentSelfLink;
        createSslTrustCert(new URI(registryState.address).getHost());
    }

    @Before
    public void createDefaultRegistryState() throws Throwable {
        // a registry without explicitly specified 'apiVersion' property
        // is considered *V1* by default
        RegistryState registryState = new RegistryState();
        registryState.address = getDefaultRegistryUri().toString();

        defaultRegistryStateLink = doPost(registryState,
                RegistryFactoryService.SELF_LINK).documentSelfLink;
        createSslTrustCert(new URI(registryState.address).getHost());
    }

    @Before
    public void createV2RegistryState() throws Throwable {
        RegistryState registryState = new RegistryState();
        registryState.address = getV2RegistryUri().toString();
        registryState.customProperties = new HashMap<>();
        registryState.customProperties.put(RegistryService.API_VERSION_PROP_NAME,
                ApiVersion.V2.toString());

        v2RegistryStateLink = doPost(registryState,
                RegistryFactoryService.SELF_LINK).documentSelfLink;
        createSslTrustCert(new URI(registryState.address).getHost());
    }

    @Test
    public void testV1Ping() throws Throwable {
        sendRegistryPingRequest(defaultRegistryUri, (Operation op) -> {
            RegistryPingResponse response = op.getBody(RegistryPingResponse.class);
            assertEquals("Unexpected registry version", response.apiVersion, ApiVersion.V1);
        });
    }

    @Test
    public void testV2Ping() throws Throwable {
        sendRegistryPingRequest(v2RegistryUri, (Operation op) -> {
            RegistryPingResponse response = op.getBody(RegistryPingResponse.class);
            assertEquals("Unexpected registry version", response.apiVersion, ApiVersion.V2);
        });
    }

    @Test
    public void testFailPing() throws Throwable {
        ImageRequest request = new ImageRequest();
        request.operationTypeId = ImageOperationType.PING.id;
        // invalid registry endpoint
        request.resourceReference = host.getUri();
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        request.customProperties = new HashMap<>();

        Operation adapterOperation = Operation
                .createPatch(registryAdapterServiceUri)
                .setReferer(host.getUri())
                .setBody(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        assertNotNull("Error message expected", ex.getMessage());
                        assertTrue(ex.getMessage().contains("Ping attempts failed with errors"));
                        host.completeIteration();
                        return;
                    }

                    host.failIteration(new IllegalStateException("Failure expected."));
                });

        host.testStart(1);
        host.send(adapterOperation);
        host.testWait();
    }

    @Test
    public void testBadRequest() throws Throwable {
        Operation adapterOperation = Operation
                .createPatch(registryAdapterServiceUri)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        assertNotNull("Error message expected", ex.getMessage());
                        assertTrue(ex instanceof IllegalArgumentException);
                        host.completeIteration();
                        return;
                    }

                    host.failIteration(new IllegalStateException("Failure expected."));
                });

        host.testStart(1);
        host.send(adapterOperation);
        host.testWait();
    }

    @Test
    public void testV1Search() throws Throwable {
        URI defaultRegistryStateUri = UriUtils.buildUri(host, defaultRegistryStateLink);

        sendRegistrySearchRequest(defaultRegistryStateUri, "ubuntu", (Operation op) -> {
            RegistrySearchResponse response = op.getBody(RegistrySearchResponse.class);
            assertNotNull("result is null", response);
            assertEquals("Unexpected number of results", 10, response.numResults);
            assertEquals("Unexpected number of results", 10, response.results.size());
            Result result = response.results.get(0);
            // V1 images names has default namespace 'library'
            assertEquals("results[0].name", "library/ubuntu", result.name);
            assertEquals("results[0].is_official", true, result.official);
        });

    }

    @Test
    public void testTrustCertRemovedAfterRegistryRemoval() throws
            Throwable {
        List<String>  trustCertList = getDocumentLinksOfType(SslTrustCertificateState.class);
        int trustCertInitialSize = trustCertList.size();
        assertEquals(1, trustCertInitialSize);
        String trustCertLink = trustCertList.get(0);

        RegistryState registryState = new RegistryState();
        registryState.customProperties = new HashMap<>();
        registryState.customProperties.put(RegistryService.REGISTRY_TRUST_CERTS_PROP_NAME,
                trustCertLink);
        doPatch(registryState, defaultRegistryStateLink);
        doPatch(registryState, v2RegistryStateLink);
        doPatch(registryState, dockerHubRegistryStateLink);

        doDelete(UriUtils.buildUri(host, defaultRegistryStateLink), false);
        trustCertList = getDocumentLinksOfType(SslTrustCertificateState.class);
        assertEquals(trustCertInitialSize, trustCertList.size());

        doDelete(UriUtils.buildUri(host, v2RegistryStateLink), false);
        trustCertList = getDocumentLinksOfType(SslTrustCertificateState.class);
        assertEquals(trustCertInitialSize, trustCertList.size());

        doDelete(UriUtils.buildUri(host, dockerHubRegistryStateLink), false);
        trustCertList = getDocumentLinksOfType(SslTrustCertificateState.class);
        assertEquals(trustCertInitialSize - 1, trustCertList.size());
    }

    @Test
    public void testV2Search() throws Throwable {
        URI v2RegistryStateUri = UriUtils.buildUri(host, v2RegistryStateLink);

        sendRegistrySearchRequest(v2RegistryStateUri, "v2image", (Operation op) -> {
            RegistrySearchResponse response = op.getBody(RegistrySearchResponse.class);
            assertNotNull("result is null", response);
            assertEquals("Unexpected number of results", 2, response.results.size());
            Result result = response.results.get(0);
            assertEquals("results[0].name", "test/v2image", result.name);
        });
    }

    @Test
    public void testUnsupportedRegistryVersion() throws Throwable {

        doDelete(UriUtils.buildUri(host, dockerHubRegistryStateLink), false);

        RegistryState registryState = new RegistryState();
        registryState.address = "http://0.0.0.0";
        registryState.customProperties = new HashMap<>();
        registryState.customProperties.put(RegistryService.API_VERSION_PROP_NAME, "V99");

        String unsupportedRegistryStateLink = doPost(registryState,
                RegistryFactoryService.SELF_LINK).documentSelfLink;
        URI unsupportedRegistryStateUri = UriUtils.buildUri(host, unsupportedRegistryStateLink);

        ImageRequest request = new ImageRequest();
        request.operationTypeId = ImageOperationType.SEARCH.id;
        request.resourceReference = unsupportedRegistryStateUri;
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        request.customProperties = new HashMap<>();
        request.customProperties.put(RegistryAdapterService.SEARCH_QUERY_PROP_NAME,
                "foobar/vmware/admiral");

        Operation adapterOperation = Operation
                .createPatch(registryAdapterServiceUri)
                .setReferer(host.getUri())
                .setBody(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        try {
                            assertNotNull("Error message expected", ex.getMessage());
                            assertTrue(ex.getMessage().contains("Unsupported registry version"));
                            host.completeIteration();
                        } catch (AssertionError e) {
                            host.failIteration(e);
                        }

                        return;
                    }

                    host.failIteration(new IllegalStateException("Failure expected."));
                });

        host.testStart(1);
        host.send(adapterOperation);
        host.testWait();

        MockTaskState provisioningTask = new MockTaskState();
        provisioningTaskLink = doPost(provisioningTask,
                MockTaskFactoryService.SELF_LINK).documentSelfLink;

        request = new ImageRequest();
        request.operationTypeId = ImageOperationType.LIST_TAGS.id;
        request.resourceReference = unsupportedRegistryStateUri;
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        request.customProperties = new HashMap<>();
        request.customProperties.put(RegistryAdapterService.SEARCH_QUERY_PROP_NAME,
                "foobar/vmware/admiral");

        adapterOperation = Operation
                .createPatch(registryAdapterServiceUri)
                .setReferer(host.getUri())
                .setBody(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        try {
                            assertNotNull("Error message expected", ex.getMessage());
                            assertTrue(ex.getMessage().contains("Unsupported registry version"));
                            host.completeIteration();
                        } catch (AssertionError e) {
                            host.failIteration(e);
                        }

                        return;
                    }

                    host.failIteration(new IllegalStateException("Failure expected."));
                });

        host.testStart(1);
        host.send(adapterOperation);
        host.testWait();
    }


    @Test
    public void testDuplicateRegistryInsideProjectShouldFail() throws Throwable {
        RegistryState registryState = new RegistryState();
        registryState.address = "http://0.0.0.0";
        registryState.name = "TestRegistry";
        registryState.tenantLinks = new ArrayList<>();
        registryState.tenantLinks.add("test-project");

        doPost(registryState, RegistryFactoryService.SELF_LINK);

        try {
            doPost(registryState, RegistryFactoryService.SELF_LINK);
            fail("Should not be possible to add the same registry twice in the same scope");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains(RegistryFactoryService.REGISTRY_ALREADY_EXISTS));
        }
    }

    @Test
    public void testDuplicateGlobalRegistryShrouldFails() throws Throwable {
        RegistryState registryState = new RegistryState();
        registryState.address = "http://0.0.0.0";
        registryState.name = "TestRegistry";

        doPost(registryState, RegistryFactoryService.SELF_LINK);

        try {
            doPost(registryState, RegistryFactoryService.SELF_LINK);
            fail("Should not be possible to add the same registry twice in the same scope");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains(RegistryFactoryService.REGISTRY_ALREADY_EXISTS));
        }
    }

    @Test
    public void testSameRegistryAsGlobalAndProject() throws Throwable {
        RegistryState registryState = new RegistryState();
        registryState.address = "http://0.0.0.0";
        registryState.name = "TestRegistry";
        registryState.tenantLinks = new ArrayList<>();

        doPost(registryState, RegistryFactoryService.SELF_LINK);

        registryState.tenantLinks.add("test-project");
        doPost(registryState, RegistryFactoryService.SELF_LINK);
    }

    @Test
    public void testSameRegistryTwoDifferentProjectsRemoveOldTenantLink() throws Throwable {
        RegistryState registryState = new RegistryState();
        registryState.address = "http://0.0.0.0";
        registryState.name = "TestRegistry";
        registryState.tenantLinks = new ArrayList<>();

        registryState.tenantLinks.add("test-project1");
        doPost(registryState, RegistryFactoryService.SELF_LINK);

        registryState.tenantLinks.remove("test-project1");
        registryState.tenantLinks.add("test-project2");

        doPost(registryState, RegistryFactoryService.SELF_LINK);
    }

    @Test
    public void testSameRegistryTwoDifferentProjectsNotRemoveOldTenantLinkShouldFail() throws
            Throwable {
        RegistryState registryState = new RegistryState();
        registryState.address = "http://0.0.0.0";
        registryState.name = "TestRegistry";
        registryState.tenantLinks = new ArrayList<>();

        registryState.tenantLinks.add("test-project1");
        doPost(registryState, RegistryFactoryService.SELF_LINK);

        registryState.tenantLinks.add("test-project2");
        try {
            doPost(registryState, RegistryFactoryService.SELF_LINK);
            fail("Should not be possible to add the same registry in two different projects");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains(RegistryFactoryService.REGISTRY_ALREADY_EXISTS));
        }
    }

    @Test
    public void testSameRegistryPartiallyMatchingAddress() throws Throwable {
        RegistryState registryState = new RegistryState();
        registryState.address = "http://example.com:5000";
        registryState.name = "TestRegistry1";
        registryState.tenantLinks = new ArrayList<>();

        registryState.tenantLinks.add("test-project");
        doPost(registryState, RegistryFactoryService.SELF_LINK);

        registryState.address = "http://example.com:5000/extra/path";
        registryState.name = "TestRegistry2";
        doPost(registryState, RegistryFactoryService.SELF_LINK);
    }

    @Test
    public void testDockerHubListImageTags() throws Throwable {
        URI dockerHubRegistryStateUri = UriUtils.buildUri(host, dockerHubRegistryStateLink);

        sendRegistryListTagsRequest(dockerHubRegistryStateUri, "vmware/admiral", (Operation op) -> {
            String[] tags = op.getBody(String[].class);
            assertNotNull("result is null", tags);
            assertArrayEquals(new String[] { "7.1", "7.2", "7.3", "7.4" }, tags);
        });
    }

    @Test
    public void testV1ListImageTags() throws Throwable {
        URI defaultRegistryStateUri = UriUtils.buildUri(host, defaultRegistryStateLink);

        sendRegistryListTagsRequest(defaultRegistryStateUri, "v1registry.test/vmware/admiral", (Operation op) -> {
            String[] tags = op.getBody(String[].class);
            assertNotNull("result is null", tags);
            assertArrayEquals(new String[] { "7.1", "7.2", "7.3", "7.4" }, tags);
        });
    }

    @Test
    public void testV2ListImageTags() throws Throwable {
        URI v2RegistryStateUri = UriUtils.buildUri(host, v2RegistryStateLink);

        sendRegistryListTagsRequest(v2RegistryStateUri, "v2registry.test/vmware/admiral", (Operation op) -> {
            String[] tags = op.getBody(String[].class);
            assertNotNull("result is null", tags);
            assertArrayEquals(new String[] { "7.1", "7.2", "7.3", "7.4" }, tags);
        });
    }

    private void sendRegistrySearchRequest(URI registryStateLink, String searchTerm,
            Consumer<Operation> consumeResult) throws Throwable {

        sendRegistryRequest(ImageOperationType.SEARCH, registryStateLink,
                searchTerm, consumeResult);
    }

    private void sendRegistryPingRequest(URI registryStateLink,
            Consumer<Operation> consumeResult) throws Throwable {

        sendRegistryRequest(ImageOperationType.PING, registryStateLink, null, consumeResult);
    }

    private void sendRegistryListTagsRequest(URI registryStateLink, String searchTerm,
            Consumer<Operation> consumeResult) throws Throwable {

        sendRegistryRequest(ImageOperationType.LIST_TAGS, registryStateLink, searchTerm,
                consumeResult);
    }

    private void sendRegistryRequest(ImageOperationType type, URI resourceReference,
            String searchTerm, Consumer<Operation> consumeResult)
            throws Throwable {

        ImageRequest request = new ImageRequest();
        request.operationTypeId = type.id;
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        request.resourceReference = resourceReference;
        request.customProperties = new HashMap<>();
        request.customProperties.put(RegistryAdapterService.SEARCH_QUERY_PROP_NAME, searchTerm);

        Operation adapterOperation = Operation
                .createPatch(registryAdapterServiceUri)
                .setReferer(URI.create("/")).setBody(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                    }

                    try {
                        if (consumeResult != null) {
                            consumeResult.accept(o);
                        }
                        host.completeIteration();

                    } catch (Throwable t) {
                        host.failIteration(t);
                    }
                });

        host.testStart(1);
        host.send(adapterOperation);
        host.testWait();
    }

    private SslTrustCertificateState createSslTrustCert(String commonName) throws Throwable {
        SslTrustCertificateState sslTrustCert = new SslTrustCertificateState();
        String sslTrust1 = CommonTestStateFactory.getFileContent("test_ssl_trust.PEM").trim();
        sslTrustCert.certificate = sslTrust1;
        sslTrustCert.commonName = commonName;

        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);

        return sslTrustCert;
    }
}
