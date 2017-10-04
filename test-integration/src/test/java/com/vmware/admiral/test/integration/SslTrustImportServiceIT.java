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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getSystemOrTestProp;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.common.SslTrustImportService;
import com.vmware.admiral.service.common.SslTrustImportService.SslTrustImportRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class SslTrustImportServiceIT extends ComputeBaseTest {

    private static String certTrustedUrl = getSystemOrTestProp("cert.trusted.url");
    private static String certSelfSignedUrl = getSystemOrTestProp("cert.selfsigned.url");

    private URI uri;
    private SslTrustImportRequest request;

    @Before
    public void setUp() throws Throwable {
        uri = UriUtils.buildUri(host, SslTrustImportService.SELF_LINK);
        request = new SslTrustImportRequest();
        waitForServiceAvailability(SslTrustImportService.SELF_LINK);
        ServerX509TrustManager.init(host);
    }

    @Test
    public void testImportPublicCertificateShouldReturAccepted() throws Throwable {
        request.hostUri = URI.create(certTrustedUrl);// public trusted certificate

        Operation response = putRequest(request);

        // when public certificate and trusted, the certificate is returned and 202 status
        assertNotNull(response.getBodyRaw());
        assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getStatusCode());
    }

    @Test
    public void testImportSelfSingedCertShouldReturnImportCertForConfirmation() throws Throwable {
        request.hostUri = URI.create(certSelfSignedUrl);// self-signed

        Operation response = putRequest(request);

        // when public certificate and trusted, no body returned and 204 status
        assertEquals(HttpURLConnection.HTTP_OK, response.getStatusCode());
        SslTrustCertificateState sslTrustState = response.getBody(SslTrustCertificateState.class);
        assertNotNull(sslTrustState);
        assertNotNull(sslTrustState.certificate);
        assertNotNull(sslTrustState.validTo);
        assertNotNull(sslTrustState.validSince);
        assertNotNull(sslTrustState.fingerprint);

        assertEquals(request.hostUri.getHost(), sslTrustState.commonName);
        assertEquals(request.hostUri.getHost(), sslTrustState.issuerName);
    }

    @Test
    public void testImportSelfSingedCertShouldBeStoredWhenAccepted() throws Throwable {
        request.hostUri = URI.create(certSelfSignedUrl);// self-signed
        request.acceptCertificate = true; // confirm acceptance of not trusted certificate
        request.tenantLinks = new ArrayList<>(Arrays.asList("T1", "T2", "T3"));

        Operation response = putRequest(request);

        // when self-signed certificate is accepted, no body returned and 204 status
        assertNull(response.getBodyRaw());
        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatusCode());

        String sslTrustStateSelfLink = response.getResponseHeader(Operation.LOCATION_HEADER);
        assertNotNull(sslTrustStateSelfLink);

        SslTrustCertificateState sslTrustState = getDocument(SslTrustCertificateState.class,
                sslTrustStateSelfLink);

        assertNotNull(sslTrustState);
        assertNotNull(sslTrustState.certificate);
        assertNotNull(sslTrustState.validTo);
        assertNotNull(sslTrustState.validSince);
        assertNotNull(sslTrustState.fingerprint);
        assertNotNull(sslTrustState.tenantLinks);

        assertEquals(request.hostUri.getHost(), sslTrustState.commonName);
        assertEquals(request.hostUri.getHost(), sslTrustState.issuerName);

        assertEquals(request.tenantLinks.size(), sslTrustState.tenantLinks.size());
        Collections.sort(request.tenantLinks);
        Collections.sort(sslTrustState.tenantLinks);
        assertEquals(request.tenantLinks, sslTrustState.tenantLinks);
    }

    private Operation putRequest(SslTrustImportRequest request) throws Throwable {
        Operation[] result = new Operation[] { null };
        Operation put = Operation.createPut(uri)
                .setBody(request)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    result[0] = o;
                    host.completeIteration();
                });

        host.testStart(1);
        host.send(put);
        host.testWait();
        return result[0];
    }
}
