/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host.interceptor;

import static com.vmware.admiral.service.common.AbstractTaskStatefulService.UNAUTHORIZED_ACCESS_FOR_ACTION_MESSAGE;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.host.HostInitRequestServicesConfig;
import com.vmware.admiral.log.EventLogFactoryService;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.log.EventLogService.EventLogState.EventLogType;
import com.vmware.admiral.request.RequestStatusFactoryService;
import com.vmware.admiral.request.RequestStatusService.RequestStatus;
import com.vmware.admiral.request.ServiceDocumentDeleteTaskService;
import com.vmware.admiral.request.ServiceDocumentDeleteTaskService.ServiceDocumentDeleteTaskState;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;

public class UnauthorizedActionsRestrictionTest extends AuthBaseTest {

    public static final String EXPECTED_ILLEGAL_STATE_EXCEPTION_MESSAGE = "Should've thrown IllegalStateException!";
    public static final String ACTION_NOT_SUPPORTED = "action not supported";

    private ProjectState createdProject = null;
    private RequestStatus requestStatus = null;
    private EventLogState eventLog = null;

    @Before
    public void setUp() throws Throwable {
        if (createdProject == null) {
            createdProject = createProjectWithRoles();
        }

        createEventLog();
        createRequestStatus();
    }

    @Override
    protected void startServices(VerificationHost host) throws Throwable {
        HostInitRequestServicesConfig.startServices(host);
        super.startServices(host);
    }

    @Override
    protected void registerInterceptors(OperationInterceptorRegistry registry) {
        UnauthorizedDeleteInterceptor.register(registry);
    }

    @Test
    public void testCloudAdminHasAccessToEventLogs() throws Throwable {
        verifyUserHasFullAccessToResourcesThroughApi(USER_EMAIL_ADMIN, eventLog,
                EventLogFactoryService.SELF_LINK, false);
    }

    @Test
    public void testProjectAdminRestrictionsToEventLogs() throws Throwable {
        verifyDeleteUnsuccessfulThroughApiAsUser(USER_EMAIL_PROJECT_ADMIN_1, eventLog,
                EventLogFactoryService.SELF_LINK, false);
    }

    @Test
    public void testProjectMemberRestrictionsToEventLogs() throws Throwable {
        verifyDeleteUnsuccessfulThroughApiAsUser(USER_EMAIL_PROJECT_MEMBER_1, eventLog,
                EventLogFactoryService.SELF_LINK, false);
    }

    @Test
    public void testBasicUserRestrictionsToEventLogs() throws Throwable {
        verifyBasicUserHasNoAccessToResourcesThroughApi(eventLog, EventLogFactoryService.SELF_LINK);
    }

    @Test
    public void testCloudAdminHasAccessToRequestStatuses() throws Throwable {
        verifyUserHasFullAccessToResourcesThroughApi(USER_EMAIL_ADMIN, requestStatus,
                RequestStatusFactoryService.SELF_LINK, true);
    }

    @Test
    public void testProjectAdminRestrictionsToRequestStatuses() throws Throwable {
        verifyDeleteUnsuccessfulThroughApiAsUser(USER_EMAIL_PROJECT_ADMIN_1, requestStatus,
                RequestStatusFactoryService.SELF_LINK, true);
    }

    @Test
    public void testProjectMemberRestrictionsToRequestStatuses() throws Throwable {
        verifyDeleteUnsuccessfulThroughApiAsUser(USER_EMAIL_PROJECT_MEMBER_1, requestStatus,
                RequestStatusFactoryService.SELF_LINK, true);
    }

    @Test
    public void testBasicUserRestrictionsToRequestStatuses() throws Throwable {
        verifyBasicUserHasNoAccessToResourcesThroughApi(requestStatus, RequestStatusFactoryService.SELF_LINK);
    }

    @Test
    public void testCloudAdminCanDeleteEventLogsFromServiceDocumentDeleteTaskService() throws Throwable {
        verifyDeleteSuccessfulAsUser(USER_EMAIL_ADMIN, eventLog, EventLogFactoryService.SELF_LINK);
    }

    @Test
    public void testProjectAdminCannotDeleteEventLogsFromServiceDocumentDeleteTaskService() throws Throwable {
        verifyDeleteUnsuccessfulAsUser(USER_EMAIL_PROJECT_ADMIN_1, eventLog,
                EventLogFactoryService.SELF_LINK);
    }

    @Test
    public void testProjectMemberCannotDeleteEventLogsFromServiceDocumentDeleteTaskService() throws Throwable {
        verifyDeleteUnsuccessfulAsUser(USER_EMAIL_PROJECT_MEMBER_1, eventLog,
                EventLogFactoryService.SELF_LINK);
    }

