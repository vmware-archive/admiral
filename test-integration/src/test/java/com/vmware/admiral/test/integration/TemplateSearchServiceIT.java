/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.RegistryHostConfigService;
import com.vmware.admiral.compute.RegistryHostConfigService.RegistryHostSpec;
import com.vmware.admiral.compute.container.TemplateSearchService;
import com.vmware.admiral.image.service.ContainerImageService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class TemplateSearchServiceIT extends BaseIntegrationSupportIT {

    private static final String REGISTRY_NAME = "test-registry-name";

    @BeforeClass
    public static void setUpRegistry() throws Exception {
        configureRegistry(getTestRequiredProp("docker.registry.host.address"));
    }

    @Test
    public void testSearchImagesBasedOnRegistryAddress() throws Exception {
        // assert the default registry is there
        RegistryState dockerHub = getDocument(RegistryService.DEFAULT_INSTANCE_LINK,
                RegistryState.class);
        assertNotNull(dockerHub);

        String searchTerm = UriUtilsExtended
                .extractHostAndPort(getTestRequiredProp("docker.registry.host.address"))
                + "/vmware/bellevue";

        URI templateSearchUri = UriUtils.buildUri(new URI(getBaseUrl()),
                TemplateSearchService.SELF_LINK);

        final List<String> keyValues = new ArrayList<String>(Arrays.asList(
                TemplateSearchService.IMAGES_ONLY_PARAM, Boolean.toString(true),
                TemplateSearchService.QUERY_PARAM, searchTerm));

        templateSearchUri = UriUtils.extendUriWithQuery(templateSearchUri,
                keyValues.toArray(new String[keyValues.size()]));

        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.GET,
                templateSearchUri.toString());
        RegistrySearchResponse searchResponse = Utils.fromJson(httpResponse.responseBody,
                RegistrySearchResponse.class);
        assertEquals(2, searchResponse.results.size());
    }

    @Test
    public void testSearchImagesWithRegistryFilter() throws Exception {
        // assert the default registry is there
        RegistryState dockerHub = getDocument(RegistryService.DEFAULT_INSTANCE_LINK,
                RegistryState.class);
        assertNotNull(dockerHub);

        URI templateSearchUri = UriUtils.buildUri(new URI(getBaseUrl()),
                TemplateSearchService.SELF_LINK);

        // exclude results from the default registry
        final List<String> keyValues = new ArrayList<String>(Arrays.asList(
                TemplateSearchService.IMAGES_ONLY_PARAM, Boolean.toString(true),
                ContainerImageService.REGISTRY_FILTER_QUERY_PARAM_NAME, REGISTRY_NAME,
                TemplateSearchService.QUERY_PARAM, "vmware"));

        templateSearchUri = UriUtils.extendUriWithQuery(templateSearchUri,
                keyValues.toArray(new String[keyValues.size()]));

        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.GET,
                templateSearchUri.toString());
        RegistrySearchResponse searchResponse = Utils.fromJson(httpResponse.responseBody,
                RegistrySearchResponse.class);
        assertEquals(2, searchResponse.results.size());
    }

    private static void configureRegistry(String address) throws Exception {
        RegistryState registryState = new RegistryState();
        registryState.address = address;
        registryState.name = REGISTRY_NAME;
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;

        RegistryHostSpec registryHostSpec = new RegistryHostSpec();
        registryHostSpec.hostState = registryState;
        registryHostSpec.acceptCertificate = true;

        createOrUpdateRegistry(registryHostSpec);
    }

    private static void createOrUpdateRegistry(RegistryHostSpec hostSpec) throws Exception {
        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.PUT,
                getBaseUrl() + buildServiceUri(RegistryHostConfigService.SELF_LINK),
                Utils.toJson(hostSpec));

        if (HttpURLConnection.HTTP_NO_CONTENT != httpResponse.statusCode) {
            throw new IllegalArgumentException("Add registry host failed with status code: "
                    + httpResponse.statusCode);
        }

        List<String> headers = httpResponse.headers.get(Operation.LOCATION_HEADER);
        String documentSelfLink = headers.get(0);
        cleanUpAfterClass(getDocument(documentSelfLink, RegistryState.class));
    }
}
