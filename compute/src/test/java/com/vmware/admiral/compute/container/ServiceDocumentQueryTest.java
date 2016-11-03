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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceDocumentQuery.ServiceDocumentQueryElementResult;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ServiceDocumentQueryTest extends ComputeBaseTest {
    ServiceDocumentQuery<SslTrustCertificateState> query;
    List<SslTrustCertificateState> certs;
    private String sslTrust1;
    private String sslTrust2;

    @Before
    public void setUp() throws Throwable {
        query = new ServiceDocumentQuery<>(host, SslTrustCertificateState.class);
        certs = new ArrayList<>();
        sslTrust1 = CommonTestStateFactory.getFileContent("test_ssl_trust.PEM").trim();
        sslTrust2 = CommonTestStateFactory.getFileContent("test_ssl_trust2.PEM").trim();

        waitForServiceAvailability(SslTrustCertificateService.FACTORY_LINK);
    }

    @Test
    public void testQueryDocument() throws Throwable {
        certs = queryDocument("testLink");
        assertEquals(0, certs.size());

        SslTrustCertificateState sslTrustCert = new SslTrustCertificateState();
        sslTrustCert.certificate = sslTrust1;
        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);

        certs = queryDocument(sslTrustCert.documentSelfLink);
        assertEquals(1, certs.size());
        assertEquals(sslTrustCert.documentSelfLink, certs.get(0).documentSelfLink);
        assertEquals(sslTrust1, certs.get(0).certificate);

        delete(sslTrustCert.documentSelfLink);
        certs = queryDocument(sslTrustCert.documentSelfLink);
        assertEquals(0, certs.size());
    }

    @Test
    public void testQueryUpdatedDocumentSince() throws Throwable {
        long startTime = Utils.getNowMicrosUtc();
        certs = queryDocumentUpdatedSince(startTime, "testLink");
        assertEquals(0, certs.size());

        SslTrustCertificateState sslTrustCert = new SslTrustCertificateState();
        sslTrustCert.certificate = sslTrust1;
        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);

        long timeAfterPost = Utils.getNowMicrosUtc();

        // match time but invalid link
        certs = queryDocumentUpdatedSince(startTime, "testLink");
        assertEquals(0, certs.size());

        // match link but invalid time
        certs = queryDocumentUpdatedSince(timeAfterPost, sslTrustCert.documentSelfLink);
        assertEquals(0, certs.size());

        // match link but invalid time
        certs = queryDocumentUpdatedSince(startTime, sslTrustCert.documentSelfLink);
        assertEquals(1, certs.size());

        sslTrustCert.certificate = sslTrust2;
        doOperation(sslTrustCert, UriUtils.buildUri(host, sslTrustCert.documentSelfLink), false,
                Service.Action.PATCH);

        long timeAfterPatch = Utils.getNowMicrosUtc();

        // the delta for the update should be retrieved
        certs = queryDocumentUpdatedSince(timeAfterPost, sslTrustCert.documentSelfLink);
        assertEquals(1, certs.size());
        assertEquals(sslTrust2, certs.get(0).certificate);

        // no updates after patch
        certs = queryDocumentUpdatedSince(timeAfterPatch, sslTrustCert.documentSelfLink);
        assertEquals(0, certs.size());

        delete(sslTrustCert.documentSelfLink);

        long timeAfterDelete = Utils.getNowMicrosUtc();

        certs = queryDocumentUpdatedSince(timeAfterPatch, sslTrustCert.documentSelfLink);
        assertEquals(1, certs.size());
        assertTrue(ServiceDocument.isDeleted(certs.get(0)));

        certs = queryDocumentUpdatedSince(timeAfterDelete, sslTrustCert.documentSelfLink);
        assertEquals(0, certs.size());
    }

    @Test
    public void testQueryUpdatedSince() throws Throwable {
        long startTime = Utils.getNowMicrosUtc();

        SslTrustCertificateState sslTrustCert = new SslTrustCertificateState();
        sslTrustCert.certificate = sslTrust1;
        doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);

        SslTrustCertificateState sslTrustCert1 = new SslTrustCertificateState();
        sslTrustCert1.documentSelfLink = "sslTrust1";
        sslTrustCert1.certificate = sslTrust1;
        sslTrustCert1 = doPost(sslTrustCert1, SslTrustCertificateService.FACTORY_LINK);

        certs = queryUpdatedSince(startTime);

        int countAfterCert1 = 2;
        assertEquals(countAfterCert1, certs.size());
        long timeAfterCert1 = Utils.getNowMicrosUtc();

        sslTrustCert = new SslTrustCertificateState();
        sslTrustCert.certificate = sslTrust2;
        doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);

        SslTrustCertificateState sslTrustCert2 = new SslTrustCertificateState();
        sslTrustCert2.documentSelfLink = "sslTrust2";
        sslTrustCert2.certificate = sslTrust2;
        sslTrustCert2 = doPost(sslTrustCert2, SslTrustCertificateService.FACTORY_LINK);

        certs = queryUpdatedSince(startTime);

        int countAfterCert2 = 2;
        assertEquals(countAfterCert1 + countAfterCert2, certs.size());
        long timeAfterCert2 = Utils.getNowMicrosUtc();

        certs = queryUpdatedSince(timeAfterCert1);
        assertEquals(countAfterCert2, certs.size());

        boolean match = false;
        for (SslTrustCertificateState state : certs) {
            if (sslTrustCert2.documentSelfLink.equals(state.documentSelfLink)) {
                assertNotNull(sslTrustCert2.certificate);
                match = true;
            }
        }
        assertTrue(match);

        certs = queryUpdatedSince(timeAfterCert2);
        assertEquals(0, certs.size());

        long timeBeforeDeletion = Utils.getNowMicrosUtc();
        delete(sslTrustCert1.documentSelfLink);
        certs = queryUpdatedSince(startTime);
        assertEquals(countAfterCert1 + countAfterCert2, certs.size());

        boolean deleted = false;
        for (SslTrustCertificateState state : certs) {
            if (sslTrustCert1.documentSelfLink.equals(state.documentSelfLink)) {
                assertTrue(ServiceDocument.isDeleted(state));
                deleted = true;
            }
        }
        assertTrue(deleted);
        delete(sslTrustCert2.documentSelfLink);

        certs = queryUpdatedSince(timeBeforeDeletion);
        assertEquals(countAfterCert1 + countAfterCert2 - 2, certs.size());
        for (SslTrustCertificateState state : certs) {
            assertTrue(ServiceDocument.isDeleted(state));
        }

        certs = queryUpdatedSince(Utils.getNowMicrosUtc());
        assertEquals(0, certs.size());
    }

    @Test
    public void testQueryUpdatedSinceWithDeleteAndCreate() throws Throwable {
        long startTime = Utils.getNowMicrosUtc();
        String testSelfLink = "test-link234";

        SslTrustCertificateState sslTrustCert = new SslTrustCertificateState();
        sslTrustCert.documentSelfLink = testSelfLink;
        sslTrustCert.certificate = sslTrust1;
        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);

        certs = queryUpdatedSince(startTime);

        assertEquals(1, certs.size());

        SslTrustCertificateState updatedCert = new SslTrustCertificateState();
        updatedCert.documentSelfLink = testSelfLink;
        updatedCert.certificate = sslTrust2;
        updatedCert = doPost(updatedCert, SslTrustCertificateService.FACTORY_LINK);

        // update should still have only one document:
        certs = queryUpdatedSince(startTime);

        assertEquals(1, certs.size());
        assertEquals(sslTrust2, certs.get(0).certificate);

        delete(sslTrustCert.documentSelfLink);
        certs = queryUpdatedSince(startTime);
        // again, only one entity but indicated as deleted
        assertEquals(1, certs.size());
        assertTrue(ServiceDocument.isDeleted(certs.get(0)));

        sslTrustCert = new SslTrustCertificateState();
        sslTrustCert.documentSelfLink = testSelfLink;
        sslTrustCert.certificate = sslTrust1;
        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);

        certs = queryUpdatedSince(startTime);
        assertEquals(1, certs.size()); // expectation is to still have only one
        assertEquals(sslTrust1, sslTrustCert.certificate);
    }

    private List<SslTrustCertificateState> queryDocument(String documentSelfLink) throws Throwable {
        host.testStart(1);
        query.queryDocument(documentSelfLink, handler(true));
        host.testWait();
        return certs;
    }

    private List<SslTrustCertificateState> queryDocumentUpdatedSince(
            long documentSinceUpdateTimeMicros, String documentSelfLink) throws Throwable {
        host.testStart(1);
        query.queryUpdatedDocumentSince(documentSinceUpdateTimeMicros, documentSelfLink,
                handler(true));
        host.testWait();
        return certs;
    }

    private List<SslTrustCertificateState> queryUpdatedSince(long timeInMicros) throws Throwable {
        host.testStart(1);
        query.queryUpdatedSince(timeInMicros, handler(false));
        host.testWait();
        return certs;
    }

    private Consumer<ServiceDocumentQueryElementResult<SslTrustCertificateState>> handler(
            boolean singleResult) {
        certs.clear();
        return (r) -> {
            if (r.hasException()) {
                host.failIteration(r.getException());
                return;
            }
            if (!r.hasResult()) {
                host.completeIteration();
                return;
            }
            certs.add(r.getResult());

            if (singleResult) {
                host.completeIteration();
                return;
            }
        };
    }
}
