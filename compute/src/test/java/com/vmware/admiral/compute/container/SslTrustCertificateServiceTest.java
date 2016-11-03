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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceSubscriptionState;
import com.vmware.xenon.common.ServiceSubscriptionState.ServiceSubscriber;
import com.vmware.xenon.common.StatelessService;
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
        validateIllegalArgument(() -> {
            postForValidation(sslTrustCert);
        }, "certificate must not be null.");

        sslTrustCert.certificate = "invalid cert";
        validateIllegalArgument(() -> {
            postForValidation(sslTrustCert);
        }, "certificate is not valid.");
    }

    @Test
    public void testPATCH() throws Throwable {
        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);
        String shouldNotUpdateLink = "/some-service/not-valid/patch";
        assertNull(sslTrustCert.resourceLink);
        sslTrustCert.resourceLink = shouldNotUpdateLink;
        sslTrustCert.certificate = sslTrust2;

        boolean expectedFailure = false;
        URI uri = UriUtils.buildUri(host, sslTrustCert.documentSelfLink);
        doOperation(sslTrustCert, uri, expectedFailure, Action.PATCH);

        SslTrustCertificateState updatedSslTrustCert = getDocument(SslTrustCertificateState.class,
                sslTrustCert.documentSelfLink);

        assertNull(updatedSslTrustCert.resourceLink);
        assertEquals(sslTrust2, updatedSslTrustCert.certificate);
        validateCertProperties(updatedSslTrustCert);
    }

    @Test
    public void testPUT() throws Throwable {
        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);
        String shouldNotUpdateLink = "/some-service/not-valid/put";
        assertNull(sslTrustCert.resourceLink);
        sslTrustCert.resourceLink = shouldNotUpdateLink;
        sslTrustCert.certificate = sslTrust2;

        boolean expectedFailure = false;
        URI uri = UriUtils.buildUri(host, sslTrustCert.documentSelfLink);
        doOperation(sslTrustCert, uri, expectedFailure, Action.PUT);

        SslTrustCertificateState updatedSslTrustCert = getDocument(SslTrustCertificateState.class,
                sslTrustCert.documentSelfLink);

        assertNull(updatedSslTrustCert.resourceLink);
        assertEquals(sslTrust2, updatedSslTrustCert.certificate);
        validateCertProperties(updatedSslTrustCert);
    }

    @Test
    public void testIdempotentPOST() throws Throwable {
        String selfLinkId = "10.23.45.78:4567";

        SslTrustCertificateState sslTrustCert1 = new SslTrustCertificateState();
        sslTrustCert1.documentSelfLink = selfLinkId;
        sslTrustCert1.certificate = sslTrust1;
        sslTrustCert1 = doPost(sslTrustCert1, SslTrustCertificateService.FACTORY_LINK);

        SslTrustCertificateState sslTrustCert2 = new SslTrustCertificateState();
        sslTrustCert2.documentSelfLink = selfLinkId;
        sslTrustCert2.certificate = sslTrust2;
        sslTrustCert2 = doPost(sslTrustCert2, SslTrustCertificateService.FACTORY_LINK);

        sslTrustCert = getDocument(SslTrustCertificateState.class,
                UriUtils.buildUriPath(SslTrustCertificateService.FACTORY_LINK, selfLinkId));

        assertEquals(sslTrust2, sslTrustCert.certificate);
        validateCertProperties(sslTrustCert);
    }

    @Test
    public void testUnSubscribeWhenAssociatedResourceDeleted() throws Throwable {
        ContainerState cs = doPost(createContainerState(), ContainerFactoryService.SELF_LINK);
        sslTrustCert.resourceLink = cs.documentSelfLink;
        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);

        AtomicBoolean resultSubscribe = new AtomicBoolean();
        URI csSubscritionUri = UriUtils.buildSubscriptionUri(host, cs.documentSelfLink);
        Operation query = Operation.createGet(csSubscritionUri)
                .setReferer(host.getReferer())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    }
                    ServiceSubscriptionState subscriptionState = o
                            .getBody(ServiceSubscriptionState.class);
                    if (subscriptionState.subscribers.size() == 1) {
                        resultSubscribe.set(true);
                    }
                    host.completeIteration();
                });

        // wait until the subscription is completed
        waitFor(() -> {
            host.testStart(1);
            host.send(query);
            host.testWait();
            return resultSubscribe.get();
        });

        delete(sslTrustCert.documentSelfLink);

        AtomicBoolean resultDelete = new AtomicBoolean();
        Operation querySubs = Operation.createGet(csSubscritionUri)
                .setReferer(host.getReferer())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    }
                    ServiceSubscriptionState subscriptionState = o
                            .getBody(ServiceSubscriptionState.class);
                    if (subscriptionState.subscribers.size() == 0) {
                        resultDelete.set(true);
                    }
                    host.completeIteration();

                });

        // wait until the subscription is removed
        waitFor(() -> {
            host.testStart(1);
            host.send(querySubs);
            host.testWait();
            return resultDelete.get();
        });
    }

    @Test
    public void testSubscribeForDeletionWhenAssociateResourceSet() throws Throwable {
        ContainerState cs = doPost(createContainerState(), ContainerFactoryService.SELF_LINK);

        sslTrustCert.resourceLink = cs.documentSelfLink;
        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);
        assertNotNull(sslTrustCert.subscriptionLink);

        URI csSubscritionUri = UriUtils.buildSubscriptionUri(host, cs.documentSelfLink);
        AtomicBoolean result = new AtomicBoolean();
        Operation query = Operation.createGet(csSubscritionUri)
                .setReferer(host.getReferer())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    ServiceSubscriptionState subscriptionState = o
                            .getBody(ServiceSubscriptionState.class);
                    if (subscriptionState.subscribers.size() == 1) {
                        result.set(true);
                    }
                    host.completeIteration();
                });

        // wait until the subscription is completed
        waitFor(() -> {
            host.testStart(1);
            host.send(query);
            host.testWait();
            return result.get();
        });

        // subscribe for SslTrustCertificateState DELETE in order to verify that
        // the document will be self deleted after the reference object ContainerState is deleted.
        URI subscriptionUrl = UriUtils.buildSubscriptionUri(host, sslTrustCert.documentSelfLink);
        Operation subscribe = Operation.createPost(subscriptionUrl);
        subscribe.setReferer(host.getReferer());

        AtomicBoolean sslTrustCertDeleted = new AtomicBoolean();
        boolean replayState = false;
        boolean usePublicUri = false;
        ServiceSubscriber sr = ServiceSubscriber.create(replayState).setUsePublicUri(usePublicUri);
        host.startSubscriptionService(subscribe, new StatelessService() {
            @Override
            public void handleRequest(Operation update) {
                if (Action.DELETE == update.getAction()) {
                    sslTrustCertDeleted.set(true);
                    update.complete();
                    host.completeIteration();
                }
            }
        }, sr);

        // Delete ContainerState. The number of expected completion is two because
        // the deletion will trigger notification and call the notification handler above.
        host.testStart(2);
        Operation post = Operation
                .createDelete(UriUtils.buildUri(host, cs.documentSelfLink))
                .setBody(new ServiceDocument())
                .setCompletion(host.getCompletion());
        host.send(post);
        host.testWait();

        assertTrue(sslTrustCertDeleted.get());
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
