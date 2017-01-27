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

package com.vmware.admiral.host;

import static java.net.HttpURLConnection.HTTP_OK;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.admiral.common.util.ServerX509TrustManager.JAVAX_NET_SSL_TRUST_STORE;
import static com.vmware.admiral.common.util.ServerX509TrustManager.JAVAX_NET_SSL_TRUST_STORE_PASSWORD;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse.Result;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.SslCertificateResolver;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.image.service.ContainerImageService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.test.integration.SimpleHttpsClient;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpResponse;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class DockerSearchIT extends BaseTestCase {

    private static final String DOCKER_REGISTRY = "https://registry.hub.docker.com";
    private static final String TEST_IMAGE = "kitematic/hello-world-nginx";

    @BeforeClass
    public static void setUpClass() throws Throwable {
        // Force a custom trust store... that shouldn't override the Java default cacerts.
        URI customStore = DockerSearchIT.class.getResource("/certs/trusted_certificates.jks")
                .toURI();
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE, customStore.getPath());
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD, "changeit");
    }

    @Before
    public void setUp() throws Throwable {
        HostInitCommonServiceConfig.startServices(host);
        HostInitRegistryAdapterServiceConfig.startServices(host);
        HostInitImageServicesConfig.startServices(host);
        waitForServiceAvailability(ConfigurationFactoryService.SELF_LINK);
        waitForServiceAvailability(RegistryAdapterService.SELF_LINK);
        waitForServiceAvailability(ContainerImageService.SELF_LINK);
        waitForServiceAvailability(RegistryService.DEFAULT_INSTANCE_LINK);
    }

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    @Test
    public void testCheckTrustedCertificates() throws Exception {

        // Force null INSTANCE when in CI.
        ReflectionUtils.getField(ServerX509TrustManager.class, "INSTANCE").set(null, null);

        ServerX509TrustManager trustManager = ServerX509TrustManager.create(host);

        // Validate a public certificate, e.g. the Docker registry URL one.
        // Is should work because the default cacerts from the JRE is always included and trusted.

        SslCertificateResolver resolver = SslCertificateResolver.connect(new URI(DOCKER_REGISTRY));

        trustManager.checkServerTrusted(resolver.getCertificateChain(), "RSA");

        // Validate a custom certificate.
        // It should work because a trust store which contains the cert is passed as argument.

        URI customCertificate = DockerSearchIT.class.getResource("/certs/trusted_server.crt")
                .toURI();
        try (InputStream is = new FileInputStream(customCertificate.getPath())) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(is);

            trustManager.checkServerTrusted(new X509Certificate[] { certificate }, "RSA");
        }
    }

    @Test
    public void testSearchImageFromDocker() throws Exception {

        // Simple search against the ContainerImageService. No extra registry is configured but the
        // default Docker one should be available at least.

        URI searchUri = UriUtils.buildUri(host, ContainerImageService.SELF_LINK);
        searchUri = UriUtils.extendUriWithQuery(searchUri,
                RegistryAdapterService.SEARCH_QUERY_PROP_NAME, TEST_IMAGE);

        HttpResponse search = SimpleHttpsClient.execute(HttpMethod.GET, searchUri.toString());
        assertEquals(HTTP_OK, search.statusCode);

        assertNotNull(search.responseBody);
        RegistrySearchResponse response = Utils.fromJson(search.responseBody,
                RegistrySearchResponse.class);

        assertNotNull(response);
        assertEquals(1, response.numResults);

        Result result = response.results.get(0);
        assertNotNull(result);
        assertEquals(UriUtilsExtended.extractHostAndPort(DOCKER_REGISTRY) + "/" + TEST_IMAGE,
                result.name); // the image we searched
        assertEquals(DOCKER_REGISTRY, result.registry); // from the Docker registry
    }

}
