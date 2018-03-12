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

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.ContainerOperationTaskService.ContainerOperationTaskState;
import com.vmware.admiral.request.ContainerRemovalTaskService.ContainerRemovalTaskState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

public class ContainerOperationTaskServiceTest extends RequestBaseTest {

    private RequestBrokerState request;

    @Override
    public void setUp() throws Throwable {
        super.setUp();

        request = TestRequestStateFactory.createRequestState();
        request.resourceDescriptionLink = containerDesc.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        request.resourceCount = 2;
    }

    @Test
    public void testContainerResourceOperationCycle() throws Throwable {
        host.log("########  testContainerResourceOperationCycle ######## ");
        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);
        assertNotNull(request);
        assertNotNull(request.documentSelfLink);

        // verify the resources are created as expected:
        assertNotNull("Request resourceLinks null for requestId:  " + request.documentSelfLink,
                request.resourceLinks);
        assertEquals(request.resourceCount, request.resourceLinks.size());

        host.log("wait for containers to be in running state for request: "
                + request.documentSelfLink);
        waitForContainerPowerState(PowerState.RUNNING, request.resourceLinks);

        RequestBrokerState day2StopRequest = new RequestBrokerState();
        day2StopRequest.resourceType = request.resourceType;
        day2StopRequest.resourceLinks = request.resourceLinks;
        day2StopRequest.operation = ContainerOperationType.STOP.id;

        day2StopRequest = startRequest(day2StopRequest);

        String containerOperationTaskLink = UriUtils.buildUriPath(
                ContainerOperationTaskFactoryService.SELF_LINK,
                extractId(day2StopRequest.documentSelfLink));
        waitForTaskSuccess(containerOperationTaskLink, ContainerOperationTaskState.class);

        waitForRequestToComplete(day2StopRequest);

