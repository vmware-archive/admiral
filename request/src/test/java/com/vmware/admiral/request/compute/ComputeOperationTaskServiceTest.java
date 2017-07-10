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

import static com.vmware.admiral.compute.ComputeConstants.OPERATION_PAYLOAD_PROP_NAME;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.util.JsonMapper;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.endpoint.EndpointAdapterService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.compute.ComputeOperationTaskService.ComputeOperationTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

public class ComputeOperationTaskServiceTest extends ComputeRequestBaseTest {

    private static final String CUSTOM_PROPERTY_KEY_1 = "cp1.Key";
    private static final String CUSTOM_PROPERTY_KEY_2 = "cp2.Key";
    private static final String CUSTOM_PROPERTY_VALUE_1 = "cp1.Value";
    private static final String CUSTOM_PROPERTY_VALUE_2 = "cp2.Value";
    public static final String ERROR_PAYLOAD_IS_NULL_OR_EMPTY = "Payload is null or empty";
    private RequestBrokerState request;

    @Override
    public void setUp() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
        super.setUp();

        // create a single powered-on compute available for placement
        createVmHostCompute(true);

        request = TestRequestStateFactory.createRequestState(ResourceType.COMPUTE_TYPE.getName(),
                hostDesc.documentSelfLink);
        request.tenantLinks = computeGroupPlacementState.tenantLinks;
        request.resourceCount = 1;
    }

    @After
    public void tearDown() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(false);
    }

    @Test
    public void testComputeResourceOperationCycle() throws Throwable {
        host.log("########  testComputeResourceOperationCycle ######## ");
        RequestBrokerState provisionRequest = provisionComputes();

        host.log("wait for computes to be in on state for request: "
                + request.documentSelfLink);
        waitForComputePowerState(PowerState.ON, provisionRequest.resourceLinks);

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

    @Test
    public void testExternalComputeResourceOperationCycle() throws Throwable {
        host.log("########  testExternalComputeResourceOperationCycle ######## ");

        host.log("##                start test power service                ## ");
        host.startService(new TestPowerService());
        host.waitForServiceAvailable(TestPowerService.SELF_LINK);

        String operation = "External." + ComputeOperationType.POWER_OFF.id;
        RequestBrokerState provisionRequest = provisionComputes();
        registerResourceOperation(
                getEndpointType().name(),
                ResourceOperationSpecService.ResourceType.COMPUTE,
                operation,
                TestPowerService.SELF_LINK);

        host.log("wait for computes to be in on state for request: "
                + request.documentSelfLink);
        waitForComputePowerState(PowerState.ON, provisionRequest.resourceLinks);

        RequestBrokerState day2StopRequest = new RequestBrokerState();
        day2StopRequest.resourceType = provisionRequest.resourceType;
        day2StopRequest.resourceLinks = provisionRequest.resourceLinks;
        day2StopRequest.operation = operation;
        Map<String, String> payload = new HashMap<>();
        payload.put(CUSTOM_PROPERTY_KEY_1, CUSTOM_PROPERTY_VALUE_1);
        payload.put(CUSTOM_PROPERTY_KEY_2, CUSTOM_PROPERTY_VALUE_2);
        String json = JsonMapper.toJSON(payload);
        day2StopRequest.addCustomProperty(OPERATION_PAYLOAD_PROP_NAME, json);

        day2StopRequest = startRequest(day2StopRequest);

        String computeOperationTaskLink = UriUtils.buildUriPath(
                ComputeOperationTaskService.FACTORY_LINK,
                extractId(day2StopRequest.documentSelfLink));

        waitForTaskSuccess(computeOperationTaskLink, ComputeOperationTaskState.class);
        waitForRequestToComplete(day2StopRequest);

        // verify the resources have been stopped:
        waitForComputePowerState(PowerState.OFF, request.resourceLinks);
    }

    @Test
    public void testExternalComputeResourceOperationCycle_noPayload() throws Throwable {
        host.log("########  testExternalComputeResourceOperationCycle_noPayload ######## ");

        host.log("##                     start test power service                     ## ");
        host.startService(new TestPowerService());
        host.waitForServiceAvailable(TestPowerService.SELF_LINK);

        String operation = "External.NoPayload";
        RequestBrokerState provisionRequest = provisionComputes();
        registerResourceOperation(
                getEndpointType().name(),
                ResourceOperationSpecService.ResourceType.COMPUTE,
                operation,
                TestPowerService.SELF_LINK);

        RequestBrokerState day2StopRequest = new RequestBrokerState();
        day2StopRequest.resourceType = provisionRequest.resourceType;
        day2StopRequest.resourceLinks = provisionRequest.resourceLinks;
        day2StopRequest.operation = operation;

        day2StopRequest = startRequest(day2StopRequest);

        String computeOperationTaskLink = UriUtils.buildUriPath(
                ComputeOperationTaskService.FACTORY_LINK,
                extractId(day2StopRequest.documentSelfLink));

        ComputeOperationTaskState taskState = waitForTaskError(
                computeOperationTaskLink, ComputeOperationTaskState.class);
        Assert.assertEquals(ERROR_PAYLOAD_IS_NULL_OR_EMPTY, taskState.taskInfo.failure.message);
    }

    /**
     * Assert that request a non-existing operation fails
     * @throws Throwable
     */
    @Test
    public void testExternalComputeResourceOperationCycle_neg() throws Throwable {
        host.log("########  testExternalComputeResourceOperationCycle_neg ######## ");
        RequestBrokerState provisionRequest = provisionComputes();

        RequestBrokerState day2StopRequest = new RequestBrokerState();
        day2StopRequest.resourceType = provisionRequest.resourceType;
        day2StopRequest.resourceLinks = provisionRequest.resourceLinks;
        day2StopRequest.operation = "External.No.Such.Operation";

        day2StopRequest = startRequest(day2StopRequest);

        String computeOperationTaskLink = UriUtils.buildUriPath(
                ComputeOperationTaskService.FACTORY_LINK,
                extractId(day2StopRequest.documentSelfLink));

        waitForTaskError(computeOperationTaskLink, ComputeOperationTaskState.class);
        waitForRequestToFail(day2StopRequest);
    }

    @Override
    protected synchronized EndpointState createEndpoint() throws Throwable {
        synchronized (initializationLock) {
            if (endpoint == null) {
                endpoint = TestRequestStateFactory.createEndpoint();
                endpoint.endpointType = getEndpointType().name();
                endpoint.endpointProperties.put("endpointHost", "https://somehost");
                endpoint = getOrCreateDocument(endpoint, EndpointAdapterService.SELF_LINK);
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
        provisionRequest.resourceLinks = new HashSet<>(computeStateLinks);
        provisionRequest.operation = ComputeOperationType.CREATE.id;

        provisionRequest = startRequest(provisionRequest);

        return waitForRequestToComplete(provisionRequest);
    }

    private Collection<ComputeState> findResources(Class<? extends ServiceDocument> type,
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

        host.testStart(1);
        List<ComputeState> computes = new ArrayList<>();
        new ServiceDocumentQuery<>(host, ComputeState.class).query(query, (r) -> {
            if (r.hasException()) {
                host.failIteration(r.getException());
            } else if (r.hasResult()) {
                computes.add(r.getResult());
            } else {
                host.completeIteration();
            }
        });
        host.testWait();

        return computes;
    }

    private void waitForComputePowerState(final PowerState expectedPowerState,
            Collection<String> computeLinks) throws Throwable {
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

    private EndpointType getEndpointType() {
        return EndpointType.vsphere;
    }

    private ResourceOperationSpec registerResourceOperation(
            String endpointType,
            ResourceOperationSpecService.ResourceType resourceType,
            String operation,
            String adapterReference) {
        ResourceOperationSpec roState = new ResourceOperationSpec();
        roState.endpointType = endpointType;
        roState.operation = operation;
        roState.name = operation;
        roState.description = operation;
        roState.resourceType = resourceType;
        roState.adapterReference = UriUtils.buildUri(this.host, adapterReference);

        Operation registerOp = Operation.createPost(
                super.host,
                ResourceOperationSpecService.FACTORY_LINK)
                .setBody(roState).setCompletion((op, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Error on register resource operation: %s",
                                Utils.toString(ex));
                        op.fail(ex);
                    } else {
                        op.complete();
                    }
                });
        Operation response = super.host.waitForResponse(registerOp);
        ResourceOperationSpec persistedState = response.getBody(ResourceOperationSpec.class);
        Assert.assertNotNull(persistedState);
        return persistedState;
    }

    public static class TestPowerService extends StatelessService {

        public static final String SELF_LINK = UriPaths.PROVISIONING +
                "/vsphere/external-power-adapter";

        @Override
        public void handlePatch(Operation op) {
            if (!op.hasBody()) {
                op.fail(new IllegalArgumentException("body is required"));
                return;
            }
            ResourceOperationRequest roRequest = op.getBody(ResourceOperationRequest.class);
            op.complete();

            //
            ComputeState state = new ComputeState();
            state.powerState = PowerState.OFF;
            getHost().log(Level.INFO,
                    "Handle operation %s, for resource reference %s. Payload: %s.",
                    roRequest.operation,
                    roRequest.resourceReference,
                    roRequest.payload);
            TaskManager taskManager = new TaskManager(this,
                    roRequest.taskReference,
                    roRequest.resourceLink());

            IllegalStateException ise = assertPayload(roRequest.payload);
            if (ise != null) {
                taskManager.patchTaskToFailure(ise);
            } else {
                URI uri = UriUtils.buildUri(getHost(), roRequest.resourceLink());

                Operation.createPatch(uri)
                        .setBody(state)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                log(Level.SEVERE,
                                        "ResourceOperationRequest: " + roRequest, e);
                                taskManager.patchTaskToFailure(e);
                            } else {
                                taskManager.finishTask();
                            }
                        })
                        .sendWith(this);
            }
        }

        private IllegalStateException assertPayload(Map<String, String> payload) {
            String msg = null;
            if (payload == null || payload.isEmpty()) {
                msg = ERROR_PAYLOAD_IS_NULL_OR_EMPTY;
            } else if (!CUSTOM_PROPERTY_VALUE_1.equals(payload.get(CUSTOM_PROPERTY_KEY_1))
                    || !CUSTOM_PROPERTY_VALUE_2.equals(payload.get(CUSTOM_PROPERTY_KEY_2))) {
                msg = "Unexpected payload: " + payload;
            }
            return msg != null ? new IllegalStateException(msg) : null;
        }

    }
}