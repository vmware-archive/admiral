/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.kubernetes;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;

import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.adapter.kubernetes.service.AbstractKubernetesAdapterService.KubernetesContext;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

public class ApiUtilTest {
    private static final String HOST_ADDRESS = "test-address";
    private static KubernetesContext context;

    @BeforeClass
    public static void setUp() {
        context = createKubernetesContext(HOST_ADDRESS,
                KubernetesHostConstants.KUBERNETES_HOST_DEFAULT_NAMESPACE);
    }

    @Test
    public void testCorrectApiPrefix() {
        String expectedPrefix = context.host.address + ApiUtil.API_PREFIX_V1;
        String actualPrefix = ApiUtil.apiPrefix(context, ApiUtil.API_PREFIX_V1);
        assertEquals(expectedPrefix, actualPrefix);
    }

    @Test
    public void testCorrectNamespacePrefixFromContext() {
        final String testAddress = "some-test-address";
        final String testNamespace = "some-test-namespace";
        KubernetesContext testContext = createKubernetesContext(testAddress, testNamespace);

        String expectedPrefix = testAddress + ApiUtil.API_PREFIX_V1
                + "/namespaces/" + testNamespace;
        String actualPrefix = ApiUtil.namespacePrefix(testContext, ApiUtil.API_PREFIX_V1);

        assertEquals(expectedPrefix, actualPrefix);
    }

    @Test
    public void testCorrectNamespacePrefixFromDescription() {
        final String testNamespace = "test-namespace";
        KubernetesDescription description = createKubernetesDescription(ApiUtil.API_PREFIX_V1,
                testNamespace, null);

        String expectedPrefix = context.host.address + ApiUtil.API_PREFIX_V1 + "/namespaces/"
                + testNamespace;
        String actualPrefix = ApiUtil.namespacePrefix(context, description, ApiUtil.API_PREFIX_V1);
        assertEquals(expectedPrefix, actualPrefix);
    }

    @Test
    public void testDefaultNamespaceIfNoneSetInContextAndDescription() {
        final String testAddress = "some-test-address";
        KubernetesContext testContext = createKubernetesContext(testAddress, null);
        KubernetesDescription description = createKubernetesDescription(ApiUtil.API_PREFIX_V1, null,
                null);

        String expectedPrefix = testAddress + ApiUtil.API_PREFIX_V1
                + "/namespaces/" + KubernetesHostConstants.KUBERNETES_HOST_DEFAULT_NAMESPACE;
        String actualPrefix = ApiUtil.namespacePrefix(testContext, description,
                ApiUtil.API_PREFIX_V1);

        assertEquals(expectedPrefix, actualPrefix);
    }

    @Test
    public void testBuildApiServerProxyUrl() {
        String apiVersionPrefix = "/api/v1";
        String namespace = "test-namespace";
        String selfLink = "/services/test";
        String proxiedPath = "/test-path";

        String expected = context.host.address
                + "/api/v1/namespaces/test-namespace/services/test/proxy/test-path";
        assertEquals(expected, ApiUtil.buildApiServerProxyUri(context, apiVersionPrefix, namespace,
                selfLink, proxiedPath));
    }

    @Test
    public void testBuildKubernetesFactoryUri() {
        final String testVersion = "test-api-version";
        final String testNamespace = "test-namespace";
        final String testEntityKind = "TestEntity";
        final String expectedEntityEndpoint = "testentities";

        KubernetesDescription description = createKubernetesDescription(testVersion,
                testNamespace, testEntityKind);

        String uri = ApiUtil.buildKubernetesFactoryUri(description, context).toString();
        String expectedUri = context.host.address + "/apis/" + testVersion + "/namespaces/"
                + testNamespace + "/" + expectedEntityEndpoint;
        assertEquals(expectedUri, uri);
    }

    @Test
    public void testBuildKubernetesFactoryUriForNamespaces() {
        final String testVersion = ApiUtil.API_VERSION_V1;
        final String testNamespace = "test-namespace-should-not-appear";

        KubernetesDescription description = createKubernetesDescription(testVersion,
                testNamespace, KubernetesUtil.NAMESPACE_TYPE);

        String uri = ApiUtil.buildKubernetesFactoryUri(description, context).toString();
        String expectedUri = context.host.address + "/api/v1/namespaces";
        assertEquals(expectedUri, uri);
    }

    private static KubernetesContext createKubernetesContext(String hostAddress,
            String namespace) {
        KubernetesContext context = new KubernetesContext();
        context.host = new ComputeState();
        context.host.address = hostAddress;
        context.host.customProperties = new HashMap<>();
        if (namespace != null) {
            context.host.customProperties.put(
                    KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME,
                    namespace);
        }
        return context;
    }

    private static KubernetesDescription createKubernetesDescription(String apiVersionPrefix,
            String namespace, String entityKind) {
        KubernetesDescription description = new KubernetesDescription();
        description.apiVersion = apiVersionPrefix;
        description.namespace = namespace;
        description.type = entityKind;
        return description;
    }
}
