/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.auth.idm;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles;
import com.vmware.admiral.auth.project.ProjectService.ExpandedProjectState;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.common.LogService.LogServiceState;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class RoleRestrictionsTest extends AuthBaseTest {
    public static final String EXPECTED_ILLEGAL_ACCESS_ERROR_MESSAGE = "Should've thrown IllegalAccessError!";
    public static final String FORBIDDEN = "forbidden";
    public static final String FIRST_CERTIFICATE_PATH = "test_ssl_trust.PEM";
    public static final String SECOND_CERTIFICATE_PATH = "test_ssl_trust2.PEM";

    private ProjectState createdProject = null;

    @Before
    public void setupProjectRoles() throws Throwable {
        if (createdProject != null) {
            return;
        }

        ProjectState project = new ProjectState();
        project.name = "test";

        // Cloud Admin creates a project
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        createdProject = doPost(project, ProjectFactoryService.SELF_LINK);
        assertNotNull(createdProject);
        assertNotNull(createdProject.documentSelfLink);

        // Assign basic users to the project as Project Admins, members and viewers
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.administrators.add = Arrays.asList(USER_EMAIL_GLORIA,
                USER_EMAIL_PROJECT_ADMIN_1);

        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.members.add = Collections.singletonList(USER_EMAIL_PROJECT_MEMBER_1);

        projectRoles.viewers = new PrincipalRoleAssignment();
        projectRoles.viewers.add = Collections.singletonList(USER_EMAIL_PROJECT_VIEWER_1);

        ExpandedProjectState expandedProjectState = getExpandedProjectState(createdProject.documentSelfLink);
        doPatch(projectRoles, expandedProjectState.documentSelfLink);
        expandedProjectState = getExpandedProjectState(createdProject.documentSelfLink);

        // validate users were successfully assigned to the project
        assertTrue(expandedProjectState.administrators.size() == 2);
        assertTrue(expandedProjectState.members.size() == 1);
        assertTrue(expandedProjectState.viewers.size() == 1);

        Set<String> adminsList = expandedProjectState.administrators.stream().map(p -> p.email)
                .collect(Collectors.toSet());
        Set<String> membersList = expandedProjectState.members.stream().map(p -> p.email)
                .collect(Collectors.toSet());
        Set<String> viewersList = expandedProjectState.viewers.stream().map(p -> p.email)
                .collect(Collectors.toSet());

        assertThat(adminsList, hasItem(USER_EMAIL_GLORIA));
        assertThat(adminsList, hasItem(USER_EMAIL_PROJECT_ADMIN_1));
        assertThat(membersList, hasItem(USER_EMAIL_PROJECT_MEMBER_1));
        assertThat(viewersList, hasItem(USER_EMAIL_PROJECT_VIEWER_1));
    }

    @Test
    public void testCloudAdminHasAccessToCredentials() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        AuthCredentialsServiceState cred = new AuthCredentialsServiceState();
        cred.userEmail = "test";

        // POST
        AuthCredentialsServiceState createdState = doPost(cred, AuthCredentialsService.FACTORY_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        // GET
        AuthCredentialsServiceState retrievedState = getDocument(AuthCredentialsServiceState.class, createdState.documentSelfLink);
        assertNotNull(retrievedState);

        // PUT
        createdState.userEmail = "updated-name";
        AuthCredentialsServiceState updatedState = doPut(createdState);
        assertNotNull(updatedState);
        assertTrue(createdState.userEmail.equals(updatedState.userEmail));

        // DELETE
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
        retrievedState = getDocumentNoWait(AuthCredentialsServiceState.class, createdState.documentSelfLink);
        assertNull(retrievedState);
    }

    @Test
    public void testCloudAdminHasAccessToCertificates() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        SslTrustCertificateState cert = new SslTrustCertificateState();
        cert.certificate = CommonTestStateFactory.getFileContent(FIRST_CERTIFICATE_PATH).trim();

        // POST
        SslTrustCertificateState createdState = doPost(cert, SslTrustCertificateService.FACTORY_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        // GET
        SslTrustCertificateState retrievedState = getDocument(SslTrustCertificateState.class, createdState.documentSelfLink);
        assertNotNull(retrievedState);

        // PUT
        createdState.certificate = CommonTestStateFactory.getFileContent(SECOND_CERTIFICATE_PATH).trim();
        SslTrustCertificateState updatedState = doPut(createdState);
        assertNotNull(updatedState);
        assertTrue(createdState.certificate.equals(updatedState.certificate));

        // DELETE
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
        retrievedState = getDocumentNoWait(SslTrustCertificateState.class, createdState.documentSelfLink);
        assertNull(retrievedState);
    }

    @Test
    public void testCloudAdminHasAccessToRegistries() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        RegistryState registry = new RegistryState();
        registry.name = "test";

        // POST
        RegistryState createdState = doPost(registry, RegistryFactoryService.SELF_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        // GET
        RegistryState retrievedState = getDocument(RegistryState.class, createdState.documentSelfLink);
        assertNotNull(retrievedState);

        // PUT
        createdState.name = "updated-name";
        RegistryState updatedState = doPut(createdState);
        assertNotNull(updatedState);
        assertTrue(createdState.name.equals(updatedState.name));

        // DELETE
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
        retrievedState = getDocumentNoWait(RegistryState.class, createdState.documentSelfLink);
        assertNull(retrievedState);
    }

    @Test
    public void testCloudAdminHasAccessToProjects() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        ProjectState project = new ProjectState();
        project.name = "test-name";

        // POST
        ProjectState createdState = doPost(project, ProjectFactoryService.SELF_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        // GET
        ProjectState retrievedState = getDocument(ProjectState.class, createdState.documentSelfLink);
        assertNotNull(retrievedState);

        // PUT
        createdState.name = "updated-name";
        ProjectState updatedState = doPut(createdState);
        assertNotNull(updatedState);
        assertTrue(createdState.name.equals(updatedState.name));

        // DELETE
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
        retrievedState = getDocumentNoWait(ProjectState.class, createdState.documentSelfLink);
        assertNull(retrievedState);
    }

    @Test
    public void testCloudAdminHasAccessToConfiguration() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        ConfigurationState config = new ConfigurationState();
        config.key = "key";
        config.value = "value";

        // POST
        ConfigurationState createdState = doPost(config, ConfigurationFactoryService.SELF_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);
        assertEquals(config.key, createdState.key);
        assertEquals(config.value, createdState.value);

        // GET
        ConfigurationState retrievedState = getDocument(ConfigurationState.class, createdState.documentSelfLink);
        assertNotNull(retrievedState);

        // PUT
        createdState.value = "updated-value";
        ConfigurationState updatedState = doPut(createdState);
        assertNotNull(updatedState);
        assertTrue(createdState.value.equals(updatedState.value));

        // DELETE
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
        retrievedState = getDocumentNoWait(ConfigurationState.class, createdState.documentSelfLink);
        assertNull(retrievedState);
    }

    @Test
    public void testCloudAdminHasAccessToLogs() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        LogServiceState log = new LogServiceState();
        log.logs = new byte[] { 1 };

        // POST
        LogServiceState createdState = doPost(log, LogService.FACTORY_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);
        assertEquals(log.logs[0], createdState.logs[0]);

        // GET
        LogServiceState retrievedState = getDocument(LogServiceState.class, createdState.documentSelfLink);
        assertNotNull(retrievedState);

        // PUT
        createdState.logs = new byte[] { 1 };
        LogServiceState updatedState = doPut(createdState);
        assertNotNull(updatedState);
        assertEquals(createdState.logs[0], updatedState.logs[0]);

        // DELETE
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
        retrievedState = getDocumentNoWait(LogServiceState.class, createdState.documentSelfLink);
        assertNull(retrievedState);
    }

    @Test
    public void testBasicUserRestrictionsToCredentials() throws Throwable {

        AuthCredentialsServiceState cred = new AuthCredentialsServiceState();
        cred.userEmail = "test";

        // GET
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        AuthCredentialsServiceState createdState = doPost(cred, AuthCredentialsService.FACTORY_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));
        doGetWithRestrictionVerification(createdState, AuthCredentialsService.FACTORY_LINK, AuthCredentialsServiceState.class.getName());

        // POST
        doPostWithRestrictionVerification(cred, AuthCredentialsService.FACTORY_LINK);

        // PUT
        createdState.userEmail = "updated-name";
        doPutWithRestrictionVerification(createdState, AuthCredentialsService.FACTORY_LINK);

        // DELETE
        doDeleteWithRestrictionVerification(createdState, AuthCredentialsService.FACTORY_LINK);
    }

    @Test
    public void testBasicUserRestrictionsToCertificates() throws Throwable {

        SslTrustCertificateState cert = new SslTrustCertificateState();
        cert.certificate = CommonTestStateFactory.getFileContent(FIRST_CERTIFICATE_PATH).trim();

        // GET
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        SslTrustCertificateState createdState = doPost(cert, SslTrustCertificateService.FACTORY_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));
        doGetWithRestrictionVerification(createdState, SslTrustCertificateService.FACTORY_LINK, SslTrustCertificateState.class.getName());

        // POST
        doPostWithRestrictionVerification(cert, SslTrustCertificateService.FACTORY_LINK);

        // PUT
        createdState.commonName = "updated-name";
        doPutWithRestrictionVerification(createdState, SslTrustCertificateService.FACTORY_LINK);

        // DELETE
        doDeleteWithRestrictionVerification(createdState, SslTrustCertificateService.FACTORY_LINK);
    }

    @Test
    public void testBasicUserRestrictionsToRegistries() throws Throwable {

        RegistryState registry = new RegistryState();
        registry.name = "test";

        // GET
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        RegistryState createdState = doPost(registry, RegistryFactoryService.SELF_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));
        RegistryState document = getDocument(RegistryState.class, createdState.documentSelfLink);
        assertNotNull(document);
        assertEquals(createdState.documentSelfLink, document.documentSelfLink);

        // POST
        doPostWithRestrictionVerification(registry, RegistryFactoryService.SELF_LINK);

        // PUT
        createdState.name = "updated-name";
        doPutWithRestrictionVerification(createdState, RegistryFactoryService.SELF_LINK);

        // DELETE
        doDeleteWithRestrictionVerification(createdState, RegistryFactoryService.SELF_LINK);
    }

    @Test
    public void testBasicUserRestrictionsToProjects() throws Throwable {

        ProjectState project = new ProjectState();
        project.name = "test-name";

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        ProjectState createdState = doPost(project, ProjectFactoryService.SELF_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));

        // GET
        doGetWithRestrictionVerification(createdState, ProjectFactoryService.SELF_LINK, ProjectState.class.getName());

        // POST
        project.name = "test1";
        doPostWithRestrictionVerification(project, ProjectFactoryService.SELF_LINK);

        // PUT
        createdState.name = "updated-name";
        doPutWithRestrictionVerification(createdState, ProjectFactoryService.SELF_LINK);

        // PATCH
        createdState.name = "updated-name";
        doPatchWithRestrictionVerification(createdState, createdState.documentSelfLink);

        // DELETE
        doDeleteWithRestrictionVerification(createdState, ProjectFactoryService.SELF_LINK);
    }

    @Test
    public void testBasicUserRestrictionsToConfiguration() throws Throwable {
        ConfigurationState config = new ConfigurationState();
        config.key = "key";
        config.value = "value";

        // use admin for creation of the state
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        ConfigurationState createdState = doPost(config, ConfigurationFactoryService.SELF_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);
        assertEquals(config.key, createdState.key);
        assertEquals(config.value, createdState.value);

        // switch role to basic user
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));

        // GET
        ConfigurationState retrievedState = getDocument(ConfigurationState.class, createdState.documentSelfLink);
        assertNotNull(retrievedState);

        // POST
        doPostWithRestrictionVerification(config, ConfigurationFactoryService.SELF_LINK);

        // PUT
        createdState.value = "updated-value";
        doPutWithRestrictionVerification(createdState, ConfigurationFactoryService.SELF_LINK);

        // DELETE
        doDeleteWithRestrictionVerification(createdState, ConfigurationFactoryService.SELF_LINK);
    }

    @Test
    public void testBasicUserRestrictionsToLogs() throws Throwable {
        LogServiceState log = new LogServiceState();
        log.logs = new byte[] { 1 };

        // use admin for creation of the state
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        LogServiceState createdState = doPost(log, LogService.FACTORY_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);
        assertEquals(log.logs[0], createdState.logs[0]);

        // switch role to basic user
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));

        // GET
        doGetWithRestrictionVerification(createdState, LogService.FACTORY_LINK, LogServiceState.class.getName());

        // POST
        doPostWithRestrictionVerification(log, LogService.FACTORY_LINK);

        // PUT
        doPutWithRestrictionVerification(createdState, LogService.FACTORY_LINK);

        // DELETE
        doDeleteWithRestrictionVerification(createdState, LogService.FACTORY_LINK);
    }

    @Test
    public void testProjectAdminRestrictionsToCredentials() throws Throwable {

        AuthCredentialsServiceState cred = new AuthCredentialsServiceState();
        cred.userEmail = "test";

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        AuthCredentialsServiceState createdState = doPost(cred, AuthCredentialsService.FACTORY_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_GLORIA));

        // GET
        getDocumentNoWait(AuthCredentialsServiceState.class, createdState.documentSelfLink);

        // POST
        doPost(cred, AuthCredentialsService.FACTORY_LINK);

        // PUT
        createdState.userEmail = "updated-name";
        doPut(createdState);

        // DELETE
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
    }

    @Test
    public void testProjectAdminRestrictionsToCertificates() throws Throwable {

        SslTrustCertificateState cert = new SslTrustCertificateState();
        cert.certificate = CommonTestStateFactory.getFileContent("test_ssl_trust.PEM").trim();

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        SslTrustCertificateState createdState = doPost(cert, SslTrustCertificateService.FACTORY_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_GLORIA));

        // GET
        getDocument(SslTrustCertificateState.class, createdState.documentSelfLink);

        // POST
        doPost(cert, SslTrustCertificateService.FACTORY_LINK);

        // PUT
        createdState.commonName = "updated-name";
        doPut(createdState);

        // DELETE
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
    }


    @Test
    public void testProjectAdminRestrictionsToRegistries() throws Throwable {

        RegistryState registry = new RegistryState();
        registry.name = "test";
        registry.tenantLinks = Collections.singletonList(createdProject.documentSelfLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        RegistryState createdState = doPost(registry, RegistryFactoryService.SELF_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_GLORIA));

        // GET
        getDocument(RegistryState.class, createdState.documentSelfLink);

        // POST
        doPost(registry, RegistryFactoryService.SELF_LINK);

        // PUT
        createdState.name = "updated-name";
        doPut(createdState);

        // DELETE
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
    }

    @Test
    public void testProjectAdminRestrictionsToConfiguration() throws Throwable {
        ConfigurationState config = new ConfigurationState();
        config.key = "key";
        config.value = "value";

        // use cloud admin for creation of the state
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        ConfigurationState createdState = doPost(config, ConfigurationFactoryService.SELF_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);
        assertEquals(config.key, createdState.key);
        assertEquals(config.value, createdState.value);

        // switch role to project admin
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_GLORIA));

        // GET
        ConfigurationState retrievedState = getDocument(ConfigurationState.class, createdState.documentSelfLink);
        assertNotNull(retrievedState);

        // POST
        doPostWithRestrictionVerification(config, ConfigurationFactoryService.SELF_LINK);

        // PUT
        createdState.value = "updated-value";
        doPutWithRestrictionVerification(createdState, ConfigurationFactoryService.SELF_LINK);

        // DELETE
        doDeleteWithRestrictionVerification(createdState, ConfigurationFactoryService.SELF_LINK);
    }

    @Test
    public void testProjectAdminRestrictionsToLogs() throws Throwable {
        LogServiceState log = new LogServiceState();
        log.logs = new byte[] { 1 };

        // use admin for creation of the state
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        LogServiceState createdState = doPost(log, LogService.FACTORY_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);
        assertEquals(log.logs[0], createdState.logs[0]);

        // switch role to project admin
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_GLORIA));

        // GET
        doGetWithRestrictionVerification(createdState, LogService.FACTORY_LINK, LogServiceState.class.getName());

        // POST
        doPostWithRestrictionVerification(log, LogService.FACTORY_LINK);

        // PUT
        doPutWithRestrictionVerification(createdState, LogService.FACTORY_LINK);

        // DELETE
        doDeleteWithRestrictionVerification(createdState, LogService.FACTORY_LINK);
    }

    @Test
    public void testProjectAdminRestrictionsToProjectsHeDoesNotBelongToAsAdmin() throws Throwable {

        ProjectState project = new ProjectState();
        project.name = "test-name";

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        ProjectState createdState = doPost(project, ProjectFactoryService.SELF_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_GLORIA));

        // POST
        project.name = "test.name";
        doPostWithRestrictionVerification(project, ProjectFactoryService.SELF_LINK);

        // PUT
        createdState.name = "updated-name";
        doPutWithRestrictionVerification(createdState, ProjectFactoryService.SELF_LINK);

        // DELETE
        doDeleteWithRestrictionVerification(createdState, ProjectFactoryService.SELF_LINK);
    }

    @Test
    public void testProjectAdminHasAccessToTheProjectsHeBelongsToAsAdmin() throws Throwable {

        ProjectState project = new ProjectState();
        project.name = "test";

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_GLORIA));

        // GET
        ProjectState retrievedState = getDocument(ProjectState.class, createdProject.documentSelfLink);
        assertNotNull(retrievedState);

        // PUT
        this.createdProject.name = "updated-name";
        ProjectState updatedState = doPut(createdProject);
        assertNotNull(updatedState);
        assertEquals(createdProject.name, updatedState.name);

        // PATCH
        ProjectState state = new ProjectState();
        state.name = "patched-name";

        ProjectState patchedState = doPatch(state, createdProject.documentSelfLink);
        assertNotNull(patchedState);
        assertEquals(state.name, patchedState.name);

        // DELETE
        doDeleteWithRestrictionVerification(retrievedState, ProjectFactoryService.SELF_LINK);
    }

    @Test
    public void testProjectAdminHasAccessToTheResourcesOfTheProjectHeBelongsToAsAdmin() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_GLORIA));

        ContainerDescription cd = new ContainerDescription();
        cd.image = "test";
        cd.tenantLinks = new ArrayList<String>();
        cd.tenantLinks.add(createdProject.documentSelfLink);

        // POST
        ContainerDescription createdContainerDesc = doPost(cd, ContainerDescriptionService.FACTORY_LINK);

        // GET
        ContainerDescription retrievedState = getDocument(ContainerDescription.class, createdContainerDesc.documentSelfLink);
        assertNotNull(retrievedState);
        assertEquals(retrievedState.tenantLinks, createdContainerDesc.tenantLinks);

        // PUT
        retrievedState.name = "updated_name";
        ContainerDescription updatedState = doPut(retrievedState);
        assertNotNull(updatedState);
        assertEquals(retrievedState.name, updatedState.name);

        // DELETE
        doDelete(UriUtils.buildUri(host, createdContainerDesc.documentSelfLink), false);
    }

    @Test
    public void testCloudAdminCanAssignCloudAdminRole() throws Throwable {

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_CLOUD_ADMIN));
        assignCloudAdminRoleTo(USER_EMAIL_BASIC_USER);
        PrincipalRoles roles = getUserRolesFor(USER_EMAIL_BASIC_USER);

        assertNotNull("could not retrieve roles for user " + USER_EMAIL_BASIC_USER, roles);
        assertNotNull("roles set is empty or null for user " + USER_EMAIL_BASIC_USER, roles.roles);
        assertThat(
                "Expected user " + USER_EMAIL_BASIC_USER + " to have role "
                        + AuthRole.CLOUD_ADMIN.toString(),
                roles.roles, hasItem(AuthRole.CLOUD_ADMIN));
    }

    @Test
    public void testProjectAdminCannotAssignCloudAdminRole() throws Throwable {
        assertCannotAssignCloudAdminRoleAs(USER_EMAIL_PROJECT_ADMIN_1);
    }

    @Test
    public void testProjectMemberCannotAssignCloudAdminRole() throws Throwable {
        assertCannotAssignCloudAdminRoleAs(USER_EMAIL_PROJECT_MEMBER_1);
    }

    @Test
    public void testProjectViewerCannotAssignCloudAdminRole() throws Throwable {
        assertCannotAssignCloudAdminRoleAs(USER_EMAIL_PROJECT_VIEWER_1);
    }

    @Test
    public void testBasicUserCannotAssignCloudAdminRole() throws Throwable {
        assertCannotAssignCloudAdminRoleAs(USER_EMAIL_BASIC_USER);
    }

    private void assertCannotAssignCloudAdminRoleAs(String principalId) throws Throwable {
        host.assumeIdentity(buildUserServicePath(principalId));
        try {
            assignCloudAdminRoleTo(USER_EMAIL_BASIC_USER);
            fail(String.format(
                    "Expected user '%s' not to have the privilege to assign the cloud admin role",
                    principalId));
        } catch (IllegalAccessError e) {
            assertThat("Unexpected failure, expected forbidden message",
                    e.getMessage(), containsString(FORBIDDEN));
        }

        PrincipalRoles roles = getUserRolesFor(USER_EMAIL_BASIC_USER);
        assertNotNull("could not retrieve roles for user " + USER_EMAIL_BASIC_USER, roles);
        assertNotNull("roles set is empty or null for user " + USER_EMAIL_BASIC_USER, roles.roles);
        String msg = String.format("Expected user '%s' not to have role '%s'",
                USER_EMAIL_BASIC_USER,
                AuthRole.CLOUD_ADMIN);
        Assert.assertThat(msg, roles.roles, not(hasItem(AuthRole.CLOUD_ADMIN)));

    }

    private void assignCloudAdminRoleTo(String principalId) throws Throwable {
        String rolesLink = buildRolesLinkFor(principalId);

        PrincipalRoleAssignment body = new PrincipalRoleAssignment();
        body.add = Collections.singletonList(AuthRole.CLOUD_ADMIN.toString());

        doPatch(body, rolesLink);

    }

    private PrincipalRoles getUserRolesFor(String principalId) throws Throwable {
        String rolesLink = buildRolesLinkFor(principalId);
        return getDocument(PrincipalRoles.class, rolesLink);
    }

    private String buildRolesLinkFor(String principalId) {
        return UriUtils.buildUriPath(PrincipalService.SELF_LINK,
                principalId,
                PrincipalService.ROLES_SUFFIX);
    }


    private void assertForbiddenMessage(IllegalAccessError e) {
        assertTrue(e.getMessage().toLowerCase().startsWith(FORBIDDEN));
    }

    private void doPostWithRestrictionVerification(ServiceDocument doc, String selfLink) throws Throwable {
        host.log("POST to %s", selfLink);

        try {
            doPost(doc, selfLink);
            fail(EXPECTED_ILLEGAL_ACCESS_ERROR_MESSAGE);
        } catch (IllegalAccessError e) {
            assertForbiddenMessage(e);
        }
    }

    private void doGetWithRestrictionVerification(ServiceDocument createdState, String selfLink, String className) throws Throwable {
        host.log("GET to %s", selfLink);

        // Verify basic user cannot list the documents
        List<String> docs = getDocument(
                ServiceDocumentQueryResult.class, selfLink)
                .documentLinks;
        assertTrue(docs == null || docs.isEmpty());

        try {
            getDocument(Class.forName(className), createdState.documentSelfLink);
            fail(EXPECTED_ILLEGAL_ACCESS_ERROR_MESSAGE);
        } catch (IllegalAccessError e) {
            assertForbiddenMessage(e);
        }
    }

    private void doPutWithRestrictionVerification(ServiceDocument doc, String selfLink) throws Throwable {
        host.log("PUT to %s", selfLink);

        try {
            doPut(doc);
            fail(EXPECTED_ILLEGAL_ACCESS_ERROR_MESSAGE);
        } catch (IllegalAccessError e) {
            assertForbiddenMessage(e);
        }
    }

    private void doPatchWithRestrictionVerification(ServiceDocument doc, String selfLink)
            throws Throwable {
        host.log("PATCH to %s", selfLink);

        try {
            doPatch(doc, selfLink);
            fail(EXPECTED_ILLEGAL_ACCESS_ERROR_MESSAGE);
        } catch (IllegalAccessError e) {
            assertForbiddenMessage(e);
        }
    }

    private void doDeleteWithRestrictionVerification(ServiceDocument doc, String selfLink) throws Throwable {
        host.log("DELETE to %s", selfLink);

        try {
            doDelete(UriUtils.buildUri(host, doc.documentSelfLink), false);
            fail(EXPECTED_ILLEGAL_ACCESS_ERROR_MESSAGE);
        } catch (IllegalAccessError e) {
            assertForbiddenMessage(e);
        }
    }

}
