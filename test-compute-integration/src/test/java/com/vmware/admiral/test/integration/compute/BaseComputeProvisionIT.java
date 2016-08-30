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

package com.vmware.admiral.test.integration.compute;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.test.BaseTestCase.TestWaitForHandler;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.GroupResourcePolicyService;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.compute.endpoint.EndpointService;
import com.vmware.admiral.compute.endpoint.EndpointService.EndpointState;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.ReservationRemovalTaskFactoryService;
import com.vmware.admiral.request.ReservationRemovalTaskService.ReservationRemovalTaskState;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.test.integration.BaseIntegrationSupportIT;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public abstract class BaseComputeProvisionIT extends BaseIntegrationSupportIT {

    private static final String VMS_RESOURCE_POOL_ID = "vms-resource-pool";
    private static final String RESOURCE_POOL_ID = "hosts-resource-pool";
    private static final String ENDPOINT_ID = "endpoint";

    private static final String TENANT_LINKS_KEY = "test.tenant.links";

    public static enum EndpointType {
        aws,
        azure,
        gpc,
        vsphere;
    }

    private static final String SUFFIX = "bel10";
    private final Set<ComputeState> computesToDelete = new HashSet<>();
    private GroupResourcePolicyState groupResourcePolicyState;
    private EndpointType endpointType;
    protected final TestDocumentLifeCycle documentLifeCycle = TestDocumentLifeCycle.FOR_DELETE;
    protected ResourcePoolState vmsResourcePool;
    private List<String> tenantLinks;

    @Before
    public void setUp() throws Exception {

        endpointType = getEndpointType();
        EndpointState endpoint = createEndpoint(endpointType, documentLifeCycle);
        ResourcePoolState poolState = createResourcePool(endpointType, endpoint, documentLifeCycle);
        groupResourcePolicyState = createResourcePolicy("host-policy", endpointType, poolState,
                documentLifeCycle);
        vmsResourcePool = createResourcePoolOfVMs(endpointType, documentLifeCycle);
        doSetUp();
    }

    protected void doSetUp() throws Exception {
    }

    @Override
    public void baseTearDown() throws Exception {
        for (ComputeState compute : computesToDelete) {
            try {
                logger.info("---------- Clean up: Request Delete the compute instance: %s --------",
                        compute.documentSelfLink);
                delete(compute);
                cleanupReservation(compute);
            } catch (Throwable t) {
                logger.warning(
                        String.format("Unable to remove compute %s: %s", compute.documentSelfLink,
                                t.getMessage()));
            }
        }

        super.baseTearDown();
    }

    private void cleanupReservation(ComputeState compute) throws Exception {
        if (groupResourcePolicyState == null) {
            // no group quata
            return;
        }
        ReservationRemovalTaskState task = new ReservationRemovalTaskState();
        task.resourceDescriptionLink = compute.descriptionLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task.groupResourcePolicyLink = groupResourcePolicyState.documentSelfLink;

        task = postDocument(ReservationRemovalTaskFactoryService.SELF_LINK, task);
        assertNotNull(task);

        waitForStateChange(
                task.documentSelfLink,
                (body) -> {
                    ReservationRemovalTaskState state = Utils.fromJson(body,
                            ReservationRemovalTaskState.class);
                    if (state.taskInfo.stage.equals(TaskStage.FINISHED)) {
                        return true;
                    } else if (state.taskInfo.stage.equals(TaskStage.FAILED)) {
                        fail("Reservation clean up error: " + state.taskInfo.failure);
                        return true;
                    } else {
                        return false;
                    }
                });
    }

    protected abstract EndpointType getEndpointType();

    protected abstract void extendEndpoint(EndpointState endpoint);

    protected abstract void extendComputeDescription(ComputeDescription computeDescription)
            throws Exception;

    @Test
    public void testProvision() throws Throwable {

        ComputeDescription computeDescription = createComputeDescription(endpointType,
                documentLifeCycle);

        RequestBrokerState allocateRequest = requestCompute(computeDescription, true, null);

        allocateRequest = getDocument(allocateRequest.documentSelfLink, RequestBrokerState.class);

        assertNotNull(allocateRequest.resourceLinks);
        System.out.println(allocateRequest.resourceLinks);
        for (String link : allocateRequest.resourceLinks) {
            ComputeState computeState = getDocument(link, ComputeState.class);
            assertNotNull(computeState);
            computesToDelete.add(computeState);
        }

        RequestBrokerState provisionRequest = requestCompute(computeDescription, false,
                allocateRequest.resourceLinks);

        provisionRequest = getDocument(provisionRequest.documentSelfLink, RequestBrokerState.class);
        assertNotNull(provisionRequest);
        assertNotNull(provisionRequest.resourceLinks);
        try {
            doWithResources(provisionRequest.resourceLinks);
        } finally {
            // create a host removal task - RequestBroker
            RequestBrokerState deleteRequest = new RequestBrokerState();
            deleteRequest.resourceType = ResourceType.COMPUTE_TYPE.getName();
            deleteRequest.resourceLinks = provisionRequest.resourceLinks;
            deleteRequest.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
            RequestBrokerState cleanupRequest = postDocument(RequestBrokerFactoryService.SELF_LINK,
                    deleteRequest);

            waitForTaskToComplete(cleanupRequest.documentSelfLink);
        }
    }

    protected void doWithResources(List<String> resourceLinks) throws Throwable {
    }

    private RequestBrokerState requestCompute(ComputeDescription computeDescription,
            boolean allocation, List<String> resourceLinks)
            throws Exception {
        RequestBrokerState requestBrokerState = new RequestBrokerState();
        requestBrokerState.resourceType = ResourceType.COMPUTE_TYPE.getName();
        requestBrokerState.resourceCount = 1;
        requestBrokerState.resourceDescriptionLink = computeDescription.documentSelfLink;
        requestBrokerState.resourceLinks = resourceLinks;
        requestBrokerState.tenantLinks = getTenantLinks();
        requestBrokerState.customProperties = new HashMap<>();
        if (allocation) {
            requestBrokerState.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                    "true");
        } else {
            requestBrokerState.operation = ContainerOperationType.CREATE.id;
        }

        RequestBrokerState request = postDocument(RequestBrokerFactoryService.SELF_LINK,
                requestBrokerState);

        waitForTaskToComplete(request.documentSelfLink);
        return request;
    }

    private String getLink(String factoryLink, String name) {
        return UriUtils.buildUriPath(factoryLink, name);
    }

    protected String name(EndpointType endpointType, String prefix, String suffix) {
        return String.format("%s-%s-%s", prefix, endpointType.name(), suffix);
    }

    private EndpointState createEndpoint(EndpointType endpointType,
            TestDocumentLifeCycle documentLifeCycle)
            throws Exception {
        String name = name(endpointType, ENDPOINT_ID, SUFFIX);
        EndpointState endpoint = new EndpointState();
        endpoint.documentSelfLink = getLink(EndpointService.FACTORY_LINK, name);
        endpoint.endpointType = endpointType.name();
        endpoint.name = name;
        endpoint.tenantLinks = getTenantLinks();
        extendEndpoint(endpoint);

        return postDocument(EndpointService.FACTORY_LINK, endpoint,
                documentLifeCycle);
    }

    protected List<String> getTenantLinks() {
        if (this.tenantLinks == null) {
            String tenantLinkProp = getTestProp(TENANT_LINKS_KEY, "/tenants/admiral");
            String[] values = StringUtils.split(tenantLinkProp, ',');

            List<String> result = new LinkedList<>();
            for (int i = 0; i < values.length; i++) {
                result.add(values[i].trim());
            }
            this.tenantLinks = result;
        }
        return tenantLinks;
    }

    protected ResourcePoolState createResourcePool(EndpointType endpointType,
            EndpointState endpoint, TestDocumentLifeCycle documentLifeCycle) throws Exception {
        return createResourcePool(endpointType, endpoint, RESOURCE_POOL_ID, documentLifeCycle);
    }

    protected ResourcePoolState createResourcePool(EndpointType endpointType,
            EndpointState endpoint, String poolId, TestDocumentLifeCycle documentLifeCycle)
            throws Exception {
        String name = name(endpointType, poolId, SUFFIX);
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.documentSelfLink = getLink(ResourcePoolService.FACTORY_LINK, name);
        poolState.name = name;
        poolState.id = poolState.name;
        poolState.projectName = endpointType.name();
        poolState.tenantLinks = getTenantLinks();
        poolState.maxCpuCount = 1600;
        poolState.minCpuCount = 16;
        poolState.currencyUnit = "USD";
        poolState.maxCpuCostPerMinute = 1.0;
        poolState.maxDiskCostPerMinute = 1.0;
        poolState.minMemoryBytes = 1024L * 1024L * 1024L * 46L;
        poolState.maxMemoryBytes = poolState.minMemoryBytes * 2;
        poolState.minDiskCapacityBytes = poolState.maxDiskCapacityBytes = 1024L * 1024L * 1024L
                * 1024L;
        poolState.customProperties = new HashMap<>();
        if (endpoint != null) {
            poolState.customProperties.put(
                    ComputeConstants.ENDPOINT_LINK_PROP_NAME, endpoint.documentSelfLink);
        }

        ResourcePoolState resourcePoolState = postDocument(ResourcePoolService.FACTORY_LINK,
                poolState, documentLifeCycle);

        assertNotNull(resourcePoolState);

        return resourcePoolState;
    }

    private ResourcePoolState createResourcePoolOfVMs(EndpointType endpointType,
            TestDocumentLifeCycle documentLifeCycle) throws Exception {
        return createResourcePool(endpointType, null, VMS_RESOURCE_POOL_ID, documentLifeCycle);
    }

    private ComputeDescription createComputeDescription(EndpointType endpointType,
            TestDocumentLifeCycle documentLifeCycle)
            throws Exception {
        String id = name(endpointType, "test", UUID.randomUUID().toString());
        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc.id = id;
        computeDesc.name = "small";
        computeDesc.tenantLinks = getTenantLinks();
        computeDesc.customProperties = new HashMap<>();
        computeDesc.customProperties.put(ComputeProperties.CUSTOM_DISPLAY_NAME,
                "belvm" + System.currentTimeMillis());
        computeDesc.customProperties
                .put(ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_IMAGE_ID_NAME, "linux");

        computeDesc.customProperties.put(
                ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK,
                vmsResourcePool.documentSelfLink);

        extendComputeDescription(computeDesc);

        ComputeDescription computeDescription = postDocument(ComputeDescriptionService.FACTORY_LINK,
                computeDesc, documentLifeCycle);

        return computeDescription;
    }

    protected GroupResourcePolicyState createResourcePolicy(String name, EndpointType endpointType,
            ResourcePoolState poolState, TestDocumentLifeCycle documentLifeCycle)
            throws Exception {
        GroupResourcePolicyState policyState = new GroupResourcePolicyState();
        policyState.maxNumberInstances = 30;
        policyState.resourcePoolLink = poolState.documentSelfLink;
        policyState.name = name(endpointType, name, SUFFIX);
        policyState.documentSelfLink = policyState.name;
        policyState.availableInstancesCount = 1000000;
        policyState.priority = 1;
        policyState.tenantLinks = getTenantLinks();

        GroupResourcePolicyState currentQuata = getDocument(
                getLink(GroupResourcePolicyService.FACTORY_LINK, policyState.name),
                GroupResourcePolicyState.class);
        if (currentQuata != null) {
            return currentQuata;
        }
        GroupResourcePolicyState resourcePolicyState = postDocument(
                GroupResourcePolicyService.FACTORY_LINK, policyState, documentLifeCycle);

        assertNotNull(resourcePolicyState);

        return resourcePolicyState;
    }

    protected static void waitFor(TestWaitForHandler handler) throws Throwable {
        waitFor("Failed waiting for condition... ", handler);
    }

    protected static void waitFor(String errorMessage, TestWaitForHandler handler)
            throws Throwable {
        int iterationCount = TASK_CHANGE_WAIT_POLLING_RETRY_COUNT;
        Thread.sleep(STATE_CHANGE_WAIT_POLLING_PERIOD_MILLIS / 5);
        for (int i = 0; i < iterationCount; i++) {
            if (handler.test()) {
                return;
            }

            Thread.sleep(STATE_CHANGE_WAIT_POLLING_PERIOD_MILLIS);
        }
        fail(errorMessage);
    }

}
