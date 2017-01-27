/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
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

import static org.hamcrest.CoreMatchers.hasItems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.UriUtilsExtended;
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

public class ContainerImageTagsIT extends BaseTestCase {
    private static final String TENANT = "/tenants/docker-test";

    private static final String DEFAULT_TAG = "latest";

    private static final String TEST_IMAGE = "vmware/bellevue";
    private static final String TEST_IMAGE_DOCKER_HUB = "alpine";
    private static final String TEST_IMAGE_DOCKER_HUB_FULL_ADDRESS =
            "registry.hub.docker.com/library/alpine";

    private static String v1RegistryAddress;
    private static String v2RegistryAddress;
    private static String defaultRegistryAddress;

    @BeforeClass
    public static void setUpClass() throws Exception {
        v1RegistryAddress = getTestRequiredProp("docker.registry.host.address");
        v2RegistryAddress = getTestRequiredProp("docker.v2.registry.host.address");
        defaultRegistryAddress = getPrivateField(
                RegistryService.class.getDeclaredField("DEFAULT_REGISTRY_ADDRESS"), null);
    }

    @Before
    public void setUp() throws Throwable {
        HostInitCommonServiceConfig.startServices(host);
        HostInitRegistryAdapterServiceConfig.startServices(host);
        HostInitImageServicesConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, false);
        waitForServiceAvailability(ConfigurationFactoryService.SELF_LINK);
        waitForServiceAvailability(RegistryAdapterService.SELF_LINK);
        waitForServiceAvailability(ContainerImageService.SELF_LINK);
        waitForServiceAvailability(RegistryService.DEFAULT_INSTANCE_LINK);
        waitForServiceAvailability(ContainerImageTagsService.SELF_LINK);
    }

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    @Test
    public void testListTagsFromV1Registry() throws Exception {
        String imageName = toFullImageName(v1RegistryAddress, TEST_IMAGE);
        configureRegistry(v1RegistryAddress, TENANT);
        verifyImageTags(imageName, TENANT, new String[] { DEFAULT_TAG, "latest-mock" });
    }

    @Test
    public void testListTagsFromV2Registry() throws Exception {
        String imageName = toFullImageName(v2RegistryAddress, TEST_IMAGE);
        configureRegistry(v2RegistryAddress, TENANT);
        verifyImageTags(imageName, TENANT, new String[] { DEFAULT_TAG });
    }

    @Test
    public void testListTagsFromDockerHub() throws Throwable {
        // Docker Hub list tags requests are expected to use the v2 endpoint,
        // otherwise we get fewer tags, in our case only "2.6" and "2.7".
        configureRegistry(defaultRegistryAddress, TENANT);
        String[] expectedTags = new String[] {  "2.6", "2.7", "3.1", "3.2", "3.3", "3.4" };
        verifyImageTags(TEST_IMAGE_DOCKER_HUB, TENANT, expectedTags);
    }

    @Test
    public void testListTagsFromDockerHubFullImageName() throws Throwable {
        configureRegistry(defaultRegistryAddress, TENANT);
        String[] expectedTags = new String[] {  "2.6", "2.7", "3.1", "3.2", "3.3", "3.4" };
        verifyImageTags(TEST_IMAGE_DOCKER_HUB_FULL_ADDRESS, TENANT, expectedTags);
    }

    @Test
    public void testFailWhenRegistryWithDifferentTenant() throws Exception {
        configureRegistry(v1RegistryAddress, "different-tenant");
        String imageName = toFullImageName(v1RegistryAddress, TEST_IMAGE);
        verifyListTagsFailure(imageName, TENANT);
    }

    @Test
    public void testListTagsNoTenant() throws Exception {
        String v1ImageName = toFullImageName(v1RegistryAddress, TEST_IMAGE);
        String v2ImageName = toFullImageName(v2RegistryAddress, TEST_IMAGE);
        configureRegistry(v1RegistryAddress, null);
        configureRegistry(v2RegistryAddress, null);
        verifyImageTags(v1ImageName, null, new String[] { DEFAULT_TAG, "latest-mock" });
        verifyImageTags(v2ImageName, null, new String[] { DEFAULT_TAG });
    }

    @Test
    public void testFailForNonexistentImage() throws Exception {
        String nonexistentImage = "nonexisting-image-admiral-test";

        configureRegistry(v1RegistryAddress, null);
        configureRegistry(v2RegistryAddress, null);

        String v1ImageName = toFullImageName(v1RegistryAddress, nonexistentImage);
        String v2ImageName = toFullImageName(v1RegistryAddress, nonexistentImage);

        verifyListTagsFailure(v1ImageName, null);
        verifyListTagsFailure(v2ImageName, null);
        // search in docker hub
        verifyListTagsFailure(nonexistentImage, null);
    }

    @Test
    public void testFailForNonexistentRegistry() throws Exception {
        String imageName = toFullImageName("nonexisting-registry.admiral.test", TEST_IMAGE);
        verifyListTagsFailure(imageName, TENANT);
    }

    private void verifyImageTags(String imageName, String tenant, String[] expectedTags)
            throws Exception {
        URI listTagsUri = UriUtils.buildUri(host, ContainerImageTagsService.SELF_LINK);
        listTagsUri = UriUtils.extendUriWithQuery(listTagsUri,
                RegistryAdapterService.SEARCH_QUERY_PROP_NAME, imageName);
        if (tenant != null) {
            listTagsUri = UriUtils.extendUriWithQuery(listTagsUri,
                    ContainerImageTagsService.TENANT_LINKS_PARAM_NAME, tenant);
        }

        HttpResponse search = SimpleHttpsClient.execute(HttpMethod.GET, listTagsUri.toString());
        assertEquals(HTTP_OK, search.statusCode);

        assertNotNull(search.responseBody);
        String[] response = Utils.fromJson(search.responseBody, String[].class);

        assertNotNull(response);
        assertThat(Arrays.asList(response), hasItems(expectedTags));
    }

    private void verifyListTagsFailure(String imageName, String tenant) throws Exception {
        URI listTagsUri = UriUtils.buildUri(host, ContainerImageTagsService.SELF_LINK);
        listTagsUri = UriUtils.extendUriWithQuery(listTagsUri,
                RegistryAdapterService.SEARCH_QUERY_PROP_NAME, imageName);
        if (tenant != null) {
            listTagsUri = UriUtils.extendUriWithQuery(listTagsUri,
                    ContainerImageTagsService.TENANT_LINKS_PARAM_NAME, tenant);
        }

        try {
            SimpleHttpsClient.execute(HttpMethod.GET, listTagsUri.toString());
            fail("Expected exception while listing image tags.");
        } catch (IllegalArgumentException e) {
        }
    }

    private String configureRegistry(String registryAddress, String tenant) throws Exception {
        RegistryState registryState = new RegistryState();
        registryState.address = registryAddress;
        registryState.name = UUID.randomUUID().toString();
        if (tenant != null) {
            registryState.tenantLinks = Collections.singletonList(tenant);
        }
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;

        RegistryHostSpec registryHostSpec = new RegistryHostSpec();
        registryHostSpec.hostState = registryState;
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

    private String toFullImageName(String registryAddress, String imageName) {
        String hostname = UriUtilsExtended.extractHostAndPort(registryAddress);
        return String.format("%s/%s", hostname, imageName);
    }
}
