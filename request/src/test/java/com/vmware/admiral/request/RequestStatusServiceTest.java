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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.RequestStatusService.RequestStatus;
import com.vmware.admiral.request.composition.CompositionTaskFactoryService;
import com.vmware.admiral.request.composition.CompositionTaskService.CompositionTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.TypeName;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.SortOrder;
import com.vmware.xenon.services.common.QueryTask.QueryTerm;

/**
 * Test for RequestStatusService
 */
public class RequestStatusServiceTest extends RequestBaseTest {
    public static final String EXPECTED_LAST_PHASE = RequestBrokerService.DISPLAY_NAME;
    public static final String EXPECTED_LAST_SUBSTAGE =
            RequestBrokerState.SubStage.COMPLETED.name();

    public static List<String> EXPECTED_PHASES_IN_HISTORY = Arrays.asList(
            RequestBrokerService.DISPLAY_NAME,
            ContainerAllocationTaskService.DISPLAY_NAME,
            ReservationTaskService.DISPLAY_NAME,
            PlacementHostSelectionTaskService.DISPLAY_NAME,
            ResourceNamePrefixTaskService.DISPLAY_NAME
            );

    private String requestId;
    private List<RequestStatus> statusHistory;
    private String formattedHistory;

    @Test
    public void testSingleRequestStatus() throws Throwable {
        ContainerDescription containerDesc = createContainerDescription();
        RequestStatus requestStatus = verifyRequestStatus(containerDesc.documentSelfLink);
        assertEquals(containerDesc.name, requestStatus.name);

        // Verify Request resource links and desc name:
        String requestLink = UriUtils.buildUriPath(RequestBrokerFactoryService.SELF_LINK,
                extractId(requestStatus.documentSelfLink));
        RequestBrokerState requestBroker = getDocument(RequestBrokerState.class, requestLink);
        assertNotNull(requestBroker);

        assertEquals(requestBroker.resourceLinks, requestStatus.resourceLinks);

        // Day2 operation request:
        RequestBrokerState request = requestDay2Operation(requestBroker,
                ContainerOperationType.STOP.id);

        waitFor(() -> {
            RequestStatus status = getRequestStatus(requestId);
            return TaskState.isFinished(status.taskInfo)
                    && (status.progress.intValue() == 100);
        });

        requestStatus = getRequestStatus(requestId);
        assertNotNull(requestStatus);
        assertEquals(ContainerOperationType.extractDisplayName(request.operation),
                requestStatus.name);
        assertEquals(requestBroker.resourceLinks, requestStatus.resourceLinks);

        // Removal operation request:
        request = requestDay2Operation(requestBroker,
                ContainerOperationType.DELETE.id);

        waitFor(() -> {
            RequestStatus status = getRequestStatus(requestId);
            return TaskState.isFinished(status.taskInfo)
                    && (status.progress.intValue() == 100);
        });

        requestStatus = getRequestStatus(requestId);
        assertNotNull(requestStatus);
        assertEquals(ContainerOperationType.extractDisplayName(request.operation),
                requestStatus.name);
        assertEquals(requestBroker.resourceLinks, requestStatus.resourceLinks);
    }

    @Test
    public void testCompositionRequestStatus() throws Throwable {
        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription("name1");
        ContainerDescription desc2 = TestRequestStateFactory.createContainerDescription("name2");
        desc2.affinity = new String[] { desc1.name };
        CompositeDescription compositeDesc = createCompositeDesc(ResourceType.CONTAINER_TYPE, desc1,
                desc2);
        RequestStatus requestStatus = verifyRequestStatus(compositeDesc.documentSelfLink);
        assertEquals(compositeDesc.name, requestStatus.name);

        String compositionTaskLink = UriUtils.buildUriPath(CompositionTaskFactoryService.SELF_LINK,
                extractId(requestStatus.documentSelfLink));
        CompositionTaskState compositionTaskState = getDocument(CompositionTaskState.class,
                compositionTaskLink);
        assertNotNull(compositionTaskState);

        assertEquals(1, requestStatus.resourceLinks.size());
        assertEquals(compositionTaskState.compositeComponentLink,
                requestStatus.resourceLinks.get(0));
    }

