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

package com.vmware.admiral.auth.project;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.compute.RegistryHostConfigService;
import com.vmware.admiral.compute.RegistryHostConfigService.RegistryHostSpec;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.kubernetes.entities.pods.Container;
import com.vmware.admiral.compute.kubernetes.entities.pods.Pod;
import com.vmware.admiral.compute.kubernetes.entities.pods.PodSpec;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.admiral.compute.kubernetes.service.DeploymentFactoryService;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService;
import com.vmware.admiral.compute.kubernetes.service.GenericKubernetesEntityFactoryService;
import com.vmware.admiral.compute.kubernetes.service.GenericKubernetesEntityService;
import com.vmware.admiral.compute.kubernetes.service.PodFactoryService;
import com.vmware.admiral.compute.kubernetes.service.PodLogService;
import com.vmware.admiral.compute.kubernetes.service.PodService;
import com.vmware.admiral.compute.kubernetes.service.ReplicaSetService;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityFactoryHandler;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ProjectRoleRestrictionsTest extends AuthBaseTest {

    private static final String AUTH_CONTENT_FILE_NAME = "roles-restrictions-auth-content.json";
    private static final String USER_EMAIL_FRITZ = "fritz@admiral.com";
    private static final String USER_EMAIL_TONY = "tony@admiral.com";

    @Before
    public void setup() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN2));
        loadAuthContent(AUTH_CONTENT_FILE_NAME);
    }

    @Test
    public void testProjectMembersCannotModifyComputes() throws Throwable {
        // create compute state in project 1 as cloud admin
        ComputeState csProject1 = createComputeStateAsUser(PROJECT_NAME_TEST_PROJECT_1, USER_EMAIL_ADMIN2);

        // Project members should be able to access the document
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_CONNIE, true);

        // Verify compute state modification is forbidden
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_CONNIE));
        // POST
        ComputeState cs = new ComputeState();
        cs.name = "test";
        cs.descriptionLink = "descLink";
        doPostWithRestrictionVerification(cs, ComputeService.FACTORY_LINK);
        // PUT
        csProject1.name = "updated-name";
        doPutWithRestrictionVerification(csProject1, ComputeService.FACTORY_LINK);
        // PATCH
        doPatchWithRestrictionVerification(csProject1, csProject1.documentSelfLink);
        // DELETE
        doDeleteWithRestrictionVerification(csProject1, ComputeService.FACTORY_LINK);
    }

    @Test
    public void testComputesFromOtherProjectsAreNotAccessible() throws Throwable {
        // create compute state in project 1 as cloud admin
        ComputeState csProject1 = createComputeStateAsUser(PROJECT_NAME_TEST_PROJECT_1, USER_EMAIL_ADMIN2);
        // create compute state in project 2 as cloud admin
        ComputeState csProject2 = createComputeStateAsUser(PROJECT_NAME_TEST_PROJECT_2, USER_EMAIL_ADMIN2);

        // Project members and admins should be able to access the document, other users should not have access
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_CONNIE, true);
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_FRITZ, true);
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_GLORIA, false);

        verifyDocumentAccessible(csProject2.documentSelfLink, USER_EMAIL_GLORIA, true);
        verifyDocumentAccessible(csProject2.documentSelfLink, USER_EMAIL_FRITZ, true);
        verifyDocumentAccessible(csProject2.documentSelfLink, USER_EMAIL_CONNIE, false);
    }

    @Test
    public void testContainersFromOtherProjectsAreNotAccessible() throws Throwable {
        // create container state in project 1 as a member
        ContainerState csProject1 = createContainerStateAsUser(PROJECT_NAME_TEST_PROJECT_1, USER_EMAIL_CONNIE);
        // create container state in project 2 as another member
        ContainerState csProject2 = createContainerStateAsUser(PROJECT_NAME_TEST_PROJECT_2, USER_EMAIL_GLORIA);

        // Project members and admins should be able to access the document, other users should not have access
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_CONNIE, true);
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_FRITZ, true);
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_GLORIA, false);

        verifyDocumentAccessible(csProject2.documentSelfLink, USER_EMAIL_GLORIA, true);
        verifyDocumentAccessible(csProject2.documentSelfLink, USER_EMAIL_FRITZ, true);
        verifyDocumentAccessible(csProject2.documentSelfLink, USER_EMAIL_CONNIE, false);
    }

    @Test
    public void testK8sResourcesFromOtherProjectsAreNotAccessible() throws Throwable {
        ArrayList<Pair<String, BaseKubernetesState>> entities = new ArrayList<>();
        entities.add(new Pair<>(PodFactoryService.SELF_LINK,
                new PodService.PodState()));
        entities.add(new Pair<>(DeploymentFactoryService.SELF_LINK,
                new DeploymentService.DeploymentState()));
        entities.add(new Pair<>(GenericKubernetesEntityFactoryService.SELF_LINK,
                new GenericKubernetesEntityService.GenericKubernetesEntityState()));
        entities.add(new Pair<>(ReplicaSetService.FACTORY_LINK,
                new ReplicaSetService.ReplicaSetState()));
        entities.add(new Pair<>(ReplicationControllerService.FACTORY_LINK,
                new ReplicationControllerService.ReplicationControllerState()));
        entities.add(new Pair<>(ServiceEntityFactoryHandler.SELF_LINK,
                new ServiceEntityHandler.ServiceState()));

        for (Pair<String, BaseKubernetesState> pair : entities) {

            host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN2));

            // create k8s resource state in project 1 as a member
            BaseKubernetesState resourceProject1 = createK8sStateAsUser(
                    PROJECT_NAME_TEST_PROJECT_1, USER_EMAIL_CONNIE,
                    pair.left, pair.right);
            // create k8s resource state in project 2 as another member
            BaseKubernetesState resourceProject2 = createK8sStateAsUser(
                    PROJECT_NAME_TEST_PROJECT_2, USER_EMAIL_GLORIA,
                    pair.left, pair.right);

            // Project members and admins should be able to access the document, other users should not have access
            verifyDocumentAccessible(resourceProject1.documentSelfLink, USER_EMAIL_CONNIE, true);
            verifyDocumentAccessible(resourceProject1.documentSelfLink, USER_EMAIL_FRITZ, true);
            verifyDocumentAccessible(resourceProject1.documentSelfLink, USER_EMAIL_GLORIA, false);
            verifyDocumentAccessible(resourceProject1.documentSelfLink,
                    USER_EMAIL_BASIC_UNASSIGNED_USER, false);

            verifyDocumentAccessible(resourceProject2.documentSelfLink, USER_EMAIL_GLORIA, true);
            verifyDocumentAccessible(resourceProject2.documentSelfLink, USER_EMAIL_FRITZ, true);
            verifyDocumentAccessible(resourceProject2.documentSelfLink, USER_EMAIL_CONNIE, false);
            verifyDocumentAccessible(resourceProject2.documentSelfLink,
                    USER_EMAIL_BASIC_UNASSIGNED_USER, false);
        }

        // Verify that pod logs are not accessible by users not in the project
        // create k8s resource state in project 1 as a member
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN2));
        PodService.PodState state = new PodService.PodState();
        state.pod = new Pod();
        state.pod.spec = new PodSpec();
        state.pod.spec.containers = new ArrayList<>();
        Container c = new Container();
        c.name = "fake";
        state.pod.spec.containers.add(c);
        BaseKubernetesState podProject1 = createK8sStateAsUser(PROJECT_NAME_TEST_PROJECT_1,
                USER_EMAIL_CONNIE, PodFactoryService.SELF_LINK, state);
        String logsLink = PodLogService.SELF_LINK + "?" + PodLogService.POD_ID_QUERY_PARAM + "="
                + UriUtils.getLastPathSegment(podProject1.documentSelfLink);
        verifyDocumentAccessible(logsLink, USER_EMAIL_CONNIE, true);
        verifyDocumentAccessible(logsLink, USER_EMAIL_FRITZ, true);
        verifyDocumentAccessible(logsLink, USER_EMAIL_GLORIA, false);
        verifyDocumentAccessible(logsLink, USER_EMAIL_BASIC_UNASSIGNED_USER, false);
    }

    @Test
    public void testContainersOfOtherMembersOfTheProjectsAreAccessible() throws Throwable {
        // create container state in project 1 as a member
        ContainerState cs1 = createContainerStateAsUser(PROJECT_NAME_TEST_PROJECT_1, USER_EMAIL_CONNIE);
        // create container state in project 1 as another member
        ContainerState cs2 = createContainerStateAsUser(PROJECT_NAME_TEST_PROJECT_1, USER_EMAIL_TONY);

        // Project members and admins should be able to access all documents in the project
        verifyDocumentAccessible(cs1.documentSelfLink, USER_EMAIL_CONNIE, true);
        verifyDocumentAccessible(cs1.documentSelfLink, USER_EMAIL_TONY, true);
        verifyDocumentAccessible(cs1.documentSelfLink, USER_EMAIL_FRITZ, true);

        verifyDocumentAccessible(cs2.documentSelfLink, USER_EMAIL_CONNIE, true);
        verifyDocumentAccessible(cs2.documentSelfLink, USER_EMAIL_TONY, true);
        verifyDocumentAccessible(cs2.documentSelfLink, USER_EMAIL_FRITZ, true);
    }

    @Test
    public void testProjectMemberCannotModifyProject() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_TONY));

        String projectLink = getProjectLinkByName(PROJECT_NAME_TEST_PROJECT_1);

        ProjectState project = new ProjectState();
        project.name = "test-name";

        try {
            doPatch(project, projectLink);
            fail(EXPECTED_ILLEGAL_ACCESS_ERROR_MESSAGE);
        } catch (IllegalAccessError e) {
            assertForbiddenMessage(e);
        }
    }

    @Test
    public void testProjectAdminCanModifyProject() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_FRITZ));

        String projectLink = getProjectLinkByName(PROJECT_NAME_TEST_PROJECT_1);

        ProjectState project = new ProjectState();
        project.name = "test-name";

        doPatch(project, projectLink);
    }

    @Test
    public void testCloudAdminCanAddProjectRegistry() throws Throwable {
        createProjectSpecificRegistryStateAsUser(PROJECT_NAME_TEST_PROJECT_1, USER_EMAIL_ADMIN2);
    }

    @Test
    public void testProjectAdminCanAddProjectRegistry() throws Throwable {
        createProjectSpecificRegistryStateAsUser(PROJECT_NAME_TEST_PROJECT_1, USER_EMAIL_FRITZ);
    }

    @Test
    public void testProjectAdminCannotAddGlobalRegistry() throws Throwable {
        try {
            createProjectSpecificRegistryStateAsUser(null, USER_EMAIL_FRITZ);
            fail("Expected project admins not to be able to create global registries");
        } catch (IllegalAccessError ex) {
            // expected
        }
    }

    @Test
    public void testProjectMemberCannotAddProjectRegistry() throws Throwable {
        try {
            createProjectSpecificRegistryStateAsUser(PROJECT_NAME_TEST_PROJECT_1,
                    USER_EMAIL_GLORIA);
            fail("Expected project members not to be able to create project-specific registries");
        } catch (IllegalAccessError ex) {
            // expected
        }
    }

    private ComputeState createComputeStateAsUser(String projectName, String userEmail)
            throws Throwable {
        String projectLink = getProjectLinkByName(projectName);
        String userLink = buildUserServicePath(userEmail);

        host.assumeIdentity(userLink);

        ComputeState cs = new ComputeState();
        cs.name = UUID.randomUUID().toString();
        cs.descriptionLink = "desc-link";
        cs.type = ComputeType.VM_GUEST;
        cs.tenantLinks = new ArrayList<>();
        cs.tenantLinks.add(projectLink);

        ComputeState result = doPost(cs, ComputeService.FACTORY_LINK);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN2));

        return result;
    }

    private ContainerState createContainerStateAsUser(String projectName, String userEmail)
            throws Throwable {
        String projectLink = getProjectLinkByName(projectName);
        String userLink = buildUserServicePath(userEmail);

        host.assumeIdentity(userLink);

        ContainerState cs = new ContainerState();
        cs.name = UUID.randomUUID().toString();
        cs.descriptionLink = "desc-link";
        cs.tenantLinks = new ArrayList<>();
        cs.tenantLinks.add(projectLink);

        ContainerState result = doPost(cs, ContainerFactoryService.SELF_LINK);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN2));

        return result;
    }

    private RegistryState createProjectSpecificRegistryStateAsUser(String projectName,
            String userEmail)
            throws Throwable {
        String projectLink = projectName == null ? null : getProjectLinkByName(projectName);
        String userLink = buildUserServicePath(userEmail);

        host.assumeIdentity(userLink);

        RegistryState rs = new RegistryState();
        rs.name = UUID.randomUUID().toString();
        rs.address = rs.name;
        rs.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;
        rs.tenantLinks = projectLink == null ? null : Collections.singletonList(projectLink);

        RegistryHostSpec hostSpec = new RegistryHostSpec();
        hostSpec.acceptCertificate = true;
        hostSpec.acceptHostAddress = true;
        hostSpec.hostState = rs;

        String[] resultLink = new String[1];
        Operation put = Operation.createPut(host, RegistryHostConfigService.SELF_LINK)
                .setReferer("/")
                .setBody(hostSpec)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to PUT a new registry: %s",
                                Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        resultLink[0] = o.getResponseHeader(Operation.LOCATION_HEADER);
                        host.log(Level.INFO, "Created a new registry with selfLink [%s]",
                                resultLink[0]);
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(put);
        host.testWait();

        assertNotNull(resultLink[0]);
        RegistryState result = getDocument(RegistryState.class, resultLink[0]);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN2));

        return result;
    }

    private <T extends BaseKubernetesState> T createK8sStateAsUser(String projectName,
            String userEmail, String factoryLink, T state) throws Throwable {

        String projectLink = getProjectLinkByName(projectName);
        String userLink = buildUserServicePath(userEmail);

        host.assumeIdentity(userLink);

        state.name = UUID.randomUUID().toString();
        state.descriptionLink = "desc-link";
        state.tenantLinks = new ArrayList<>();
        state.tenantLinks.add(projectLink);

        T result = doPost(state, factoryLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN2));

        return result;
    }

    private void assertForbiddenMessage(IllegalAccessError e) {
        assertTrue(e.getMessage().toLowerCase().startsWith(FORBIDDEN));
    }

}
