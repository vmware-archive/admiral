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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.util.OperationUtil;
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

    private static final Logger logger = Logger.getLogger(TemplateSearchServiceIT.class.getName());

    private static RegistryState configuredRegistry;

    private List<RegistryState> registriesToEnable = new ArrayList<>();
    private List<RegistryState> registriesToDisable = new ArrayList<>();

    @BeforeClass
    public static void setUpRegistryInDefaultProject() throws Exception {
        configuredRegistry = configureRegistry(getTestRequiredProp("docker.registry.host.address"));
    }

    @Before
    public void init() {
        registriesToEnable = new ArrayList<>();
        registriesToDisable = new ArrayList<>();
    }

    @After
    public void cleanUp() throws Exception {
        enableRegistries(registriesToEnable);
        disableRegistries(registriesToDisable);
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

        final List<String> keyValues = new ArrayList<>(Arrays.asList(
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
        final List<String> keyValues = new ArrayList<>(Arrays.asList(
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

    @Test
    public void testSearchImagesInRegistryFromAnotherProjectYieldsNoResults() throws Exception {
        // the test registry is part of the default project. Let's create another one
        ProjectState createdProject = createProject();

        // assert the default registry is there
        RegistryState dockerHub = getDocument(RegistryService.DEFAULT_INSTANCE_LINK,
                RegistryState.class);
        assertNotNull(dockerHub);

        URI templateSearchUri = UriUtils.buildUri(new URI(getBaseUrl()),
                TemplateSearchService.SELF_LINK);

        // restrict search results to the test registry only
        // with a filter based on the name of the registry
        final List<String> keyValues = new ArrayList<String>(Arrays.asList(
                TemplateSearchService.IMAGES_ONLY_PARAM, Boolean.toString(true),
                ContainerImageService.REGISTRY_FILTER_QUERY_PARAM_NAME, REGISTRY_NAME,
                TemplateSearchService.QUERY_PARAM, "vmware"));

        templateSearchUri = UriUtils.extendUriWithQuery(templateSearchUri,
                keyValues.toArray(new String[keyValues.size()]));

        HashMap<String, String> headers = new HashMap<>();
        headers.put(OperationUtil.PROJECT_ADMIRAL_HEADER, createdProject.documentSelfLink);

        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.GET,
                templateSearchUri.toString(), null, headers, null);
        RegistrySearchResponse searchResponse = Utils.fromJson(httpResponse.responseBody,
                RegistrySearchResponse.class);

        // The test registry is defined in the context of the default project, i.e. it is not global
        // and is not part of this project, so we expect no results
        assertEquals(0, searchResponse.results.size());
    }

    @Test
    public void testSearchImagesWhenRegistriesAreDisabled() throws Exception {
        logger.info("Assert the default registry is there");
        RegistryState dockerHub = getDocument(RegistryService.DEFAULT_INSTANCE_LINK,
                RegistryState.class);
        dockerHub.name = dockerHub.address; // required name when updating a registry
        assertNotNull(dockerHub);

        logger.info("Assert the preconfigured registry is there");
        RegistryState configuredReg = getDocument(configuredRegistry.documentSelfLink,
                RegistryState.class);
        assertNotNull(configuredReg);

        List<RegistryState> disabledRegistries = disableRegistries(Arrays.asList(dockerHub, configuredReg));
        registriesToEnable.addAll(disabledRegistries);

        URI templateSearchUri = UriUtils.buildUri(new URI(getBaseUrl()),
                TemplateSearchService.SELF_LINK);

        final List<String> keyValues = new ArrayList<>(Arrays.asList(
                TemplateSearchService.IMAGES_ONLY_PARAM, Boolean.toString(true),
                TemplateSearchService.QUERY_PARAM, "vmware"));

        templateSearchUri = UriUtils.extendUriWithQuery(templateSearchUri,
                keyValues.toArray(new String[keyValues.size()]));

        logger.info("Search URI built: " + templateSearchUri);

        HashMap<String, String> headers = new HashMap<>();
        headers.put(OperationUtil.PROJECT_ADMIRAL_HEADER, ProjectService.DEFAULT_PROJECT_LINK);

        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.GET,
                templateSearchUri.toString(), null, headers, null);
        RegistrySearchResponse searchResponse = Utils.fromJson(httpResponse.responseBody,
                RegistrySearchResponse.class);

        assertEquals(0, searchResponse.results.size());
    }

    @Test
    public void testSearchImagesFromRegistriesWithSpecificPath() throws Exception {
        logger.info("Assert the default registry is there");
        RegistryState dockerHub = getDocument(RegistryService.DEFAULT_INSTANCE_LINK,
                RegistryState.class);
        dockerHub.name = dockerHub.address; // required name when updating a registry
        assertNotNull(dockerHub);

        logger.info("Assert the preconfigured registry is there");
        RegistryState configuredReg = getDocument(configuredRegistry.documentSelfLink,
                RegistryState.class);
        assertNotNull(configuredReg);

        List<RegistryState> disabledRegistries = disableRegistries(Arrays.asList(dockerHub, configuredReg));
        registriesToEnable.addAll(disabledRegistries);

        String address = getTestRequiredProp("docker.v2.registry.host.address");
        logger.info("Configuring registry. Registry address: " + address);
        logger.info("This registry contains: busybox, sample/busybox, vmware/bellevue");
        RegistryState v2Registry = configureRegistry(address);
        assertNotNull(v2Registry);
        assertFalse(v2Registry.disabled);
        registriesToDisable.add(v2Registry);

        URI templateSearchUri = UriUtils.buildUri(new URI(getBaseUrl()),
                TemplateSearchService.SELF_LINK);

        logger.info("Test that the busybox image will be retrieved twice.");
        final List<String> keyValues = new ArrayList<>(Arrays.asList(
                TemplateSearchService.IMAGES_ONLY_PARAM, Boolean.toString(true),
                TemplateSearchService.QUERY_PARAM, "busybox"));

        templateSearchUri = UriUtils.extendUriWithQuery(templateSearchUri,
                keyValues.toArray(new String[keyValues.size()]));

        logger.info("Search URI built: " + templateSearchUri);
        HashMap<String, String> headers = new HashMap<>();
        headers.put(OperationUtil.PROJECT_ADMIRAL_HEADER, ProjectService.DEFAULT_PROJECT_LINK);

        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.GET,
                templateSearchUri.toString(), null, headers, null);
        RegistrySearchResponse searchResponse = Utils.fromJson(httpResponse.responseBody,
                RegistrySearchResponse.class);

        assertEquals(2, searchResponse.results.size());

        disableRegistries(Arrays.asList(v2Registry));

        logger.info("Test that the busybox image will be retrieved once because of the additional path "
                + "in the registry.");
        address = getTestRequiredProp("docker.v2.registry.host.address") + "/sample";
        logger.info("Configure registry with path. Registry address: " + address);
        logger.info("This registry contains: busybox, sample/busybox, vmware/bellevue");
        RegistryState registryWithPath = configureRegistry(address);
        assertNotNull(registryWithPath);
        assertFalse(registryWithPath.disabled);
        registriesToDisable.add(registryWithPath);

        httpResponse = SimpleHttpsClient.execute(HttpMethod.GET,
                templateSearchUri.toString(), null, headers, null);
        searchResponse = Utils.fromJson(httpResponse.responseBody,
                RegistrySearchResponse.class);

        assertEquals(1, searchResponse.results.size());
    }

    private static RegistryState configureRegistry(String address) throws Exception {
        RegistryState registryState = new RegistryState();
        registryState.address = address;
        registryState.name = REGISTRY_NAME;
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;
        registryState.disabled = Boolean.FALSE;
        registryState.tenantLinks = Collections.singletonList(ProjectService.DEFAULT_PROJECT_LINK);

        RegistryHostSpec registryHostSpec = new RegistryHostSpec();
        registryHostSpec.hostState = registryState;
        registryHostSpec.acceptCertificate = true;

        return createRegistry(registryHostSpec);
    }

    private static RegistryState createRegistry(RegistryHostSpec hostSpec) throws Exception {
        HttpResponse httpResponse = updateRegistry(hostSpec);

        List<String> headers = httpResponse.headers.get(Operation.LOCATION_HEADER);
        String documentSelfLink = headers.get(0);

        RegistryState registry = getDocument(documentSelfLink, RegistryState.class);
        cleanUpAfterClass(registry);
        return registry;
    }

    private static List<RegistryState> disableRegistries(List<RegistryState> registries) throws Exception {
        ArrayList<RegistryState> disabledRegistries = new ArrayList<>();
        if (registries != null) {
            for (RegistryState r : registries) {
                logger.info("Disable registry: " + r.address);
                r.disabled = true;
                r = updateRegistry(r);
                assertTrue(r.disabled);
                disabledRegistries.add(r);
            }
        }

        return disabledRegistries;
    }

    private static List<RegistryState> enableRegistries(List<RegistryState> registries) throws Exception {
        ArrayList<RegistryState> enabledRegistries = new ArrayList<>();
        if (registries != null) {
            for (RegistryState r : registries) {
                logger.info("Enable registry: " + r.address);
                r.disabled = false;
                r = updateRegistry(r);
                assertFalse(r.disabled);
                enabledRegistries.add(r);
            }
        }

        return enabledRegistries;
    }

    private static HttpResponse updateRegistry(RegistryHostSpec hostSpec) throws Exception {
        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.PUT,
                getBaseUrl() + buildServiceUri(RegistryHostConfigService.SELF_LINK),
                Utils.toJson(hostSpec));

        if (HttpURLConnection.HTTP_NO_CONTENT != httpResponse.statusCode) {
            String error = String.format("Add registry host failed with status code: %s",
                    httpResponse.statusCode);
            logger.log(Level.SEVERE, error);
            throw new IllegalArgumentException(error);
        }

        return httpResponse;
    }

    private static RegistryState updateRegistry(RegistryState reg) throws Exception {
        RegistryHostSpec registryHostSpec = new RegistryHostSpec();
        registryHostSpec.hostState = reg;

        updateRegistry(registryHostSpec);

        reg = getDocument(reg.documentSelfLink,
                RegistryState.class);
        assertNotNull(reg);

        return reg;
    }

    private ProjectState createProject() throws Exception {
        ProjectState project = new ProjectState();
        project.name = UUID.randomUUID().toString();

        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.POST,
                getBaseUrl() + buildServiceUri(ProjectFactoryService.SELF_LINK),
                Utils.toJson(project));

        if (HttpURLConnection.HTTP_OK != httpResponse.statusCode) {
            String error = String.format("Create project failed with status code: %s",
                    httpResponse.statusCode);
            logger.log(Level.SEVERE, error);
            throw new IllegalArgumentException(error);
        }

        ProjectState result;
        try {
            result = Utils.fromJson(httpResponse.responseBody, ProjectState.class);
        } catch (Throwable e) {
            String error = String.format(
                    "Create project completed but response body in non-parsable: %s",
                    Utils.toString(e));
            logger.log(Level.SEVERE, error, e);
            throw new IllegalArgumentException(error, e);
        }

        String documentSelfLink = result.documentSelfLink;
        cleanUpAfter(getDocument(documentSelfLink, RegistryState.class));

        return result;
    }

}