    @Test
    public void testRequestStatusShouldNotBeModifiedAfterCompletion() throws Throwable {
        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription("name1");
        desc1.portBindings = null;
        ContainerDescription desc2 = TestRequestStateFactory.createContainerDescription("name2");
        desc2.portBindings = null;
        desc2.affinity = new String[] { desc1.name };
        CompositeDescription compositeDesc = createCompositeDesc(ResourceType.CONTAINER_TYPE, desc1,
                desc2);
        RequestStatus requestStatus = verifyRequestStatus(compositeDesc.documentSelfLink);
        assertEquals(compositeDesc.name, requestStatus.name);

        String compositionTaskLink = UriUtils.buildUriPath(CompositionTaskFactoryService.SELF_LINK,
                extractId(requestStatus.documentSelfLink));
        CompositionTaskState compositionTaskState = getDocument(CompositionTaskState.class,
                compositionTaskLink);
        assertNotNull(compositionTaskState);

        assertEquals(1, requestStatus.resourceLinks.size());
        assertEquals(compositionTaskState.compositeComponentLink,
                requestStatus.resourceLinks.get(0));

        // The request is already finished the patch should not change the state
        requestStatus.taskInfo = TaskState.createAsStarted();
        doOperation(requestStatus, UriUtilsExtended.buildUri(host, requestStatus.documentSelfLink),
                false,
                Action.PATCH);
        RequestStatus finalStatus = getRequestStatus(requestId);
        assertTrue(TaskState.isFinished(finalStatus.taskInfo));
    }

    // jira issue VSYM-687
    @Test
    public void testRequestStatusShouldBeFailedAfterError() throws Throwable {
        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription("name1");
        ContainerDescription desc2 = TestRequestStateFactory.createContainerDescription("name2");
        desc2.affinity = new String[] { desc1.name };
        CompositeDescription compositeDesc = createCompositeDesc(ResourceType.CONTAINER_TYPE, desc1,
                desc2);

        // Stop the docker adapter service in order to fail the request
        Service service = new MockDockerAdapterService();
        service.setSelfLink(MockDockerAdapterService.SELF_LINK);
        stopService(service);

        RequestBrokerState request = TestRequestStateFactory.createRequestState();
        request.resourceDescriptionLink = compositeDesc.documentSelfLink;
        request.tenantLinks = groupPolicyState.tenantLinks;
        request = startRequest(request);
        waitForRequestToFail(request);
        requestId = extractId(request.documentSelfLink);

        waitFor(() -> {
            RequestStatus status = getRequestStatus(requestId);
            return TaskState.isFailed(status.taskInfo)
                    && status.progress.intValue() < 100;
        });
        RequestStatus requestStatus = getRequestStatus(requestId);
        // patch the state to 100 completion state STARTED and subStage ERROR
        for (Map<String, Integer> value : requestStatus.requestProgressByComponent.values()) {
            value.replaceAll((k, v) -> 100);
        }
        requestStatus.documentSelfLink = requestId + "-name2";
        requestStatus.taskInfo = TaskState.createAsStarted();
        requestStatus.progress = 100;
        requestStatus.subStage = DefaultSubStage.ERROR.name();
        requestStatus.phase = "Container Allocation";
        doOperation(requestStatus,
                UriUtilsExtended.buildUri(host, getRequestStatus(requestId).documentSelfLink),
                false,
                Action.PATCH);

        waitFor(() -> {
            RequestStatus state = getRequestStatus(requestId);
            return DefaultSubStage.ERROR.name().equals(state.subStage)
                    && (state.progress.intValue() == 100);
        });

        // Try to set the request status to finished
        requestStatus.taskInfo = TaskState.createAsFinished();
        doOperation(requestStatus,
                UriUtilsExtended.buildUri(host, getRequestStatus(requestId).documentSelfLink),
                false,
                Action.PATCH);

        RequestStatus finalStatus = getRequestStatus(requestId);
        assertTrue(TaskState.isFailed(finalStatus.taskInfo));
    }

    // jira issue VSYM-1013
    @Test
    public void testRequestStatusShouldBeFailedAfterErrorInOneOfTheComponents() throws Throwable {
        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription("name1");
        ContainerDescription desc2 = TestRequestStateFactory.createContainerDescription("name2");
        desc2.customProperties.put(MockDockerAdapterService.FAILURE_EXPECTED, "simulate failure");
        CompositeDescription compositeDesc = createCompositeDesc(ResourceType.CONTAINER_TYPE, desc1,
                desc2);

        RequestBrokerState request = TestRequestStateFactory.createRequestState();
        request.resourceDescriptionLink = compositeDesc.documentSelfLink;
        request.tenantLinks = groupPolicyState.tenantLinks;
        request = startRequest(request);
        waitForRequestToFail(request);
        requestId = extractId(request.documentSelfLink);

        waitFor(() -> {
            RequestStatus status = getRequestStatus(requestId);
            return TaskState.isFailed(status.taskInfo)
                    && status.progress.intValue() == 100;
        });
    }

    @After
    public void logRequestHistory() throws Throwable {
        try {
            if (formattedHistory == null) {
                if (statusHistory == null) {
                    statusHistory = getRequestHistory();
                }

                formattedHistory = formatRequestHistory(statusHistory);
            }

            host.log(formattedHistory.replaceAll("%", "%%"));

        } catch (Throwable x) {
            host.log("Failed to fetch request history: %s", Utils.toString(x));
        }
    }

