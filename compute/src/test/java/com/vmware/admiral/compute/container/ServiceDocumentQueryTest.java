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

import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceDocumentQuery.ServiceDocumentQueryElementResult;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ServiceDocumentQueryTest extends ComputeBaseTest {
    ServiceDocumentQuery<ContainerDescription> query;
    List<ContainerDescription> descs;
    private String image1 = "image1";
    private String image2 = "image2";

    @Before
    public void setUp() throws Throwable {
        query = new ServiceDocumentQuery<>(host, ContainerDescription.class);
        descs = new ArrayList<>();
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
    }

    @Test
    public void testQueryDocument() throws Throwable {
        descs = queryDocument("testLink");
        assertEquals(0, descs.size());

        ContainerDescription sslTrustCert = new ContainerDescription();
        sslTrustCert.image = image1;
        sslTrustCert = doPost(sslTrustCert, ContainerDescriptionService.FACTORY_LINK);

        descs = queryDocument(sslTrustCert.documentSelfLink);
        assertEquals(1, descs.size());
        assertEquals(sslTrustCert.documentSelfLink, descs.get(0).documentSelfLink);
        assertEquals(image1, descs.get(0).image);

        delete(sslTrustCert.documentSelfLink);
        descs = queryDocument(sslTrustCert.documentSelfLink);
        assertEquals(0, descs.size());
    }

    @Test
    public void testQueryUpdatedDocumentSince() throws Throwable {
        long startTime = Utils.getNowMicrosUtc();
        descs = queryDocumentUpdatedSince(startTime, "testLink");
        assertEquals(0, descs.size());

        ContainerDescription sslTrustCert = new ContainerDescription();
        sslTrustCert.image = image1;
        sslTrustCert = doPost(sslTrustCert, ContainerDescriptionService.FACTORY_LINK);

        long timeAfterPost = Utils.getNowMicrosUtc();

        // match time but invalid link
        descs = queryDocumentUpdatedSince(startTime, "testLink");
        assertEquals(0, descs.size());

        // match link but invalid time
        descs = queryDocumentUpdatedSince(timeAfterPost, sslTrustCert.documentSelfLink);
        assertEquals(0, descs.size());

        // match link but invalid time
        descs = queryDocumentUpdatedSince(startTime, sslTrustCert.documentSelfLink);
        assertEquals(1, descs.size());

        sslTrustCert.image = image2;
        doOperation(sslTrustCert, UriUtils.buildUri(host, sslTrustCert.documentSelfLink), false,
                Service.Action.PATCH);

        long timeAfterPatch = Utils.getNowMicrosUtc();

        // the delta for the update should be retrieved
        descs = queryDocumentUpdatedSince(timeAfterPost, sslTrustCert.documentSelfLink);
        assertEquals(1, descs.size());
        assertEquals(image2, descs.get(0).image);

        // no updates after patch
        descs = queryDocumentUpdatedSince(timeAfterPatch, sslTrustCert.documentSelfLink);
        assertEquals(0, descs.size());

        delete(sslTrustCert.documentSelfLink);

        long timeAfterDelete = Utils.getNowMicrosUtc();

        descs = queryDocumentUpdatedSince(timeAfterPatch, sslTrustCert.documentSelfLink);
        assertEquals(1, descs.size());
        assertTrue(ServiceDocument.isDeleted(descs.get(0)));

        descs = queryDocumentUpdatedSince(timeAfterDelete, sslTrustCert.documentSelfLink);
        assertEquals(0, descs.size());
    }

    @Test
    public void testQueryUpdatedSince() throws Throwable {
        long startTime = Utils.getNowMicrosUtc();

        ContainerDescription sslTrustCert = new ContainerDescription();
        sslTrustCert.image = image1;
        doPost(sslTrustCert, ContainerDescriptionService.FACTORY_LINK);

        ContainerDescription sslTrustCert1 = new ContainerDescription();
        sslTrustCert1.documentSelfLink = "image";
        sslTrustCert1.image = image1;
        sslTrustCert1 = doPost(sslTrustCert1, ContainerDescriptionService.FACTORY_LINK);

        descs = queryUpdatedSince(startTime);

        int countAfterCert1 = 2;
        assertEquals(countAfterCert1, descs.size());
        long timeAfterCert1 = Utils.getNowMicrosUtc();

        sslTrustCert = new ContainerDescription();
        sslTrustCert.image = image2;
        doPost(sslTrustCert, ContainerDescriptionService.FACTORY_LINK);

        ContainerDescription sslTrustCert2 = new ContainerDescription();
        sslTrustCert2.documentSelfLink = "image2";
        sslTrustCert2.image = image2;
        sslTrustCert2 = doPost(sslTrustCert2, ContainerDescriptionService.FACTORY_LINK);

        descs = queryUpdatedSince(startTime);

        int countAfterCert2 = 2;
        assertEquals(countAfterCert1 + countAfterCert2, descs.size());
        long timeAfterCert2 = Utils.getNowMicrosUtc();

        descs = queryUpdatedSince(timeAfterCert1);
        assertEquals(countAfterCert2, descs.size());

        boolean match = false;
        for (ContainerDescription state : descs) {
            if (sslTrustCert2.documentSelfLink.equals(state.documentSelfLink)) {
                assertNotNull(sslTrustCert2.image);
                match = true;
            }
        }
        assertTrue(match);

        descs = queryUpdatedSince(timeAfterCert2);
        assertEquals(0, descs.size());

        long timeBeforeDeletion = Utils.getNowMicrosUtc();
        delete(sslTrustCert1.documentSelfLink);
        descs = queryUpdatedSince(startTime);
        assertEquals(countAfterCert1 + countAfterCert2, descs.size());

        boolean deleted = false;
        for (ContainerDescription state : descs) {
            if (sslTrustCert1.documentSelfLink.equals(state.documentSelfLink)) {
                assertTrue(ServiceDocument.isDeleted(state));
                deleted = true;
            }
        }
        assertTrue(deleted);
        delete(sslTrustCert2.documentSelfLink);

        descs = queryUpdatedSince(timeBeforeDeletion);
        assertEquals(countAfterCert1 + countAfterCert2 - 2, descs.size());
        for (ContainerDescription state : descs) {
            assertTrue(ServiceDocument.isDeleted(state));
        }

        descs = queryUpdatedSince(Utils.getNowMicrosUtc());
        assertEquals(0, descs.size());
    }

    @Test
    public void testQueryUpdatedSinceWithDeleteAndCreate() throws Throwable {
        long startTime = Utils.getNowMicrosUtc();
        String testSelfLink = "test-link234";

        ContainerDescription sslTrustCert = new ContainerDescription();
        sslTrustCert.documentSelfLink = testSelfLink;
        sslTrustCert.image = image1;
        sslTrustCert = doPost(sslTrustCert, ContainerDescriptionService.FACTORY_LINK);

        descs = queryUpdatedSince(startTime);

        assertEquals(1, descs.size());

        ContainerDescription updatedCert = new ContainerDescription();
        updatedCert.documentSelfLink = testSelfLink;
        updatedCert.image = image2;
        updatedCert = doPost(updatedCert, ContainerDescriptionService.FACTORY_LINK);

        // update should still have only one document:
        descs = queryUpdatedSince(startTime);

        assertEquals(1, descs.size());
        assertEquals(image2, descs.get(0).image);

        delete(sslTrustCert.documentSelfLink);
        descs = queryUpdatedSince(startTime);
        // again, only one entity but indicated as deleted
        assertEquals(1, descs.size());
        assertTrue(ServiceDocument.isDeleted(descs.get(0)));

        sslTrustCert = new ContainerDescription();
        sslTrustCert.documentSelfLink = testSelfLink;
        sslTrustCert.image = image1;
        sslTrustCert = doPost(sslTrustCert, ContainerDescriptionService.FACTORY_LINK);

        descs = queryUpdatedSince(startTime);
        assertEquals(1, descs.size()); // expectation is to still have only one
        assertEquals(image1, sslTrustCert.image);
    }

    private List<ContainerDescription> queryDocument(String documentSelfLink) throws Throwable {
        host.testStart(1);
        query.queryDocument(documentSelfLink, handler(true));
        host.testWait();
        return descs;
    }

    private List<ContainerDescription> queryDocumentUpdatedSince(
            long documentSinceUpdateTimeMicros, String documentSelfLink) throws Throwable {
        host.testStart(1);
        query.queryUpdatedDocumentSince(documentSinceUpdateTimeMicros, documentSelfLink,
                handler(true));
        host.testWait();
        return descs;
    }

    private List<ContainerDescription> queryUpdatedSince(long timeInMicros) throws Throwable {
        host.testStart(1);
        query.queryUpdatedSince(timeInMicros, handler(false));
        host.testWait();
        return descs;
    }

    private Consumer<ServiceDocumentQueryElementResult<ContainerDescription>> handler(
            boolean singleResult) {
        descs.clear();
        return (r) -> {
            if (r.hasException()) {
                host.failIteration(r.getException());
                return;
            }
            if (!r.hasResult()) {
                host.completeIteration();
                return;
            }
            descs.add(r.getResult());

            if (singleResult) {
                host.completeIteration();
                return;
            }
        };
    }
}
