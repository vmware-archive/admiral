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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceDocumentQuery.ServiceDocumentQueryElementResult;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.ServiceUriPaths;

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

        ContainerDescription desc = new ContainerDescription();
        desc.image = image1;
        desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);

        descs = queryDocument(desc.documentSelfLink);
        assertEquals(1, descs.size());
        assertEquals(desc.documentSelfLink, descs.get(0).documentSelfLink);
        assertEquals(image1, descs.get(0).image);

        delete(desc.documentSelfLink);
        descs = queryDocument(desc.documentSelfLink);
        assertEquals(0, descs.size());
    }

    @Test
    public void testQueryUpdatedDocumentSince() throws Throwable {
        long startTime = Utils.getNowMicrosUtc();
        descs = queryDocumentUpdatedSince(startTime, "testLink");
        assertEquals(0, descs.size());

        ContainerDescription desc = new ContainerDescription();
        desc.image = image1;
        desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);

        long timeAfterPost = Utils.getNowMicrosUtc();

        // match time but invalid link
        descs = queryDocumentUpdatedSince(startTime, "testLink");
        assertEquals(0, descs.size());

        // match link but invalid time
        descs = queryDocumentUpdatedSince(timeAfterPost, desc.documentSelfLink);
        assertEquals(0, descs.size());

        // match link but invalid time
        descs = queryDocumentUpdatedSince(startTime, desc.documentSelfLink);
        assertEquals(1, descs.size());

        desc.image = image2;
        doOperation(desc, UriUtils.buildUri(host, desc.documentSelfLink), false,
                Service.Action.PATCH);

        long timeAfterPatch = Utils.getNowMicrosUtc();

        // the delta for the update should be retrieved
        descs = queryDocumentUpdatedSince(timeAfterPost, desc.documentSelfLink);
        assertEquals(1, descs.size());
        assertEquals(image2, descs.get(0).image);

        // no updates after patch
        descs = queryDocumentUpdatedSince(timeAfterPatch, desc.documentSelfLink);
        assertEquals(0, descs.size());

        delete(desc.documentSelfLink);

        long timeAfterDelete = Utils.getNowMicrosUtc();

        descs = queryDocumentUpdatedSince(timeAfterPatch, desc.documentSelfLink);
        assertEquals(1, descs.size());
        assertTrue(ServiceDocument.isDeleted(descs.get(0)));

        descs = queryDocumentUpdatedSince(timeAfterDelete, desc.documentSelfLink);
        assertEquals(0, descs.size());
    }

    @Test
    public void testQueryUpdatedSince() throws Throwable {
        long startTime = Utils.getNowMicrosUtc();

        ContainerDescription desc1 = new ContainerDescription();
        desc1.image = image1;
        doPost(desc1, ContainerDescriptionService.FACTORY_LINK);

        ContainerDescription desc2 = new ContainerDescription();
        desc2.documentSelfLink = "image";
        desc2.image = image1;
        desc2 = doPost(desc2, ContainerDescriptionService.FACTORY_LINK);

        descs = queryUpdatedSince(startTime);

        int countAfterDesc1 = 2;
        assertEquals(countAfterDesc1, descs.size());
        long timeAfterDesc1 = Utils.getNowMicrosUtc();

        desc1 = new ContainerDescription();
        desc1.image = image2;
        doPost(desc1, ContainerDescriptionService.FACTORY_LINK);

        ContainerDescription desc3 = new ContainerDescription();
        desc3.documentSelfLink = "image2";
        desc3.image = image2;
        desc3 = doPost(desc3, ContainerDescriptionService.FACTORY_LINK);

        descs = queryUpdatedSince(startTime);

        int countAfterDesc2 = 2;
        assertEquals(countAfterDesc1 + countAfterDesc2, descs.size());
        long timeAfterDesc2 = Utils.getNowMicrosUtc();

        descs = queryUpdatedSince(timeAfterDesc1);
        assertEquals(countAfterDesc2, descs.size());

        boolean match = false;
        for (ContainerDescription state : descs) {
            if (desc3.documentSelfLink.equals(state.documentSelfLink)) {
                assertNotNull(desc3.image);
                match = true;
            }
        }
        assertTrue(match);

        descs = queryUpdatedSince(timeAfterDesc2);
        assertEquals(0, descs.size());

        long timeBeforeDeletion = Utils.getNowMicrosUtc();
        delete(desc2.documentSelfLink);
        descs = queryUpdatedSince(startTime);
        assertEquals(countAfterDesc1 + countAfterDesc2, descs.size());

        boolean deleted = false;
        for (ContainerDescription state : descs) {
            if (desc2.documentSelfLink.equals(state.documentSelfLink)) {
                assertTrue(ServiceDocument.isDeleted(state));
                deleted = true;
            }
        }
        assertTrue(deleted);
        delete(desc3.documentSelfLink);

        descs = queryUpdatedSince(timeBeforeDeletion);
        assertEquals(countAfterDesc1 + countAfterDesc2 - 2, descs.size());
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

        ContainerDescription desc = new ContainerDescription();
        desc.documentSelfLink = testSelfLink;
        desc.image = image1;
        desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);

        descs = queryUpdatedSince(startTime);

        assertEquals(1, descs.size());

        ContainerDescription updatedDesc = new ContainerDescription();
        updatedDesc.documentSelfLink = testSelfLink;
        updatedDesc.image = image2;
        updatedDesc = doPost(updatedDesc, ContainerDescriptionService.FACTORY_LINK);

        // update should still have only one document:
        descs = queryUpdatedSince(startTime);

        assertEquals(1, descs.size());
        assertEquals(image2, descs.get(0).image);

        delete(desc.documentSelfLink);
        descs = queryUpdatedSince(startTime);
        // again, only one entity but indicated as deleted
        assertEquals(1, descs.size());
        assertTrue(ServiceDocument.isDeleted(descs.get(0)));

        desc = new ContainerDescription();
        desc.documentSelfLink = testSelfLink;
        desc.image = image1;
        desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);

        descs = queryUpdatedSince(startTime);
        assertEquals(1, descs.size()); // expectation is to still have only one
        assertEquals(image1, desc.image);
    }

    @Test
    public void testQueryResultLimit() throws Throwable {
        final String queryTaskDocumentSelfLink = UriUtils.buildUriPath(
                ServiceUriPaths.CORE_QUERY_TASKS, "/testQueryTaskResultLimit");
        QuerySpecification qs = new QuerySpecification();
        qs.query = Query.Builder.create().addKindFieldClause(ContainerDescription.class).build();
        QueryTask qt = QueryTask.create(qs);
        qt.documentSelfLink = queryTaskDocumentSelfLink + 1;

        final AtomicReference<QueryTask> q = new AtomicReference<>();
        host.testStart(1);
        new ServiceDocumentQuery<>(host, ContainerDescription.class)
                .query(qt, handler(false, q, qt.documentSelfLink));
        host.testWait();
        qt = q.getAndSet(null);
        assertNotNull(qt);
        assertEquals(ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT,
                qt.querySpec.resultLimit);

        Integer resourceLimit = 1000;
        qs = new QuerySpecification();
        qs.query = Query.Builder.create().addKindFieldClause(ContainerDescription.class).build();
        qt = QueryTask.create(qs);
        qt.querySpec.resultLimit = resourceLimit;
        qt.documentSelfLink = queryTaskDocumentSelfLink + 2;
        host.testStart(1);
        new ServiceDocumentQuery<>(host, ContainerDescription.class)
                .query(qt, handler(false, q, qt.documentSelfLink));
        host.testWait();
        qt = q.getAndSet(null);
        assertNotNull(qt);
        assertEquals(resourceLimit, qt.querySpec.resultLimit);

        qs = new QuerySpecification();
        qs.query = Query.Builder.create().addKindFieldClause(ContainerDescription.class).build();
        qt = QueryTask.create(qs);
        QueryUtil.addCountOption(qt);
        qt.documentSelfLink = queryTaskDocumentSelfLink + 3;
        host.testStart(1);
        new ServiceDocumentQuery<>(host, ContainerDescription.class)
                .query(qt, handler(true, q, qt.documentSelfLink));
        host.testWait();
        qt = q.getAndSet(null);
        assertNotNull(qt);
        assertNull(qt.querySpec.resultLimit);
    }

    @Test
    public void testQueryTaskDeleted() throws Throwable {
        final String queryTaskDocumentSelfLink = UriUtils.buildUriPath(
                ServiceUriPaths.CORE_QUERY_TASKS, "/testQueryTaskResultLimit");
        final String queryTaskLink = queryTaskDocumentSelfLink + 1;
        QuerySpecification qs = new QuerySpecification();
        qs.query = Query.Builder.create().addKindFieldClause(ContainerDescription.class).build();
        QueryTask qt = QueryTask.create(qs);
        qt.documentSelfLink = queryTaskLink;
        int fiveSec = 5000000;
        qt.documentExpirationTimeMicros = ServiceUtils.getExpirationTimeFromNowInMicros(fiveSec);

        final AtomicReference<QueryTask> q = new AtomicReference<>();
        host.testStart(1);
        new ServiceDocumentQuery<>(host, ContainerDescription.class)
                .query(qt, handler(false, q, qt.documentSelfLink));
        host.testWait();
        qt = q.getAndSet(null);
        //validate query task exists
        assertNotNull(qt);
        // validate query task is deleted
        waitFor(() -> {
            QueryTask queryTask = getDocumentNoWait(QueryTask.class, queryTaskLink);
            return queryTask == null;
        });
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
        return handler(singleResult, null, null);
    }

    private Consumer<ServiceDocumentQueryElementResult<ContainerDescription>> handler(
            boolean singleResult, final AtomicReference<QueryTask> q, final String link) {
        descs.clear();
        return (r) -> {
            if (r.hasException()) {
                host.failIteration(r.getException());
                return;
            }

            if (q != null && q.get() == null) {
                try {
                    QueryTask queryTask = getDocumentNoWait(QueryTask.class, link);
                    q.set(queryTask);
                } catch (Throwable ignore) {
                }
            }

            if (r.hasResult()) {
                descs.add(r.getResult());

                if (singleResult) {
                    host.completeIteration();
                }
            } else {
                host.completeIteration();
            }
        };
    }

}