    public RequestStatus verifyRequestStatus(String descriptionLink) throws Throwable {
        // Request a container instance:
        RequestBrokerState request = TestRequestStateFactory.createRequestState();
        request.resourceDescriptionLink = descriptionLink;
        request.tenantLinks = groupPolicyState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);
        waitForRequestToComplete(request);
        requestId = extractId(request.documentSelfLink);
        host.log("########  request completed ######## ");

        RequestStatus[] result = new RequestStatus[] { null };
        waitFor(() -> {
            result[0] = getRequestStatus(requestId);
            return EXPECTED_LAST_PHASE.equals(result[0].phase)
                    && (result[0].component == null)
                    && TaskStage.FINISHED.equals(result[0].taskInfo.stage)
                    && (result[0].progress.intValue() == 100);
        });
        RequestStatus requestStatus = result[0];

        assertNotNull("RequestStatus is missing", requestStatus);
        assertEquals("Unexpected last phase", EXPECTED_LAST_PHASE, requestStatus.phase);

        assertEquals("Request didn't complete", TaskStage.FINISHED, requestStatus.taskInfo.stage);
        assertEquals("Unexpected sub stage", EXPECTED_LAST_SUBSTAGE, requestStatus.subStage);

        assertEquals("Progress", new Integer(100), requestStatus.progress);

        // verify the request status history (query for all versions of the RequestStatus)
        statusHistory = getRequestHistory();
        Set<String> actualPhasesInHistory = statusHistory.stream()
                .map((rs) -> rs.phase)
                .collect(Collectors.toSet());

        formattedHistory = formatRequestHistory(statusHistory);

        for (String expectedPhase : EXPECTED_PHASES_IN_HISTORY) {
            assertTrue(String.format("Missing phase [%s] in request status history: [%s]",
                    expectedPhase, formattedHistory),
                    actualPhasesInHistory.contains(expectedPhase));
        }

        return requestStatus;
    }

    private List<RequestStatus> getRequestHistory() throws Throwable {
        String selfLink = UriUtils.buildUriPath(RequestStatusFactoryService.SELF_LINK, requestId);
        QueryTask historyQuery = QueryUtil.buildPropertyQuery(RequestStatus.class,
                ServiceDocument.FIELD_NAME_SELF_LINK, selfLink);
        historyQuery.querySpec.options = EnumSet.of(QueryOption.INCLUDE_ALL_VERSIONS,
                QueryOption.EXPAND_CONTENT, QueryOption.SORT);

        historyQuery.querySpec.sortTerm = new QueryTerm();
        historyQuery.querySpec.sortTerm.propertyName = ServiceDocument.FIELD_NAME_VERSION;
        historyQuery.querySpec.sortTerm.propertyType = TypeName.LONG;
        historyQuery.querySpec.sortOrder = SortOrder.ASC;

        List<RequestStatus> statusHistory = new ArrayList<>();
        host.testStart(1);
        new ServiceDocumentQuery<RequestStatus>(host, RequestStatus.class).query(historyQuery,
                (r) -> {
                    if (r.hasException()) {
                        host.failIteration(r.getException());

                    } else if (r.hasResult()) {
                        statusHistory.add(r.getResult());

                    } else {
                        host.completeIteration();
                    }
                });

        host.testWait();

        return statusHistory;
    }

    private RequestBrokerState requestDay2Operation(RequestBrokerState requestBroker,
            String operation)
            throws Throwable {
        RequestBrokerState request = TestRequestStateFactory.createRequestState();
        request.resourceDescriptionLink = null;
        request.tenantLinks = groupPolicyState.tenantLinks;
        request.resourceLinks = requestBroker.resourceLinks;
        request.operation = operation;
        request = startRequest(request);
        waitForRequestToComplete(request);
        requestId = extractId(request.documentSelfLink);
        return request;
    }

    private String formatRequestHistory(List<RequestStatus> statusHistory) {
        String completeHistory = statusHistory
                .stream()
                .map((rs) -> String.format("%s: %s %s %s %s %s%% %n", rs.documentVersion,
                        rs.phase, rs.component != null ? rs.component : "",
                        rs.taskInfo != null ? rs.taskInfo.stage : "", rs.subStage, rs.progress))
                .collect(Collectors.joining());

        return completeHistory;
    }

    private RequestStatus getRequestStatus(String requestId) throws Throwable {
        host.log("Fetching request status: %s", requestId);

        String path = UriUtils.buildUriPath(RequestStatusFactoryService.SELF_LINK, requestId);
        URI uri = UriUtils.buildUri(host, path);

        host.testStart(1);

        RequestStatus[] resultHolder = new RequestStatus[1];

        host.send(Operation.createGet(uri)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        if (Operation.STATUS_CODE_NOT_FOUND == o.getStatusCode()) {
                            /* resultHolder will hold a null result */
                            host.completeIteration();
                        } else {
                            host.failIteration(ex);
                        }
                    } else {
                        resultHolder[0] = o.getBody(RequestStatus.class);
                        host.completeIteration();
                    }
                }));

        host.testWait();

        return resultHolder[0];
    }

}
