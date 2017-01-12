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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class SslTrustCertificateServiceTest extends ComputeBaseTest {
    private String sslTrust1;
    private String sslTrust2;
    private SslTrustCertificateState sslTrustCert;

    @Before
    public void setUp() throws Throwable {
        sslTrust1 = CommonTestStateFactory.getFileContent("test_ssl_trust.PEM").trim();
        sslTrust2 = CommonTestStateFactory.getFileContent("test_ssl_trust2.PEM").trim();
        sslTrustCert = new SslTrustCertificateState();
        sslTrustCert.certificate = sslTrust1;

        waitForServiceAvailability(SslTrustCertificateService.FACTORY_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
    }

    @Test
    public void testPOSTandGET() throws Throwable {
        verifyService(
                FactoryService.create(SslTrustCertificateService.class),
                SslTrustCertificateState.class,
                (prefix, index) -> {
                    return sslTrustCert;
                },
                (prefix, serviceDocument) -> {
                    SslTrustCertificateState state = (SslTrustCertificateState) serviceDocument;
                    assertEquals(sslTrustCert.certificate, state.certificate);
                    validateCertProperties(state);
                });
    }

    @Test
    public void testValidateOnStart() throws Throwable {
        sslTrustCert.certificate = null;
        validateLocalizableException(() -> {
            postForValidation(sslTrustCert);
        }, "certificate must not be null.");

        sslTrustCert.certificate = "invalid cert";
        try {
            postForValidation(sslTrustCert);
            fail();
        } catch (RuntimeException e) {

        }
    }

    @Test
    public void testPATCH() throws Throwable {
        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);
        sslTrustCert.certificate = sslTrust2;

        boolean expectedFailure = false;
        URI uri = UriUtils.buildUri(host, sslTrustCert.documentSelfLink);
        doOperation(sslTrustCert, uri, expectedFailure, Action.PATCH);

        SslTrustCertificateState updatedSslTrustCert = getDocument(SslTrustCertificateState.class,
                sslTrustCert.documentSelfLink);

        assertEquals(sslTrust2, updatedSslTrustCert.certificate);
        validateCertProperties(updatedSslTrustCert);
    }

    @Test
    public void testPUT() throws Throwable {
        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);
        sslTrustCert.certificate = sslTrust2;

        boolean expectedFailure = false;
        URI uri = UriUtils.buildUri(host, sslTrustCert.documentSelfLink);
        doOperation(sslTrustCert, uri, expectedFailure, Action.PUT);

        SslTrustCertificateState updatedSslTrustCert = getDocument(SslTrustCertificateState.class,
                sslTrustCert.documentSelfLink);

        assertEquals(sslTrust2, updatedSslTrustCert.certificate);
        validateCertProperties(updatedSslTrustCert);
    }

    @Test
    public void testIdempotentPOST() throws Throwable {
        SslTrustCertificateState sslTrustCert1 = new SslTrustCertificateState();
        sslTrustCert1.certificate = sslTrust1;
        sslTrustCert1.subscriptionLink = null;
        sslTrustCert1 = doPost(sslTrustCert1, SslTrustCertificateService.FACTORY_LINK);

        SslTrustCertificateState sslTrustCert2 = new SslTrustCertificateState();
        sslTrustCert2.certificate = sslTrust1;
        sslTrustCert2.subscriptionLink = "subscription-link";
        sslTrustCert2 = doPost(sslTrustCert2, SslTrustCertificateService.FACTORY_LINK);

        sslTrustCert = getDocument(SslTrustCertificateState.class,
                sslTrustCert1.documentSelfLink);

        /* We POST two different objects without explicitly setting the documentSelfLink, but these
         * objects have the same certificate. The factory will build the same documentSelfLink for
         * both of these objects and the idempotent option will turn the post to a put, so we expect
         * to have the subscriptionLink set after the POST */
        assertEquals(sslTrustCert2.subscriptionLink, sslTrustCert.subscriptionLink);
        validateCertProperties(sslTrustCert);
    }


    @Test
    public void testNotifyForLastUpdatedPropertyOnAnyWriteOperation() throws Throwable {

        // POST should update the last updated time:
        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);

        String configStateSelfLink = UriUtils.buildUriPath(
                ConfigurationFactoryService.SELF_LINK,
                SslTrustCertificateService.SSL_TRUST_LAST_UPDATED_DOCUMENT_KEY);

        ConfigurationState[] result = new ConfigurationState[] { null };
        // wait until the config state is updated
        waitFor(() -> {
            ConfigurationState state = getDocument(ConfigurationState.class, configStateSelfLink);
            assertNotNull(state);
            result[0] = state;
            return sslTrustCert.documentSelfLink.equals(state.value);
        });

        ConfigurationState configState = result[0];
        long lastUpdated = configState.documentUpdateTimeMicros;

        boolean expectedFailure = false;
        URI uri = UriUtils.buildUri(host, sslTrustCert.documentSelfLink);

        // PUT should update the last updated time:
        SslTrustCertificateState sslTrustCert2 = new SslTrustCertificateState();
        sslTrustCert2.documentSelfLink = sslTrustCert.documentSelfLink;
        sslTrustCert2.certificate = sslTrust2;
        doOperation(sslTrustCert2, uri, expectedFailure, Action.PUT);
        SslTrustCertificateState state = getDocument(SslTrustCertificateState.class,
                sslTrustCert.documentSelfLink);
        assertEquals(sslTrust2, state.certificate);

        configState = waitForPropertyValue(configStateSelfLink, ConfigurationState.class,
                "documentUpdateTimeMicros", lastUpdated, false, waitForStageChangeCountLonger());

        assertTrue(configState.documentUpdateTimeMicros > lastUpdated);
        lastUpdated = configState.documentUpdateTimeMicros;

        // PATCH should update the last updated time:
        sslTrustCert.certificate = sslTrust1;
        doOperation(sslTrustCert, uri, expectedFailure, Action.PATCH);

        configState = waitForPropertyValue(configStateSelfLink, ConfigurationState.class,
                "documentUpdateTimeMicros", lastUpdated, false, waitForStageChangeCountLonger());

        assertTrue(configState.documentUpdateTimeMicros > lastUpdated);
        lastUpdated = configState.documentUpdateTimeMicros;

        // PATCH or PUT should NOT update the last updated time if no change
        doOperation(sslTrustCert, uri, expectedFailure, Action.PATCH);
        lastUpdated = configState.documentUpdateTimeMicros;
        configState = waitForPropertyValue(configStateSelfLink, ConfigurationState.class,
                "documentUpdateTimeMicros", lastUpdated, true, waitForStageChangeCountLonger());
        assertEquals(configState.documentUpdateTimeMicros, lastUpdated);

        // DELETE should update the last updated time:
        doOperation(new SslTrustCertificateState(), uri, expectedFailure, Action.DELETE);

        configState = waitForPropertyValue(configStateSelfLink, ConfigurationState.class,
                "documentUpdateTimeMicros", lastUpdated, false, waitForStageChangeCountLonger());
        assertTrue(configState.documentUpdateTimeMicros > lastUpdated);
    }

    public static ContainerState createContainerState() {
        ContainerState cs = new ContainerState();
        cs.id = "testId";
        cs.address = "testAddresss";
        return cs;
    }

    private void postForValidation(SslTrustCertificateState state) throws Throwable {
        URI uri = UriUtils.buildUri(host, SslTrustCertificateService.FACTORY_LINK);
        doOperation(state, uri, true, Action.POST);
    }

    private void validateCertProperties(SslTrustCertificateState state) throws Exception {
        X509Certificate[] certificates = CertificateUtil.createCertificateChain(state.certificate);

        for (X509Certificate cert : certificates) {
            cert.checkValidity();

            assertEquals(cert.getNotAfter(), new Date(TimeUnit.MICROSECONDS
                    .toMillis(state.documentExpirationTimeMicros)));
            assertEquals(CertificateUtil.getCommonName(cert.getSubjectDN()), state.commonName);
            assertEquals(CertificateUtil.getCommonName(cert.getIssuerDN()), state.issuerName);
            assertEquals(cert.getSerialNumber().toString(), state.serial);
            assertEquals(CertificateUtil.computeCertificateThumbprint(cert), state.fingerprint);
            assertEquals(cert.getNotBefore().getTime(), state.validSince);
            assertEquals(cert.getNotAfter().getTime(), state.validTo);
        }
    }
}
