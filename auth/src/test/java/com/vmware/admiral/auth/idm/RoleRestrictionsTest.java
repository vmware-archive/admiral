/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.auth.idm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import com.vmware.admiral.service.common.RegistryService;
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

        // Assign basic user to the project as Project Admin
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.administrators.add = Arrays.asList(USER_EMAIL_GLORIA);

        ExpandedProjectState expandedProjectState = getExpandedProjectState(createdProject.documentSelfLink);
        doPatch(projectRoles, expandedProjectState.documentSelfLink);
    }

    @Test
    public void testClaudAdminHasAccessToCredentials() throws Throwable {
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
    public void testClaudAdminHasAccessToCertificates() throws Throwable {
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
    public void testClaudAdminHasAccessToRegistries() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        RegistryState registry = new RegistryState();
        registry.name = "test";

        // POST
        RegistryState createdState = doPost(registry, RegistryService.FACTORY_LINK);
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
    public void testClaudAdminHasAccessToProjects() throws Throwable {
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
    public void testClaudAdminHasAccessToConfiguration() throws Throwable {
        // TODO: WIP
    }

    @Test
    public void testClaudAdminHasAccessToLogs() throws Throwable {
        // TODO: WIP
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
        RegistryState createdState = doPost(registry, RegistryService.FACTORY_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));
        doGetWithRestrictionVerification(createdState, RegistryService.FACTORY_LINK, RegistryState.class.getName());

        // POST
        doPostWithRestrictionVerification(registry, RegistryService.FACTORY_LINK);

        // PUT
        createdState.name = "updated-name";
        doPutWithRestrictionVerification(createdState, RegistryService.FACTORY_LINK);

        // DELETE
        doDeleteWithRestrictionVerification(createdState, RegistryService.FACTORY_LINK);
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

        // DELETE
        doDeleteWithRestrictionVerification(createdState, ProjectFactoryService.SELF_LINK);
    }

    @Test
    public void testBasicUserRestrictionsToConfiguration() throws Throwable {
        // TODO: WIP
    }

    @Test
    public void testBasicUserRestrictionsToLogs() throws Throwable {
        // TODO: WIP
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
    public void testProjectAdminRestrictionsToRegistries() throws Throwable {

        RegistryState registry = new RegistryState();
        registry.name = "test";

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        RegistryState createdState = doPost(registry, RegistryService.FACTORY_LINK);
        assertNotNull(createdState);
        assertNotNull(createdState.documentSelfLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_GLORIA));

        // GET
        getDocument(RegistryState.class, createdState.documentSelfLink);

        // POST
        doPost(registry, RegistryService.FACTORY_LINK);

        // PUT
        createdState.name = "updated-name";
        doPut(createdState);

        // DELETE
        doDelete(UriUtils.buildUri(host, createdState.documentSelfLink), false);
    }

    @Test
    public void testProjectAdminRestrictionsToConfiguration() throws Throwable {
        // TODO: WIP
    }

    @Test
    public void testProjectAdminRestrictionsToLogs() throws Throwable {
        // TODO: WIP
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
        project.name = "test--name";
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
