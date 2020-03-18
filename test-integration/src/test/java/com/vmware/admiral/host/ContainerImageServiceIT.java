/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.compute.RegistryHostConfigService;
import com.vmware.admiral.compute.RegistryHostConfigService.RegistryHostSpec;
import com.vmware.admiral.image.service.ContainerImageService;
import com.vmware.admiral.image.service.ContainerImageTagsService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.test.integration.SimpleHttpsClient;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerImageServiceIT extends BaseTestCase {
    private static final String TENANT = "/tenants/docker-test";

    private static final String TEST_IMAGE_DOCKER_HUB = "library/alpine";
    private static final String TEST_IMAGE_DOCKER_HUB_FULL_ADDRESS = "registry.hub.docker.com/"
            + TEST_IMAGE_DOCKER_HUB;

    @Before
    public void setUp() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(false);
        HostInitCommonServiceConfig.startServices(host);
        HostInitRegistryAdapterServiceConfig.startServices(host);
        HostInitImageServicesConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, false);
        waitForServiceAvailability(ConfigurationFactoryService.SELF_LINK);
        waitForServiceAvailability(RegistryAdapterService.SELF_LINK);
        waitForServiceAvailability(ContainerImageService.SELF_LINK);
        waitForServiceAvailability(RegistryService.DEFAULT_INSTANCE_LINK);
    }

    @Test
    public void testSearchForDockerImageSucceeds() throws Exception {
        verifyImageIsFound(TEST_IMAGE_DOCKER_HUB_FULL_ADDRESS, TENANT);
    }

    @Test
    public void testSearchForDockerImageSucceedsWithPartialResults() throws Exception {
        // Add a broken registry. It is not possible to do a search in
        // this one so we expect partial results to be returned.
        configureRegistry("https://non-existent.registry.local", TENANT, true);

        // we are not using the full image name so we will have to search in all registries
        verifyImageIsFound(TEST_IMAGE_DOCKER_HUB, TEST_IMAGE_DOCKER_HUB_FULL_ADDRESS, TENANT, true);
    }


    /**
     * Verifies that a given image can be found after performing a search in the registered
     * registries. It is expected that the search will yield complete (non-partial) results.
     *
     * @param searchImageName
     *            the name of the image to search for. It is expected that the same name in the same
     *            format will be found in the search results.
     * @param tenant
     * @throws Exception
     */
    private void verifyImageIsFound(String searchImageName, String tenant) throws Exception {
        verifyImageIsFound(searchImageName, searchImageName, tenant, false);
    }

    /**
     * Verifies that a given image can be found after performing a search in the registered
     * registries
     *
     * @param searchImageName
     *            the name of the image to search for
     * @param expectedResultImageName
     *            the name of the image in the format it is expected to appear in the search results
     *            (e.g. with registry hostname prepended)
     * @param tenant
     * @param expectPartialResult
     *            whether we expect the result to be partial (e.g. some of the search requests have
     *            failed)
     * @throws Exception
     */
    private void verifyImageIsFound(String searchImageName, String expectedResultImageName,
            String tenant, boolean expectPartialResult) throws Exception {

        URI searchImagesUri = UriUtils.buildUri(host, ContainerImageService.SELF_LINK);
        searchImagesUri = UriUtils.extendUriWithQuery(searchImagesUri,
                RegistryAdapterService.SEARCH_QUERY_PROP_NAME, searchImageName);
        if (tenant != null) {
            searchImagesUri = UriUtils.extendUriWithQuery(searchImagesUri,
                    ContainerImageTagsService.TENANT_LINKS_PARAM_NAME, tenant);
        }

        HttpResponse search = SimpleHttpsClient.execute(HttpMethod.GET, searchImagesUri.toString());
        assertEquals(HTTP_OK, search.statusCode);

        assertNotNull(search.responseBody);
        RegistrySearchResponse searchResults = Utils.fromJson(search.responseBody,
                RegistrySearchResponse.class);

        assertNotNull(searchResults);

        assertNotNull(searchResults.results);

        boolean imageExistsInSearchResults = searchResults.results.stream()
                .map(r -> r.name)
                .anyMatch(name -> name.equals(expectedResultImageName));
        assertTrue("image was expected to be found in search result: " + expectedResultImageName,
                imageExistsInSearchResults);
        assertEquals("Expected partial result value to be " + expectPartialResult,
                expectPartialResult, searchResults.isPartialResult);
    }

    private String configureRegistry(String registryAddress, String tenant, boolean acceptAddress)
            throws Exception {
        RegistryState registryState = new RegistryState();
        registryState.address = registryAddress;
        registryState.name = UUID.randomUUID().toString();
        if (tenant != null) {
            registryState.tenantLinks = Collections.singletonList(tenant);
        }
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;

        RegistryHostSpec registryHostSpec = new RegistryHostSpec();
        registryHostSpec.hostState = registryState;
        registryHostSpec.acceptHostAddress = acceptAddress;
        registryHostSpec.acceptCertificate = true;

        URI registryUri = UriUtils.buildUri(host, RegistryHostConfigService.SELF_LINK);
        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.PUT,
                registryUri.toString(), Utils.toJson(registryHostSpec));

        if (HttpURLConnection.HTTP_NO_CONTENT != httpResponse.statusCode) {
            throw new IllegalArgumentException("Add registry host failed with status code: "
                    + httpResponse.statusCode);
        }

        List<String> headers = httpResponse.headers.get(Operation.LOCATION_HEADER);
        String documentSelfLink = headers.get(0);
        return documentSelfLink;
    }
}
