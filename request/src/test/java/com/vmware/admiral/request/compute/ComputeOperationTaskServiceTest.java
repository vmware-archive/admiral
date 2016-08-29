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
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.EnvironmentMappingService;
import com.vmware.admiral.compute.EnvironmentMappingService.EnvironmentMappingState;
import com.vmware.admiral.compute.PropertyMapping;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.endpoint.EndpointService;
import com.vmware.admiral.compute.endpoint.EndpointService.EndpointState;
import com.vmware.admiral.compute.endpoint.EndpointType;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.compute.ComputeOperationTaskService.ComputeOperationTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class ComputeOperationTaskServiceTest extends RequestBaseTest {

    private RequestBrokerState request;
    private List<String> documentLinksForDeletion;

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        documentLinksForDeletion = new ArrayList<>();
        DeploymentProfileConfig.getInstance().setTest(true);

        // create a single powered-on compute available for placement
        createVmGuestCompute(true);

        createVSphereEnv();

        request = TestRequestStateFactory.createRequestState(ResourceType.COMPUTE_TYPE.getName(),
                hostDesc.documentSelfLink);
        request.tenantLinks = computeGroupPolicyState.tenantLinks;
        request.resourceCount = 1;
    }

    @After
    public void tearDown() throws Throwable {
        for (String selfLink : documentLinksForDeletion) {
            delete(selfLink);
        }
        DeploymentProfileConfig.getInstance().setTest(false);
    }

    @Test
    public void testComputeResourceOperationCycle() throws Throwable {
        host.log("########  testComputeResourceOperationCycle ######## ");
        RequestBrokerState provisionRequest = provisionComputes();

        host.log("wait for computes to be in on state for request: "
                + request.documentSelfLink);
        waitForComputePowerState(PowerState.UNKNOWN, provisionRequest.resourceLinks);

        RequestBrokerState day2StopRequest = new RequestBrokerState();
        day2StopRequest.resourceType = provisionRequest.resourceType;
        day2StopRequest.resourceLinks = provisionRequest.resourceLinks;
        day2StopRequest.operation = ComputeOperationType.POWER_OFF.id;

        day2StopRequest = startRequest(day2StopRequest);

        String computeOperationTaskLink = UriUtils.buildUriPath(
                ComputeOperationTaskService.FACTORY_LINK,
                extractId(day2StopRequest.documentSelfLink));

        waitForTaskSuccess(computeOperationTaskLink, ComputeOperationTaskState.class);
        waitForRequestToComplete(day2StopRequest);

        // verify the resources have been stopped:
        waitForComputePowerState(PowerState.OFF, request.resourceLinks);
    }

    @Override
    protected synchronized EndpointState createEndpoint() throws Throwable {
        synchronized (initializationLock) {
            if (endpoint == null) {
                endpoint = TestRequestStateFactory.createEndpoint();
                endpoint.endpointType = getEndpointType().name();
                endpoint.endpointHost = "https://somehost";
                endpoint = getOrCreateDocument(endpoint, EndpointService.FACTORY_LINK);
                assertNotNull(endpoint);
            }
            return endpoint;
        }
    }

    private RequestBrokerState provisionComputes() throws Throwable {
        request.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST, "true");
        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);
        assertNotNull(request);

        // verify the resources are created as expected:
        assertEquals(request.resourceCount, request.resourceLinks.size());
        List<String> computeStateLinks = findResourceLinks(ComputeState.class,
                request.resourceLinks);

        assertNotNull("ComputeStates were not allocated", computeStateLinks);
        assertEquals(request.resourceCount, computeStateLinks.size());

        // provision compute task - RequestBroker
        RequestBrokerState provisionRequest = new RequestBrokerState();
        provisionRequest.resourceType = ResourceType.COMPUTE_TYPE.getName();
        provisionRequest.resourceLinks = computeStateLinks;
        provisionRequest.operation = ComputeOperationType.CREATE.id;

        provisionRequest = startRequest(provisionRequest);

        return waitForRequestToComplete(provisionRequest);
    }

    private Collection<ComputeState> findResources(Class<? extends ServiceDocument> type,
            List<String> resourceLinks) throws Throwable {
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

        QueryTask[] result = new QueryTask[] { null };
        Operation post = Operation
                .createPost(UriUtils.buildUri(host, ServiceUriPaths.CORE_QUERY_TASKS))
                .setBody(query).setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.failIteration(e);
                                return;
                            }
                            result[0] = o.getBody(QueryTask.class);
                            host.completeIteration();
                        });
        host.testStart(1);
        host.send(post);
        host.testWait();

        List<ComputeState> computes = new ArrayList<>();
        for (Object json : result[0].results.documents.values()) {
            ComputeState container = Utils.fromJson(json, ComputeState.class);
            computes.add(container);
        }
        return computes;
    }

    private void waitForComputePowerState(final PowerState expectedPowerState,
            List<String> computeLinks) throws Throwable {
        assertNotNull(computeLinks);
        waitFor(() -> {
            Collection<ComputeState> computeStates = findResources(ComputeState.class,
                    computeLinks);
            assertNotNull(computeStates);
            assertEquals(computeLinks.size(), computeStates.size());
            for (ComputeState computeState : computeStates) {
                if (computeState.powerState == expectedPowerState) {
                    continue;
                }
                host.log(
                        "Container PowerState is: %s. Expected powerState: %s. Retrying for container: %s...",
                        computeState.powerState, expectedPowerState,
                        computeState.documentSelfLink);
                return false;
            }
            return true;
        });
    }

    private void createVSphereEnv() throws Throwable {
        EnvironmentMappingState ems = new EnvironmentMappingState();
        ems.endpointType = getEndpointType().name();
        ems.name = ems.endpointType;
        ems.properties = new HashMap<>();

        PropertyMapping instanceType = new PropertyMapping();
        instanceType.mappings = new HashMap<>();
        instanceType.mappings.put("small", "small");
        ems.properties.put("instanceType", instanceType);

        PropertyMapping imageRefs = new PropertyMapping();
        imageRefs.mappings = new HashMap<>();
        imageRefs.mappings.put("linux", "something");
        ems.properties.put("imageType", imageRefs);

        EnvironmentMappingState env = doPost(ems, EnvironmentMappingService.FACTORY_LINK);
        documentLinksForDeletion.add(env.documentSelfLink);
    }

    private EndpointType getEndpointType() {
        return EndpointType.vsphere;
    }
}