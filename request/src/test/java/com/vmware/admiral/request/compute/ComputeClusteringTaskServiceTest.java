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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.request.ClusteringTaskService;
import com.vmware.admiral.request.ClusteringTaskService.ClusteringTaskState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

@Ignore
public class ComputeClusteringTaskServiceTest extends ComputeRequestBaseTest {

    private RequestBrokerState request;
    private ComputeDescription description;

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        createVmGuestCompute(true);
        description = createComputeDescription();
        description = doPost(description, ComputeDescriptionService.FACTORY_LINK);

        request = TestRequestStateFactory.createComputeRequestState();
        request.resourceDescriptionLink = description.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        request.resourceCount = 3;
        Map<String, String> customProp = new HashMap<>();
        customProp.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, "test");
        request.customProperties = customProp;
    }

    private ComputeDescription createComputeDescription() {
        ComputeDescription cd = new ComputeDescription();
        cd.id = UUID.randomUUID().toString();
        cd.name = "testVM";
        cd.instanceType = "small";
        cd.customProperties = new HashMap<>();
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                "linux");
        return cd;
    }

    @Test
    public void testContainerClusteringTaskServiceIncrementByOne()
            throws Throwable {

        // Set up a ContainerDescription with _cluster =2
        int clusterSize = 2;
        ComputeDescription clustered = TestRequestStateFactory.createDockerHostDescription();
        clustered.name = "clustered";

        clustered.customProperties.put(ComputeConstants.CUSTOM_PROP_CLUSTER_SIZE_KEY,
                String.valueOf(clusterSize));
        clustered.documentSelfLink = UUID.randomUUID().toString();
        clustered = doPost(clustered, ComputeDescriptionService.FACTORY_LINK);

        request.resourceDescriptionLink = clustered.documentSelfLink;
        request.resourceCount = 1;

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        List<ComputeState> computes = queryComputeByDescriptionLink(clustered.documentSelfLink);

        // Number of computes before provisioning.
        assertEquals(2, computes.size());

        // Create Day 2 operation for clustering containers. Set resource count to be 1+ the number
        // of resources in the cluster
        RequestBrokerState day2OperationClustering = TestRequestStateFactory
                .createComputeRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        int desiredResourceCount = clusterSize + 1;
        day2OperationClustering.resourceCount = desiredResourceCount;
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        day2OperationClustering = startRequest(day2OperationClustering);

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ClusteringTaskState.class);

        waitForRequestToComplete(day2OperationClustering);

        computes = queryComputeByDescriptionLink(clustered.documentSelfLink);
        assertEquals(desiredResourceCount /* 3 */, computes.size());

    }

    @Test
    public void testContainerClusteringTaskServiceIncrementByTwo()
            throws Throwable {

        // Set up a ContainerDescription with _cluster =2
        int clusterSize = 2;
        ComputeDescription clustered = TestRequestStateFactory.createDockerHostDescription();
        clustered.name = "clustered";

        clustered.customProperties.put(ComputeConstants.CUSTOM_PROP_CLUSTER_SIZE_KEY,
                String.valueOf(clusterSize));
        clustered.documentSelfLink = UUID.randomUUID().toString();
        clustered = doPost(clustered, ComputeDescriptionService.FACTORY_LINK);

        request.resourceDescriptionLink = clustered.documentSelfLink;
        request.resourceCount = 1;

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        List<ComputeState> computes = queryComputeByDescriptionLink(clustered.documentSelfLink);

        // Number of computes before provisioning.
        assertEquals(2, computes.size());

        // Create Day 2 operation for clustering containers. Set resource count to be 1+ the number
        // of resources in the cluster
        RequestBrokerState day2OperationClustering = TestRequestStateFactory
                .createComputeRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        int desiredResourceCount = clusterSize + 2;
        day2OperationClustering.resourceCount = desiredResourceCount;
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to clustered containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ClusteringTaskState.class);

        waitForRequestToComplete(day2OperationClustering);

        computes = queryComputeByDescriptionLink(clustered.documentSelfLink);
        assertEquals(desiredResourceCount /* 4 */, computes.size());
    }

    @Test
    public void testContainerClusteringTaskServiceAddContainers() throws Throwable {

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        // Number of containers before provisioning.
        assertEquals(3, initialState.resourceLinks.size());

        // Create Day 2 operation for clustering containers.
        RequestBrokerState day2OperationClustering = TestRequestStateFactory
                .createComputeRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        // Set 'resourceCount' to 5, which is 2 more than initial resources. This means that 2 new
        // containers should be provisioned.
        day2OperationClustering.resourceCount = 5;
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to clustered containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ClusteringTaskState.class);

        waitForRequestToComplete(day2OperationClustering);

        List<ComputeState> computes = queryComputeByDescriptionLink(
                initialState.resourceDescriptionLink);
        long containersNumberAfterClustering = computes.size();
        // Number of containers after clustering, should be increased with 7.
        assertEquals(5, containersNumberAfterClustering);

        GroupResourcePlacementService.GroupResourcePlacementPoolState placementState = getDocument(
                GroupResourcePlacementService.GroupResourcePlacementPoolState.class,
                groupPlacementState.documentSelfLink);

        assertEquals(5, placementState.availableInstancesCount);
        assertEquals(5, placementState.allocatedInstancesCount);
    }

    @Test
    public void testContainerClusteringTaskAddContainersServiceRemove()
            throws Throwable {

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        List<ComputeState> computes = queryComputeByDescriptionLink(
                initialState.resourceDescriptionLink);

        // Number of containers before provisioning.
        assertEquals(3, computes.size());

        // Create Day 2 operation for clustering containers.
        RequestBrokerState day2OperationClustering = TestRequestStateFactory
                .createComputeRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        // Decrease to 2
        day2OperationClustering.resourceCount = 2;
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to cluster containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ClusteringTaskState.class);

        computes = queryComputeByDescriptionLink(initialState.resourceDescriptionLink);
        // Number of computes after clustering, should 2.
        assertEquals(2, computes.size());
    }

    protected List<ComputeState> queryComputeByDescriptionLink(
            String descriptionLink) {

        QueryTask q = QueryUtil.buildQuery(ComputeState.class, false);
        QueryTask.Query containerHost = new QueryTask.Query()
                .setTermPropertyName(ComputeState.FIELD_NAME_DESCRIPTION_LINK)
                .setTermMatchValue(descriptionLink);
        containerHost.occurance = Occurance.MUST_OCCUR;

        q.querySpec.query.addBooleanClause(containerHost);

        QueryUtil.addExpandOption(q);
        ServiceDocumentQuery<ComputeState> query = new ServiceDocumentQuery<>(host,
                ComputeState.class);

        List<ComputeState> result = new ArrayList<>();
        TestContext ctx = testCreate(1);

        query.query(q, (r) -> {
            if (r.hasException()) {
                ctx.failIteration(r.getException());
            } else if (r.hasResult()) {
                result.add(r.getResult());
            } else {
                ctx.completeIteration();
            }
        });

        ctx.await();

        return result;
    }
}