    @Test
    public void testBasicUserCannotDeleteEventLogsFromServiceDocumentDeleteTaskService() throws Throwable {
        verifyDeleteUnsuccessfulAsUser(USER_EMAIL_BASIC_USER, eventLog,
                EventLogFactoryService.SELF_LINK);
    }

    @Test
    public void testCloudAdminCanDeleteRequestStatusesFromServiceDocumentDeleteTaskService() throws
            Throwable {
        verifyDeleteSuccessfulAsUser(USER_EMAIL_ADMIN, requestStatus, RequestStatusFactoryService.SELF_LINK);
    }

    @Test
    public void testProjectAdminCannotDeleteRequestStatusesFromServiceDocumentDeleteTaskService() throws Throwable {
        verifyDeleteUnsuccessfulAsUser(USER_EMAIL_PROJECT_ADMIN_1, requestStatus,
                RequestStatusFactoryService.SELF_LINK);
    }

    @Test
    public void testProjectMemberCannotDeleteRequestStatusesFromServiceDocumentDeleteTaskService() throws Throwable {
        verifyDeleteUnsuccessfulAsUser(USER_EMAIL_PROJECT_MEMBER_1, requestStatus,
                RequestStatusFactoryService.SELF_LINK);
    }

    @Test
    public void testBasicUserCannotDeleteRequestStatusesFromServiceDocumentDeleteTaskService() throws Throwable {
        verifyDeleteUnsuccessfulAsUser(USER_EMAIL_BASIC_USER, requestStatus,
                RequestStatusFactoryService.SELF_LINK);
    }

    private void doPutNotSupportedVerification(ServiceDocument doc, String selfLink) throws Throwable {
        host.log("PUT to %s", selfLink);

        try {
            doPut(doc);
            fail(EXPECTED_ILLEGAL_STATE_EXCEPTION_MESSAGE);
        } catch (IllegalStateException e) {
            assertNotSupportedMessage(e);
        }
    }

    private <T extends ServiceDocument> T createState(T state, String factorySelfLink) throws Throwable {

        T createdState = doPost(state, factorySelfLink);
        assertNotNull(createdState.documentSelfLink);
        assertEquals(state, createdState);

        return createdState;
    }

    private void createEventLog() {
        eventLog = new EventLogState();
        eventLog.eventLogType = EventLogType.ERROR;
        eventLog.resourceType = "Host config";
        eventLog.description = "Host config failed.";
    }

    private void createRequestStatus() {
        requestStatus = new RequestStatus();
        requestStatus.name = "test-request-status";
        requestStatus.tenantLinks = new ArrayList<>();
        requestStatus.tenantLinks.add("test-project");
        requestStatus.resourceLinks = new HashSet<>();
        requestStatus.resourceLinks.add("/test/resource/link");
        requestStatus.documentSelfLink = UUID.randomUUID().toString();
    }