        // verify the resources have been stopped:
        waitForContainerPowerState(PowerState.STOPPED, request.resourceLinks);
    }

    @Test
    public void testCompositeContainerResourceOperationCycle() throws Throwable {
        host.log("########  testCompositeContainerResourceOperationCycle ######## ");
        CompositeDescription composite = new CompositeDescription();
        composite.name = "Composite day2OpsTest";
        composite.descriptionLinks = new ArrayList<String>();
        composite.descriptionLinks.add(containerDesc.documentSelfLink);
        composite = doPost(composite, CompositeDescriptionService.FACTORY_LINK);

        request = new RequestBrokerState();
        request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        request.resourceDescriptionLink = composite.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        request.customProperties = new HashMap<>();
        request.resourceCount = 1;

        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);
        assertNotNull(request);
        assertNotNull(request.documentSelfLink);

        // verify the resources are created as expected:
        assertNotNull("Request resourceLinks null for requestId:  " + request.documentSelfLink,
                request.resourceLinks);

        assertEquals(request.resourceCount, request.resourceLinks.size());

        CompositeComponent cc = getDocument(CompositeComponent.class, request.resourceLinks.iterator().next());

        host.log("wait for containers to be in running state for request: "
                + request.documentSelfLink);
        waitForContainerPowerState(PowerState.RUNNING, cc.componentLinks);

        // Get composite component link
        ContainerState container = getDocument(ContainerState.class, cc.componentLinks.get(0));

        RequestBrokerState day2Request = new RequestBrokerState();
        day2Request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        day2Request.resourceLinks = new HashSet<String>();
        day2Request.resourceLinks.add(container.compositeComponentLink);
        day2Request.operation = ContainerOperationType.STOP.id;

        day2Request = startRequest(day2Request);

        String containerOperationTaskLink = UriUtils.buildUriPath(
                ContainerOperationTaskFactoryService.SELF_LINK,
                extractId(day2Request.documentSelfLink));
        waitForTaskSuccess(containerOperationTaskLink, ContainerOperationTaskState.class);

        waitForRequestToComplete(day2Request);

        // verify the resources have been stopped:
        waitForContainerPowerState(PowerState.STOPPED, cc.componentLinks);

        day2Request = new RequestBrokerState();
        day2Request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        day2Request.resourceLinks = new HashSet<String>();
        day2Request.resourceLinks.add(container.compositeComponentLink);
        day2Request.operation = ContainerOperationType.START.id;

        day2Request = startRequest(day2Request);

        containerOperationTaskLink = UriUtils.buildUriPath(
                ContainerOperationTaskFactoryService.SELF_LINK,
                extractId(day2Request.documentSelfLink));
        waitForTaskSuccess(containerOperationTaskLink, ContainerOperationTaskState.class);

        waitForRequestToComplete(day2Request);

        // verify the resources have been started:
        waitForContainerPowerState(PowerState.RUNNING, cc.componentLinks);

    }

    @Test
    public void testFailureInProvisionOperationShouldCleanReservations() throws Throwable {
        host.log("########  testFailureInProvisionOperationShouldCleanReservations ######## ");
        request.customProperties = new HashMap<>();
        request.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                Boolean.TRUE.toString());
        host.log("########  Start Allocation Request ######## ");
        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);
        assertNotNull(request);

        // verify the resources are created as expected:
        assertEquals(request.resourceCount, request.resourceLinks.size());
        Collection<ContainerState> containerStates = findResources(ContainerState.class,
                request.resourceLinks);
        assertEquals(request.resourceCount, containerStates.size());

        waitForContainerPowerState(PowerState.PROVISIONING, request.resourceLinks);

        // verify the placements has been reserved:
        GroupResourcePlacementState groupResourcePlacement = getDocument(GroupResourcePlacementState.class,
                request.groupResourcePlacementLink);
        assertNotNull(groupResourcePlacement);
        assertEquals(groupResourcePlacement.allocatedInstancesCount, request.resourceCount);

        host.log("########  Start Day2 Provisioning Request ######## ");

        RequestBrokerState day2ProvisionRequest = new RequestBrokerState();
        day2ProvisionRequest.resourceType = request.resourceType;
        day2ProvisionRequest.resourceLinks = request.resourceLinks;
        day2ProvisionRequest.operation = ContainerOperationType.CREATE.id;
        day2ProvisionRequest.resourceDescriptionLink = containerStates.iterator().next().descriptionLink;
        day2ProvisionRequest.customProperties = new HashMap<>();
        day2ProvisionRequest.customProperties.put(MockDockerAdapterService.FAILURE_EXPECTED,
                "simulate failure");

        day2ProvisionRequest = startRequest(day2ProvisionRequest);

        String containerAllocationTaskLink = UriUtils.buildUriPath(
                ContainerAllocationTaskFactoryService.SELF_LINK,
                extractId(day2ProvisionRequest.documentSelfLink));

        // wait for container provisioning to complete
        waitForTaskError(containerAllocationTaskLink, ContainerAllocationTaskState.class);

        // wait for the request to complete
        waitForRequestToFail(day2ProvisionRequest);

        ContainerRemovalTaskState removalRequest = getDocument(ContainerRemovalTaskState.class,
                UriUtils.buildUriPath(ContainerRemovalTaskFactoryService.SELF_LINK,
                        extractId(day2ProvisionRequest.documentSelfLink)));
        assertNotNull(removalRequest);

        // wait for the removal request to complete as well (it is asynch completion)
        waitForTaskSuccess(removalRequest.documentSelfLink, ContainerRemovalTaskState.class);

        // verify the resources have been removed:
        containerStates = findResources(ContainerState.class, request.resourceLinks);
        assertTrue("containerStates not cleaned for request id:  "
                + day2ProvisionRequest.documentSelfLink, containerStates.isEmpty());

        // verified the placements have been released:
        groupResourcePlacement = getDocument(GroupResourcePlacementState.class,
                request.groupResourcePlacementLink);
        assertNotNull(groupResourcePlacement);
        assertEquals(groupResourcePlacement.allocatedInstancesCount, 0);
    }

    private Collection<ContainerState> findResources(Class<? extends ServiceDocument> type,
            Collection<String> resourceLinks) throws Throwable {
        QueryTask query = QueryUtil.buildQuery(type, true);
        QueryTask.Query resourceLinkClause = new QueryTask.Query();
        for (String resourceLink : resourceLinks) {
            if (ComputeState.class == type) {
                // assumptions is that the selfLinks id of ContainerState and ComputeState are the
                // same.
                resourceLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK,
                        extractId(resourceLink));
            }
            QueryTask.Query rlClause = new QueryTask.Query()
                    .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                    .setTermMatchValue(resourceLink);

            rlClause.occurance = Occurance.SHOULD_OCCUR;
            resourceLinkClause.addBooleanClause(rlClause);
        }
        query.querySpec.query.addBooleanClause(resourceLinkClause);
        query.querySpec.options = EnumSet.of(QueryOption.EXPAND_CONTENT);

        List<ContainerState> containers = new ArrayList<>();
        new ServiceDocumentQuery<>(
                host, null).query(query,
                        (r) -> {
                            if (r.hasException()) {
                                host.failIteration(r.getException());
                                return;
                            } else if (r.hasResult()) {
                                ContainerState container = Utils.fromJson(r.getRawResult(),
                                        ContainerState.class);
                                containers.add(container);
                            } else {
                                host.completeIteration();
                            }
                        });
        host.testStart(1);
        host.testWait();

        return containers;
    }

    private void waitForContainerPowerState(final PowerState expectedPowerState,
            Collection<String> containerLinks) throws Throwable {
        assertNotNull(containerLinks);
        waitFor(() -> {
            Collection<ContainerState> containerStates = findResources(ContainerState.class,
                    containerLinks);
            assertNotNull(containerStates);
            assertEquals(containerLinks.size(), containerStates.size());
            for (ContainerState containerState : containerStates) {
                if (containerState.powerState == expectedPowerState) {
                    continue;
                }
                host.log(
                        "Container PowerState is: %s. Expected powerState: %s. Retrying for container: %s...",
                        containerState.powerState, expectedPowerState,
                        containerState.documentSelfLink);
                return false;
            }
            return true;
        });
    }
}