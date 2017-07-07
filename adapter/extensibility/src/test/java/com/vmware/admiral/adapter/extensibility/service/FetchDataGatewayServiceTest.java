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

package com.vmware.admiral.adapter.extensibility.service;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapters.registry.FetchDataRequest;
import com.vmware.photon.controller.model.adapters.registry.FetchDataRequest.RequestType;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestRequestSender;

public class FetchDataGatewayServiceTest extends BaseTestCase {
    private final Logger logger = Logger.getLogger(getClass().getName());
    private static final String TEST_ENDPOINT_TYPE =
            FetchDataGatewayServiceTest.class.getSimpleName();

    private static final String HOST = "https://localhost:8910" + FetchDataGatewayService.SELF_LINK;
    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(10));

        PhotonModelServices.startServices(this.host);
        PhotonModelMetricServices.startServices(this.host);
        PhotonModelAdaptersRegistryAdapters.startServices(this.host);

        this.host.startService(new FetchDataGatewayServiceTest.TestEchoService());
        this.host.startService(new FetchDataGatewayService());

        this.host.setTimeoutSeconds(300);

        this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
        this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);

        this.host.waitForServiceAvailable(
                FetchDataGatewayServiceTest.TestEchoService.SELF_LINK,
                FetchDataGatewayService.SELF_LINK);

        this.sender = new TestRequestSender(this.host);
    }

    @Test
    public void testGetByEndpointTypeWithHost() {
        testGetByXXX(true, false,
                (ept, d) -> {
                    FetchDataRequest request = new FetchDataRequest();
                    request.entityId = ept;
                    request.requestType = RequestType.EndpointType;
                    request.data = d;
                    return request;
                });
    }

    @Test
    public void testGetByEndpointType_neg() {
        testGetByXXX(true, true,
                (ept, d) -> {
                    FetchDataRequest request = new FetchDataRequest();
                    request.entityId = ept;
                    request.requestType = RequestType.EndpointType;
                    request.data = d;
                    return request;
                });
    }

    @Test
    public void testGetByResource() {
        testGetByXXX(false, false,
                (ept, d) -> {
                    EndpointState endpointState = registerEndpoint(ept);
                    ComputeState computeState = registerComputeState(endpointState);
                    FetchDataRequest request = new FetchDataRequest();
                    request.entityId = computeState.documentSelfLink;
                    request.requestType = RequestType.ResourceOperation;
                    request.data = d;
                    return request;
                });
    }

    @Test
    public void testGetByEndpoint() {
        testGetByXXX(false, false,
                (ept, d) -> {
                    EndpointState endpointState = registerEndpoint(ept);
                    FetchDataRequest request = new FetchDataRequest();
                    request.entityId = endpointState.documentSelfLink;
                    request.requestType = RequestType.Endpoint;
                    request.data = d;
                    return request;
                });
    }

    private void testGetByXXX(boolean withHost, boolean withWrongPath,
            BiFunction<String, Object, FetchDataRequest> config) {

        registerConfig(TEST_ENDPOINT_TYPE, withHost);

        String verificationToken = UUID.randomUUID().toString();
        this.logger.info("verificationToken: " + verificationToken);

        String hostUri = this.host.getUri().toASCIIString();

        FetchDataRequest request = config.apply(TEST_ENDPOINT_TYPE, verificationToken);

        String location = hostUri
                + UriUtils.buildUriPath(
                FetchDataGatewayService.SELF_LINK,
                withWrongPath ? "wrong" : FetchDataGatewayServiceTest.TestEchoService.SELF_LINK);
        ;
        this.logger.info("location: " + location);
        URI uri = URI.create(location);

        try {
            Operation response = this.sender
                    .sendAndWait(Operation.createPatch(uri).setBody(request));
            Assert.assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
            Object bodyRaw = response.getBodyRaw();
            this.logger.info("bodyRaw: " + bodyRaw);
            Assert.assertTrue(bodyRaw instanceof String);
            Assert.assertEquals(verificationToken, bodyRaw);
        } catch (RuntimeException rte) {
            Throwable[] suppressed = rte.getSuppressed();
            if (suppressed != null
                    && suppressed.length == 1
                    && (suppressed[0] instanceof IllegalArgumentException)
                    && suppressed[0].getMessage()
                    .contains(UriUtils.buildUriPath(UriPaths.ADAPTER, TEST_ENDPOINT_TYPE))) {
                return;
            }
            throw rte;
        }
    }

    /**
     * verify that https://localhost:8910/adapter-extensibility/fetch-data/step1/step2?k1=v1&k2=v3
     * return /step1/step2?k1=v1&k2=v3
     * @throws Exception
     */
    @Test
    public void testExtractServicePath() throws Exception {
        String expected = "/step1/step2?k1=v1&k2=v3#fragment";
        URI uri = URI.create(HOST + expected);

        Method extractServicePath = FetchDataGatewayService.class
                .getDeclaredMethod("extractServicePath", URI.class);
        extractServicePath.setAccessible(true);
        String servicePath = (String) extractServicePath.invoke(null, uri);

        Assert.assertEquals(expected, servicePath);
    }

    public static class TestEchoService extends StatelessService {

        public static final String SELF_LINK =
                UriPaths.ADAPTER + "/" + TEST_ENDPOINT_TYPE + "/test-backend-service";

        @Override
        public void handlePatch(Operation get) {
            FetchDataRequest fetchDataRequest = get.getBody(FetchDataRequest.class);
            get.setBody(fetchDataRequest.data).complete();
        }
    }

    private PhotonModelAdapterConfig registerConfig(String id, boolean withHost) {
        PhotonModelAdapterConfig config = getPhotonModelAdapterConfig(id, withHost);

        this.logger.info("register: " + config);

        return this.sender.sendPostAndWait(
                UriUtils.buildUri(this.host.getUri(),
                        PhotonModelAdaptersRegistryService.FACTORY_LINK),
                config,
                PhotonModelAdapterConfig.class);
    }

    private EndpointState registerEndpoint(String endpointType) {
        EndpointState endpointState = new EndpointState();
        endpointState.endpointType = endpointType;
        endpointState.name = endpointType;

        return this.sender.sendPostAndWait(
                UriUtils.buildUri(this.host.getUri(),
                        EndpointService.FACTORY_LINK),
                endpointState,
                EndpointState.class);
    }

    private ComputeState registerComputeState(
            EndpointState endpointState) {
        ComputeState computeState = new ComputeState();
        computeState.descriptionLink = "descriptionLink";
        computeState.endpointLink = endpointState.documentSelfLink;

        return this.sender.sendPostAndWait(
                UriUtils.buildUri(this.host.getUri(),
                        ComputeService.FACTORY_LINK),
                computeState,
                ComputeState.class);
    }

    private PhotonModelAdapterConfig getPhotonModelAdapterConfig(String id, boolean withHost) {
        PhotonModelAdapterConfig config = new PhotonModelAdapterConfig();
        config.id = id;
        config.name = id;
        config.documentSelfLink = config.id;
        Map<String, String> customProperties = new HashMap<>();
        config.customProperties = customProperties;
        Map<String, String> endpoints = new HashMap<>();
        String link = AdapterTypePath.ENDPOINT_CONFIG_ADAPTER.adapterLink(id);
        if (withHost) {
            link = AdapterUriUtil.buildAdapterUri(this.host, link).toASCIIString();
        }
        endpoints.put(AdapterTypePath.ENDPOINT_CONFIG_ADAPTER.key, link);
        config.adapterEndpoints = endpoints;
        return config;
    }
}