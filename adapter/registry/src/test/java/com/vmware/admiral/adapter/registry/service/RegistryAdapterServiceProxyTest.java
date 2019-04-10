/*
 * Copyright (c) 2016-2019 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.registry.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;

import static com.vmware.admiral.adapter.registry.service.RegistryAdapterService.REGISTRY_NO_PROXY_LIST_PARAM_NAME;
import static com.vmware.admiral.adapter.registry.service.RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE;
import static com.vmware.admiral.adapter.registry.service.RegistryAdapterService.REGISTRY_PROXY_PARAM_NAME;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ImageOperationType;
import com.vmware.admiral.adapter.common.service.mock.MockTaskFactoryService;
import com.vmware.admiral.adapter.registry.mock.BaseMockRegistryTestCase;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitRegistryAdapterServiceConfig;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.http.netty.NettyHttpServiceClient;

/**
 * Test registry adapter service
 */
public class RegistryAdapterServiceProxyTest extends BaseMockRegistryTestCase {

    private final String proxyAddress = "http://myproxy:80";
    private final String noProxyList = "registry1.com, registry2.com:1234";

    @Mock
    private ServiceClient serviceClientProxy;
    @Mock
    private ServiceClient serviceClientNoProxy;

    @Before
    public void startServices() throws Throwable {
        HostInitTestDcpServicesConfig.startServices(host);
        HostInitCommonServiceConfig.startServices(host);

        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockTaskFactoryService.SELF_LINK)),
                new MockTaskFactoryService());
        waitForServiceAvailability(ConfigurationFactoryService.SELF_LINK);
    }

    @Test
    public void testNoProxyNoList() throws Throwable {
        setProperty(REGISTRY_PROXY_PARAM_NAME, REGISTRY_PROXY_NULL_VALUE);
        setProperty(REGISTRY_NO_PROXY_LIST_PARAM_NAME, REGISTRY_PROXY_NULL_VALUE);
        RegistryAdapterService registryAdapterService = (RegistryAdapterService) host
                .startServiceAndWait(RegistryAdapterService.class,
                        RegistryAdapterService.SELF_LINK);
        assertClients(registryAdapterService, REGISTRY_PROXY_NULL_VALUE, REGISTRY_PROXY_NULL_VALUE);
    }

    @Test
    public void testWithProxyNoList() throws Throwable {
        setProperty(REGISTRY_PROXY_PARAM_NAME, proxyAddress);
        setProperty(REGISTRY_NO_PROXY_LIST_PARAM_NAME, REGISTRY_PROXY_NULL_VALUE);
        RegistryAdapterService registryAdapterService = (RegistryAdapterService) host
                .startServiceAndWait(RegistryAdapterService.class,
                        RegistryAdapterService.SELF_LINK);

        assertClients(registryAdapterService, proxyAddress, REGISTRY_PROXY_NULL_VALUE);
    }

    @Test
    public void testWithBadProxyNoList() throws Throwable {
        setProperty(REGISTRY_PROXY_PARAM_NAME, "/foo bar");
        setProperty(REGISTRY_NO_PROXY_LIST_PARAM_NAME, REGISTRY_PROXY_NULL_VALUE);
        RegistryAdapterService registryAdapterService = (RegistryAdapterService) host
                .startServiceAndWait(RegistryAdapterService.class,
                        RegistryAdapterService.SELF_LINK);

        NettyHttpServiceClient serviceClientProxy = getField(registryAdapterService,
                "serviceClientProxy");
        assertNull("When bad proxy is set the serviceClientProxy should be null.",
                serviceClientProxy);
    }

    @Test
    public void testNoProxyWithList() throws Throwable {
        setProperty(REGISTRY_PROXY_PARAM_NAME, REGISTRY_PROXY_NULL_VALUE);
        setProperty(REGISTRY_NO_PROXY_LIST_PARAM_NAME, noProxyList);
        RegistryAdapterService registryAdapterService = (RegistryAdapterService) host
                .startServiceAndWait(RegistryAdapterService.class,
                        RegistryAdapterService.SELF_LINK);
        assertClients(registryAdapterService, REGISTRY_PROXY_NULL_VALUE,
                noProxyList);
    }

    @Test
    public void testWithProxyWithList() throws Throwable {
        setProperty(REGISTRY_PROXY_PARAM_NAME, proxyAddress);
        setProperty(REGISTRY_NO_PROXY_LIST_PARAM_NAME, noProxyList);
        RegistryAdapterService registryAdapterService = (RegistryAdapterService) host
                .startServiceAndWait(RegistryAdapterService.class,
                        RegistryAdapterService.SELF_LINK);

        assertClients(registryAdapterService, proxyAddress, noProxyList);
    }

    @Test
    public void testInitNoProxyNoList() throws Throwable {
        HostInitRegistryAdapterServiceConfig.startServices(host);
        ConfigurationState configurationState = getDocument(ConfigurationState.class,
                UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                        REGISTRY_PROXY_PARAM_NAME));
        assertEquals(REGISTRY_PROXY_PARAM_NAME + " should be " + REGISTRY_PROXY_NULL_VALUE,
                REGISTRY_PROXY_NULL_VALUE, configurationState.value);
        configurationState = getDocument(ConfigurationState.class,
                UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                        REGISTRY_NO_PROXY_LIST_PARAM_NAME));
        assertEquals(REGISTRY_NO_PROXY_LIST_PARAM_NAME + " should be "
                        + REGISTRY_PROXY_NULL_VALUE, REGISTRY_PROXY_NULL_VALUE,
                configurationState.value);
    }

    @Test
    public void testInitWithProxyNoList() throws Throwable {
        setProperty(REGISTRY_PROXY_PARAM_NAME, proxyAddress);
        HostInitRegistryAdapterServiceConfig.startServices(host);
        ConfigurationState configurationState = getDocument(ConfigurationState.class,
                UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                        REGISTRY_PROXY_PARAM_NAME));
        assertEquals(REGISTRY_PROXY_PARAM_NAME + " should be " + proxyAddress,
                proxyAddress, configurationState.value);
        configurationState = getDocument(ConfigurationState.class,
                UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                        REGISTRY_NO_PROXY_LIST_PARAM_NAME));
        assertEquals(REGISTRY_NO_PROXY_LIST_PARAM_NAME + " should be " + REGISTRY_PROXY_NULL_VALUE,
                REGISTRY_PROXY_NULL_VALUE, configurationState.value);
    }

    @Test
    public void testInitNoProxyWithList() throws Throwable {
        setProperty(REGISTRY_NO_PROXY_LIST_PARAM_NAME, noProxyList);
        HostInitRegistryAdapterServiceConfig.startServices(host);
        ConfigurationState configurationState = getDocument(ConfigurationState.class,
                UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                        REGISTRY_PROXY_PARAM_NAME));
        assertEquals(REGISTRY_PROXY_PARAM_NAME + " should be " + REGISTRY_PROXY_NULL_VALUE,
                REGISTRY_PROXY_NULL_VALUE, configurationState.value);
        configurationState = getDocument(ConfigurationState.class,
                UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                        REGISTRY_NO_PROXY_LIST_PARAM_NAME));
        assertEquals(REGISTRY_NO_PROXY_LIST_PARAM_NAME + " should be " + noProxyList,
                noProxyList, configurationState.value);
    }

    @Test
    public void testInitWithProxyWithList() throws Throwable {
        setProperty(REGISTRY_PROXY_PARAM_NAME, proxyAddress);
        setProperty(REGISTRY_NO_PROXY_LIST_PARAM_NAME, noProxyList);
        HostInitRegistryAdapterServiceConfig.startServices(host);
        ConfigurationState configurationState = getDocument(ConfigurationState.class,
                UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                        REGISTRY_PROXY_PARAM_NAME));
        assertEquals(REGISTRY_PROXY_PARAM_NAME + " should be " + proxyAddress, proxyAddress,
                configurationState.value);
        configurationState = getDocument(ConfigurationState.class,
                UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                        REGISTRY_NO_PROXY_LIST_PARAM_NAME));
        assertEquals(REGISTRY_NO_PROXY_LIST_PARAM_NAME + " should be " + noProxyList, noProxyList,
                configurationState.value);
    }

    /**
     * Tests that when there's a defined proxy all requests except for the ones in the
     * registry.no.proxy.list are routed through the proxied client.
     */
    @Test
    public void testClientRouting() throws Throwable {
        setProperty(REGISTRY_PROXY_PARAM_NAME, proxyAddress);
        setProperty(REGISTRY_NO_PROXY_LIST_PARAM_NAME, noProxyList);
        RegistryAdapterService registryAdapterService = (RegistryAdapterService) host
                .startServiceAndWait(RegistryAdapterService.class,
                        RegistryAdapterService.SELF_LINK);

        MockitoAnnotations.initMocks(this);
        setField(registryAdapterService, "serviceClientProxy", serviceClientProxy);
        setField(registryAdapterService, "serviceClientNoProxy", serviceClientNoProxy);

        // should go through the proxy
        registryAdapterService.handlePatch(operationTo("http://abc.com"));
        assertClientCalls(1, 0);

        // should go directly to the host, registry1.com is in the list
        registryAdapterService.handlePatch(operationTo("http://registry1.com"));
        assertClientCalls(0, 1);

        // should go through the proxy, registry2.com is not in the list - mind the port!
        registryAdapterService.handlePatch(operationTo("http://registry2.com/"));
        assertClientCalls(1, 0);

        // should go through the proxy, registry2.com is not in the list - mind the port!
        registryAdapterService.handlePatch(operationTo("http://registry2.com:8000/"));
        assertClientCalls(1, 0);

        // should go directly to the host, registry2.com:1234 is in the list
        registryAdapterService.handlePatch(operationTo("http://registry2.com:1234/home"));
        assertClientCalls(0, 1);
    }

    @Test
    public void testDummyCoverageStuff() throws Throwable {
        RegistryAdapterService registryAdapterService = (RegistryAdapterService) host
                .startServiceAndWait(RegistryAdapterService.class,
                        RegistryAdapterService.SELF_LINK);

        MockitoAnnotations.initMocks(this);
        setField(registryAdapterService, "serviceClientProxy", serviceClientProxy);
        setField(registryAdapterService, "serviceClientNoProxy", serviceClientNoProxy);

        Semaphore s = new Semaphore(0);
        registryAdapterService.handlePatch(operationTo("some-uri")
                .setCompletion((op, ex) -> {
                    if (ex == null) {
                        fail("exception expected");
                    }
                    assertTrue(ex instanceof IllegalArgumentException);
                    s.release();
                }));

        Operation operation = operationTo("some-uri");
        ((AdapterRequest) operation.getBodyRaw()).operationTypeId = ImageOperationType.INSPECT.id;
        registryAdapterService.handlePatch(operation
                .setCompletion((op, ex) -> {
                    if (ex == null) {
                        fail("exception expected");
                    }
                    assertTrue(ex instanceof IllegalArgumentException);
                    assertTrue(ex.getMessage().contains("Unexpected request type"));
                    s.release();
                }));

        boolean success = s.tryAcquire(2, TimeUnit.SECONDS);
        assertTrue("handlePatch did not failed within the given time", success);

        registryAdapterService.handleStop(Operation.createDelete(new URI("some-uri")));
    }

    private Operation operationTo(String proxyAddress) throws URISyntaxException {
        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = ImageOperationType.PING.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = new URI(proxyAddress);
        request.customProperties = new HashMap<>();

        return Operation.createPatch(null)
                .setBodyNoCloning(request);
    }

    private void setProperty(String key, String value) throws Throwable {
        ConfigurationState configurationState = new ConfigurationState();
        configurationState.key = key;
        configurationState.value = value;
        configurationState.documentSelfLink = key;
        doPost(configurationState, ConfigurationFactoryService.SELF_LINK);
    }

    private void assertClients(RegistryAdapterService ras, String proxyAddress,
            String noProxyListProp) throws IllegalArgumentException, IllegalAccessException,
            NoSuchFieldException, SecurityException, URISyntaxException {

        NettyHttpServiceClient clientProxy = getField(ras, "serviceClientProxy");
        if (proxyAddress.equals(REGISTRY_PROXY_NULL_VALUE)) {
            assertNull("When no proxy is set the serviceClientProxy should be null.", clientProxy);
        } else {
            assertNotNull("When proxy is set the serviceClientProxy should not be null.",
                    clientProxy);
            URI clientProxyURI = getField(clientProxy, "httpProxy");
            assertNotNull("When proxy is set the proxy URI of serviceClientProxy should be null.",
                    clientProxyURI);
            assertEquals(
                    "The proxy URI NettyHttpServiceClient should mach the one in the properties.",
                    new URI(proxyAddress), clientProxyURI);
        }

        NettyHttpServiceClient clientNoProxy = getField(ras, "serviceClientNoProxy");
        assertNotNull("When no proxy is set the serviceClientNoProxy should not be null.",
                clientNoProxy);
        URI serviceClientNoProxyURI = getField(clientNoProxy, "httpProxy");
        assertNull("The proxy URI of serviceClientNoProxy should be null.",
                serviceClientNoProxyURI);

        Set<String> clientNoProxyList = getField(ras, "serviceClientNoProxyList");
        if (noProxyListProp.equals(REGISTRY_PROXY_NULL_VALUE)
                || proxyAddress.equals(REGISTRY_PROXY_NULL_VALUE)) {
            assertTrue("The serviceClientNoProxyList should be empty.",
                    clientNoProxyList.isEmpty());
        } else {
            assertEquals("The serviceClientNoProxyList should not be empty.", 2,
                    clientNoProxyList.size());
            clientNoProxyList.forEach(s -> {
                assertTrue("All registries in the list property should be in the exception list.",
                        noProxyListProp.contains(s));
            });
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Object instance, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(instance);
    }

    @SuppressWarnings("unchecked")
    private void setField(Object instance, String fieldName, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private void assertClientCalls(int proxiedClient, int noProxiedClient) {
        Mockito.verify(serviceClientProxy, Mockito.times(proxiedClient)).send(any());
        Mockito.verify(serviceClientNoProxy, Mockito.times(noProxiedClient)).send(any());
        Mockito.clearInvocations(serviceClientProxy, serviceClientNoProxy);
    }

}
