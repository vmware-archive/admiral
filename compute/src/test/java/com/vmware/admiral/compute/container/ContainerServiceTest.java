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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class ContainerServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
    }

    @Test
    public void testContainerServices() throws Throwable {
        verifyService(
                ContainerFactoryService.class,
                ContainerState.class,
                (prefix, index) -> {
                    ContainerState containerState = new ContainerState();
                    containerState.id = prefix + "id" + index;
                    containerState.names = new ArrayList<>(Arrays.asList(prefix + "name" + index));
                    containerState.command = new String[] { "cat" };
                    containerState.adapterManagementReference = URI
                            .create("http://remote-host:8082/docker-executor" + index);
                    containerState.address = "http://docker:5432/" + index;
                    containerState.descriptionLink = UriUtils.buildUriPath(
                            ContainerDescriptionService.FACTORY_LINK, "docker-nginx");
                    containerState.customProperties = new HashMap<>();
                    containerState.powerState = ContainerState.PowerState.RUNNING;

                    return containerState;
                },
                (prefix, serviceDocument) -> {
                    ContainerState containerState = (ContainerState) serviceDocument;
                    assertTrue(containerState.id.startsWith(prefix + "id"));
                    assertTrue(containerState.names.get(0).startsWith(prefix + "name"));
                    assertArrayEquals(new String[] { "cat" }, containerState.command);
                    assertTrue(containerState.adapterManagementReference.toString().startsWith(
                            "http://remote-host:8082/docker-executor"));
                    assertTrue(containerState.address.startsWith("http://docker:5432/"));
                    assertEquals(UriUtils.buildUriPath(
                            ContainerDescriptionService.FACTORY_LINK, "docker-nginx"),
                            containerState.descriptionLink);
                    assertEquals(ContainerState.PowerState.RUNNING, containerState.powerState);
                });
    }

    @Test
    public void testODataFilter() throws Throwable {
        createContainer("/parent/1", "tenant1");
        createContainer("/parent/2", "tenant2");
        createContainer("/parent/2", "tenant2::businessGroup1");
        createContainer("/parent/2", "tenant3");
        createContainer("/parent/3", "tenant4");

        oDataQueryContainers("", 5);

        oDataQueryContainers("$filter=parentLink eq '/parent/2'", 3);

        oDataQueryContainers("$filter=tenantLinks.item eq 'tenant2*'", 2);

        oDataQueryContainers("$filter=tenantLinks.item eq 'tenant3' and parentLink eq '/parent/2'", 1);

        oDataQueryContainers("$filter=parentLink eq '/parent/1' and parentLink ne '/parent/2'", 1);

        oDataQueryContainers("$filter=parentLink ne '/parent/x' and parentLink ne '/parent/y'", 5);

        oDataQueryContainers("$filter=parentLink ne '/parent/1' or parentLink ne '/parent/x'", 5);

        oDataQueryContainers("$filter=parentLink ne '/parent/1'", 4);

        // wrong query because of https://www.pivotaltracker.com/projects/1471320/stories/118558279
        oDataQueryContainers("$filter=parentLink eq '/parent/1' or parentLink ne '/parent/x'", 1);

        // alternative working query, e.g. A and (B and Not C), according to
        // https://github.com/vmware/xenon/wiki/QueryTaskService#odata-filter-for-complex-queries
        oDataQueryContainers(
                "$filter=parentLink eq '/parent/1' or (parentLink eq '/parent/*' and parentLink ne '/parent/x')",
                5);

        oDataQueryContainers("$filter=parentLink eq '/parent/1' or parentLink eq '/parent/2'", 4);

        oDataQueryContainers("$filter=parentLink ne '/parent/x' and parentLink ne '/parent/1'", 4);
    }

    @Test
    public void testODataWithManyEntities() throws Throwable {
        int numberOfContainers = 6000;
        List<String> containers = new ArrayList<>();
        for (int i = 0; i <= numberOfContainers; i++) {
            ContainerState state = createContainer("/parent/1", "tenant1");
            containers.add(state.documentSelfLink);
        }

        for (int i = 0; i <= numberOfContainers; i++) {
            ContainerState containerState = new ContainerState();
            containerState.customProperties = new HashMap<String, String>();
            containerState.customProperties.put("keyTestProp", "valueTestProp");
            doPatch(containerState, containers.get(i));
        }
        queryContainers(numberOfContainers + 1);
        countQueryContainers(numberOfContainers + 1);
    }

    @Test
    public void testPatchExpiration() throws Throwable {
        ContainerState container = createContainer("/parent/1", "tenant1");
        URI containerUri = UriUtils.buildUri(host, container.documentSelfLink);

        ContainerState patch = new ContainerState();
        long nowMicrosUtc = Utils.getNowMicrosUtc() + TimeUnit.SECONDS.toMicros(30);
        patch.documentExpirationTimeMicros = nowMicrosUtc;

        doOperation(patch, containerUri, false, Action.PATCH);
        ContainerState updatedContainer = getDocument(ContainerState.class,
                container.documentSelfLink);
        assertEquals(nowMicrosUtc, updatedContainer.documentExpirationTimeMicros);

        patch = new ContainerState();
        patch.documentExpirationTimeMicros = -1;

        doOperation(patch, containerUri, false, Action.PATCH);
        updatedContainer = getDocument(ContainerState.class, container.documentSelfLink);
        assertEquals(0, updatedContainer.documentExpirationTimeMicros);
    }

    private ContainerState createContainer(String parentLink, String group) throws Throwable {
        ContainerState containerState = new ContainerState();
        containerState.id = UUID.randomUUID().toString();
        containerState.names = new ArrayList<>(Arrays.asList("name_" + containerState.id));
        containerState.command = new String[] { "cat" };
        containerState.adapterManagementReference = URI
                .create("http://remote-host:8082/docker-executor");
        containerState.address = "http://docker:5432/";
        containerState.descriptionLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, "docker-nginx");
        containerState.customProperties = new HashMap<>();
        containerState.powerState = ContainerState.PowerState.RUNNING;
        containerState.parentLink = parentLink;
        containerState.tenantLinks = Collections.singletonList(group);

        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);

        return containerState;
    }

    private void queryContainers(int expectedCount) throws Throwable {
        AtomicInteger ac = new AtomicInteger(0);
        QuerySpecification qs = new QuerySpecification();
        qs.query = Query.Builder.create().addKindFieldClause(ContainerState.class).build();
        QueryTask qt = QueryTask.create(qs);
        host.testStart(1);
        new ServiceDocumentQuery<>(
                host, null).query(qt,
                        (r) -> {
                            if (r.hasException()) {
                                host.failIteration(r.getException());
                                return;
                            }
                            if (r.hasResult()) {
                                ac.incrementAndGet();
                                return;
                            }
                            host.completeIteration();
                        });
        host.testWait();
        assertEquals(expectedCount, ac.get());
    }

    private void countQueryContainers(int expectedCount) throws Throwable {
        List<Long> list = new ArrayList<>();
        QuerySpecification qs = new QuerySpecification();
        qs.query = Query.Builder.create().addKindFieldClause(ContainerState.class).build();
        QueryTask qt = QueryTask.create(qs);
        QueryUtil.addCountOption(qt);
        host.testStart(1);
        new ServiceDocumentQuery<>(
                host, null).query(qt,
                        (r) -> {
                            if (r.hasException()) {
                                host.failIteration(r.getException());
                                return;
                            }
                            list.add(r.getCount());
                            host.completeIteration();
                        });
        host.testWait();
        assertTrue(expectedCount == list.get(0));
    }

    private void oDataQueryContainers(String query, int expectedCount) throws Throwable {
        List<String> list = new ArrayList<>();
        URI uri = UriUtils.buildUri(host, ContainerFactoryService.SELF_LINK, query);
        host.testStart(1);
        Operation op = Operation
                .createGet(uri)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.failIteration(e);
                                return;
                            }

                            ServiceDocumentQueryResult result = o
                                    .getBody(ServiceDocumentQueryResult.class);
                            list.addAll(result.documentLinks);
                            host.completeIteration();
                        });
        host.send(op);
        host.testWait();
    }
}
