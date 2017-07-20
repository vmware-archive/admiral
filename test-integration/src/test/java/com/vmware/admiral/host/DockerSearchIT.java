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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.admiral.common.util.ServerX509TrustManager.JAVAX_NET_SSL_TRUST_STORE;
import static com.vmware.admiral.common.util.ServerX509TrustManager.JAVAX_NET_SSL_TRUST_STORE_PASSWORD;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse;
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
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class DockerSearchIT extends BaseTestCase {

    private static final String DOCKER_REGISTRY = "https://registry.hub.docker.com";
    private static final String TEST_IMAGE = "admiral";
    private static final String DEFAULT_REGISTRY_HOSTNAME = UriUtilsExtended
            .extractHostAndPort(DOCKER_REGISTRY);

    private static String oldTrustStore;
    private static String oldTrustStorePassword;

    @BeforeClass
    public static void setUpClass() throws Throwable {
        // Force a custom trust store... that shouldn't override the Java default cacerts.
        URI customStore = DockerSearchIT.class.getResource("/certs/trusted_certificates.jks")
                .toURI();
        oldTrustStore = System.setProperty(JAVAX_NET_SSL_TRUST_STORE, customStore.getPath());
        oldTrustStorePassword = System.setProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD, "changeit");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        // Restore system properties and reset trust manager to avoid side effects on other tests
        restoreSystemProperty(JAVAX_NET_SSL_TRUST_STORE, oldTrustStore);
        restoreSystemProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD, oldTrustStorePassword);
        ServerX509TrustManager.invalidate();
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

    @Test
    public void testCheckTrustedCertificates() throws Exception {

        // Force null INSTANCE when in because current test needs different trust store data.
        ServerX509TrustManager.invalidate();

        ServerX509TrustManager trustManager = ServerX509TrustManager.create(host);

        // Validate a public certificate, e.g. the Docker registry URL one.
        // Is should work because the default cacerts from the JRE is always included and trusted.

        final CountDownLatch latch = new CountDownLatch(1);
        SslCertificateResolver.execute(new URI(DOCKER_REGISTRY), (resolver, ex) -> {
            try {
                assertNull(ex);
                trustManager.checkServerTrusted(resolver.getCertificateChain(), "RSA");

                // Validate a custom certificate.
                // It should work because a trust store which contains the cert is passed as
                // argument.

                URI customCertificate = DockerSearchIT.class
                        .getResource("/certs/trusted_server.crt").toURI();
                try (InputStream is = new FileInputStream(customCertificate.getPath())) {
                    CertificateFactory factory = CertificateFactory.getInstance("X.509");
                    X509Certificate certificate = (X509Certificate) factory.generateCertificate(is);

                    trustManager.checkServerTrusted(new X509Certificate[] { certificate }, "RSA");
                }
            } catch (Throwable t) {
                fail(t.getMessage());
                throw new RuntimeException(t);
            } finally {
                latch.countDown();
            }
        });
        latch.await(HOST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
        assertTrue(response.numResults > 0);
        assertNotNull(response.results);

        response.results.forEach((result) -> {
            assertNotNull(result);
            assertNotNull(result.name);
            assertEquals(DOCKER_REGISTRY, result.registry); // from the Docker registry
            assertTrue(result.name.startsWith(DEFAULT_REGISTRY_HOSTNAME));
            assertTrue(result.name.contains(TEST_IMAGE));
        });
    }

}