    private void verifyBasicUserHasNoAccessToResourcesThroughApi(ServiceDocument state,
            String factoryServiceSelfLink) throws Throwable {

        // use admin for creation of the state
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        ServiceDocument createdState = createState(state, factoryServiceSelfLink);

        // switch role to basic user
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));

        // GET
        doGetWithRestrictionVerification(createdState, factoryServiceSelfLink,
                state.getClass().getName());

        // POST
        doPostWithRestrictionVerification(createdState, factoryServiceSelfLink);

        // PUT
        doPutWithRestrictionVerification(createdState, factoryServiceSelfLink);

        // DELETE
        doDeleteWithRestrictionVerification(createdState, factoryServiceSelfLink);

        // use admin for clean up of the state
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
        ServiceDocument deletedState = getDocumentNoWait(state.getClass(), createdState.documentSelfLink);
        assertNull(deletedState);
    }

    private void verifyUserHasFullAccessToResourcesThroughApi(String userEmail, ServiceDocument state,
            String factoryServiceSelfLink, boolean isPutSupported) throws Throwable {

        // use specified role
        host.assumeIdentity(buildUserServicePath(userEmail));

        // POST
        ServiceDocument createdState = createState(state, factoryServiceSelfLink);
        assertEquals(state, createdState);

        // GET
        ServiceDocument retrievedState = getDocument(state.getClass(), createdState.documentSelfLink);
        assertEquals(createdState, retrievedState);

        // PUT
        if (isPutSupported) {
            retrievedState = doPut(createdState);
            assertEquals(createdState, retrievedState);
        } else {
            doPutNotSupportedVerification(createdState, createdState.documentSelfLink);
        }

        // DELETE
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
        retrievedState = getDocumentNoWait(state.getClass(), createdState.documentSelfLink);
        assertNull(retrievedState);
    }

    private void verifyDeleteUnsuccessfulThroughApiAsUser(String userMail, ServiceDocument state,
            String factoryServiceSelfLink, boolean isPutSupported) throws Throwable {

        host.assumeIdentity(buildUserServicePath(userMail));

        // POST
        ServiceDocument createdState = createState(state, factoryServiceSelfLink);
        assertEquals(state, createdState);

        // GET
        ServiceDocument retrievedState = getDocument(state.getClass(), createdState.documentSelfLink);
        assertEquals(createdState, retrievedState);

        // PUT
        if (isPutSupported) {
            retrievedState = doPut(createdState);
            assertEquals(createdState, retrievedState);
        } else {
            doPutNotSupportedVerification(createdState, createdState.documentSelfLink);
        }

        // DELETE
        doDeleteWithRestrictionVerification(createdState, factoryServiceSelfLink);

        // use admin for clean up of the state
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
        ServiceDocument deletedState = getDocumentNoWait(RequestStatus.class, createdState.documentSelfLink);
        assertNull(deletedState);

    }

    private void verifyDeleteUnsuccessfulAsUser(String userEmail, ServiceDocument state,
            String factoryServiceSelfLink) throws Throwable {

        // use admin to create the state
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        createState(state, factoryServiceSelfLink);

        List<String> allStatesLinks = getDocumentLinksOfType(state.getClass());
        Assert.assertEquals(1, allStatesLinks.size());

        // switch role to specified
        host.assumeIdentity(buildUserServicePath(userEmail));
        ServiceDocumentDeleteTaskState taskState = new ServiceDocumentDeleteTaskState();
        taskState.deleteDocumentKind = Utils.buildKind(state.getClass());

        String errorMessage = String.format(UNAUTHORIZED_ACCESS_FOR_ACTION_MESSAGE, Action.POST);
        doPostWithRestrictionVerification(taskState, ServiceDocumentDeleteTaskService.FACTORY_LINK
        );

        // use admin to get the states
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        allStatesLinks = getDocumentLinksOfType(state.getClass());
        Assert.assertEquals(1, allStatesLinks.size());
    }

    private void verifyDeleteSuccessfulAsUser(String userEmail, ServiceDocument state,
            String factoryServiceSelfLink) throws Throwable {
        host.assumeIdentity(buildUserServicePath(userEmail));

        createState(state, factoryServiceSelfLink);

        List<String> allStatesLinks = getDocumentLinksOfType(state.getClass());
        Assert.assertEquals(1, allStatesLinks.size());

        ServiceDocumentDeleteTaskState taskState = new ServiceDocumentDeleteTaskState();
        taskState.deleteDocumentKind = Utils.buildKind(state.getClass());

        taskState = doPost(taskState, ServiceDocumentDeleteTaskService.FACTORY_LINK);
        waitForTaskCompletion(taskState.documentSelfLink, ServiceDocumentDeleteTaskState.class);

        allStatesLinks = getDocumentLinksOfType(state.getClass());
        Assert.assertEquals(0, allStatesLinks.size());
    }

    private void assertNotSupportedMessage(IllegalStateException e) {
        assertTrue(e.getMessage().toLowerCase().startsWith(ACTION_NOT_SUPPORTED));
    }

    private <T extends ServiceDocument> void assertEquals(T state1, T state2) {
        switch (state1.getClass().getSimpleName()) {
        case "EventLogState":
            assertEquals((EventLogState) state1, (EventLogState) state2);
            break;
        case "RequestStatus":
            assertEquals((RequestStatus) state1, (RequestStatus) state2);
            break;
        default:
            Assert.assertEquals(state1, state2);
        }
    }

    private void assertEquals(EventLogState state1, EventLogState state2) {
        assertNotNull(state1);
        assertNotNull(state2);
        Assert.assertEquals(state1.eventLogType, state2.eventLogType);
        Assert.assertEquals(state1.resourceType, state2.resourceType);
        Assert.assertEquals(state1.description, state2.description);
    }

    private void assertEquals(RequestStatus status1, RequestStatus status2) {
        assertNotNull(status1);
        assertNotNull(status2);
        Assert.assertEquals(status1.name, status2.name);
        Assert.assertEquals(status1.resourceLinks, status2.resourceLinks);
        Assert.assertEquals(status1.tenantLinks, status2.tenantLinks);
    }

}
