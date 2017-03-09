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

package com.vmware.admiral.adapter.registry.service;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.service.mock.MockTaskFactoryService;
import com.vmware.admiral.adapter.registry.mock.BaseMockRegistryTestCase;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitRegistryAdapterServiceConfig;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.http.netty.NettyHttpServiceClient;

/**
 * Test registry adapter service
 */
public class RegistryAdapterServiceProxyTest extends BaseMockRegistryTestCase {
    final String proxyAddress = "http://myproxy:80";
    final String noProxyList = "regitry1.com, registry2.com";

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
        setProperty(RegistryAdapterService.REGITRY_PROXY_PARAM_NAME, RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE);
        setProperty(RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME, RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE);
        RegistryAdapterService registryAdapterService = (RegistryAdapterService) host
                .startServiceAndWait(RegistryAdapterService.class,
                        RegistryAdapterService.SELF_LINK);
        assertClients(registryAdapterService, RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE, RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE);
    }

    @Test
    public void testWithProxyNoList() throws Throwable {
        setProperty(RegistryAdapterService.REGITRY_PROXY_PARAM_NAME, proxyAddress);
        setProperty(RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME, RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE);
        RegistryAdapterService registryAdapterService = (RegistryAdapterService) host
                .startServiceAndWait(RegistryAdapterService.class,
                        RegistryAdapterService.SELF_LINK);

        assertClients(registryAdapterService, proxyAddress, RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE);
    }

    @Test
    public void testNoProxyWithList() throws Throwable {
        setProperty(RegistryAdapterService.REGITRY_PROXY_PARAM_NAME, RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE);
        setProperty(RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME, noProxyList);
        RegistryAdapterService registryAdapterService = (RegistryAdapterService) host
                .startServiceAndWait(RegistryAdapterService.class,
                        RegistryAdapterService.SELF_LINK);
        assertClients(registryAdapterService, RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE, noProxyList);
    }

    @Test
    public void testWithProxyWithList() throws Throwable {
        setProperty(RegistryAdapterService.REGITRY_PROXY_PARAM_NAME, proxyAddress);
        setProperty(RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME, noProxyList);
        RegistryAdapterService registryAdapterService = (RegistryAdapterService) host
                .startServiceAndWait(RegistryAdapterService.class,
                        RegistryAdapterService.SELF_LINK);

        assertClients(registryAdapterService, proxyAddress, noProxyList);
    }

    @Test
    public void testInitNoProxyNoList() throws Throwable {
        HostInitRegistryAdapterServiceConfig.startServices(host);
        ConfigurationState configurationState = getDocument(ConfigurationState.class, UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK, RegistryAdapterService.REGITRY_PROXY_PARAM_NAME));
        assertEquals(RegistryAdapterService.REGITRY_PROXY_PARAM_NAME + " should be " + RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE, RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE, configurationState.value);
        configurationState = getDocument(ConfigurationState.class, UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK, RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME));
        assertEquals(RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME + " should be " + RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE, RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE, configurationState.value);
    }

    @Test
    public void testInitWithProxyNoList() throws Throwable {
        setProperty(RegistryAdapterService.REGITRY_PROXY_PARAM_NAME, proxyAddress);
        HostInitRegistryAdapterServiceConfig.startServices(host);
        ConfigurationState configurationState = getDocument(ConfigurationState.class, UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK, RegistryAdapterService.REGITRY_PROXY_PARAM_NAME));
        assertEquals(RegistryAdapterService.REGITRY_PROXY_PARAM_NAME + " should be " + proxyAddress, proxyAddress, configurationState.value);
        configurationState = getDocument(ConfigurationState.class, UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK, RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME));
        assertEquals(RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME + " should be " + RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE, RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE, configurationState.value);
    }

    @Test
    public void testInitNoProxyWithList() throws Throwable {
        setProperty(RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME, noProxyList);
        HostInitRegistryAdapterServiceConfig.startServices(host);
        ConfigurationState configurationState = getDocument(ConfigurationState.class, UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK, RegistryAdapterService.REGITRY_PROXY_PARAM_NAME));
        assertEquals(RegistryAdapterService.REGITRY_PROXY_PARAM_NAME + " should be " + RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE, RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE, configurationState.value);
        configurationState = getDocument(ConfigurationState.class, UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK, RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME));
        assertEquals(RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME + " should be " + noProxyList, noProxyList, configurationState.value);
    }

    @Test
    public void testInitWithProxyWithList() throws Throwable {
        setProperty(RegistryAdapterService.REGITRY_PROXY_PARAM_NAME, proxyAddress);
        setProperty(RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME, noProxyList);
        HostInitRegistryAdapterServiceConfig.startServices(host);
        ConfigurationState configurationState = getDocument(ConfigurationState.class, UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK, RegistryAdapterService.REGITRY_PROXY_PARAM_NAME));
        assertEquals(RegistryAdapterService.REGITRY_PROXY_PARAM_NAME + " should be " + proxyAddress, proxyAddress, configurationState.value);
        configurationState = getDocument(ConfigurationState.class, UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK, RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME));
        assertEquals(RegistryAdapterService.REGITRY_NO_PROXY_LIST_PARAM_NAME + " should be " + noProxyList, noProxyList, configurationState.value);
    }

    private void setProperty(String key, String value) throws Throwable {
        ConfigurationState configurationState = new ConfigurationState();
        configurationState.key = key;
        configurationState.value = value;
        configurationState.documentSelfLink = key;
        doPost(configurationState, ConfigurationFactoryService.SELF_LINK);
    }

    private void assertClients(RegistryAdapterService registryAdapterService, String proxyAddress,
            String noProxyListProp) throws IllegalArgumentException, IllegalAccessException,
                    NoSuchFieldException, SecurityException, URISyntaxException {

        Field field = RegistryAdapterService.class.getDeclaredField("serviceClientProxy");
        field.setAccessible(true);
        NettyHttpServiceClient serviceClientProxy = (NettyHttpServiceClient) field
                .get(registryAdapterService);
        if (proxyAddress.equals(RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE)) {
            assertNull("When no proxy is set the serviceClientProxy should be null.",
                    serviceClientProxy);
        } else {
            assertNotNull("When proxy is set the serviceClientProxy should not be null.",
                    serviceClientProxy);
            field = NettyHttpServiceClient.class.getDeclaredField("httpProxy");
            field.setAccessible(true);
            URI serviceClientProxyURI = (URI) field.get(serviceClientProxy);
            assertNotNull("When proxy is set the proxy URI of serviceClientProxy should be null.",
                    serviceClientProxyURI);
            assertEquals(
                    "The proxy URI NettyHttpServiceClient should mach the one in the properties.",
                    new URI(proxyAddress), serviceClientProxyURI);
        }

        field = RegistryAdapterService.class.getDeclaredField("serviceClientNoProxy");
        field.setAccessible(true);
        NettyHttpServiceClient serviceClientNoProxy = (NettyHttpServiceClient) field
                .get(registryAdapterService);
        assertNotNull("When no proxy is set the serviceClientNoProxy should not be null.",
                serviceClientNoProxy);
        field = NettyHttpServiceClient.class.getDeclaredField("httpProxy");
        field.setAccessible(true);
        URI serviceClientNoProxyURI = (URI) field.get(serviceClientNoProxy);
        assertNull("The proxy URI of serviceClientNoProxy should be null.",
                serviceClientNoProxyURI);

        field = RegistryAdapterService.class.getDeclaredField("serviceClientNoProxyList");
        field.setAccessible(true);
        Set<String> serviceClientNoProxyList = (Set<String>) field.get(registryAdapterService);
        if (noProxyListProp.equals(RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE)
                || proxyAddress.equals(RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE)) {
            assertTrue("The serviceClientNoProxyList should be empty.",
                    serviceClientNoProxyList.isEmpty());
        } else {
            assertEquals("The serviceClientNoProxyList should not be empty.", 2,
                    serviceClientNoProxyList.size());
            serviceClientNoProxyList.forEach(s -> {
                assertTrue("All registries in the list property should be in the exception list.",
                        noProxyListProp.contains(s));
            });
        }
    }
}
