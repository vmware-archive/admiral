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

package com.vmware.admiral.auth.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles;
import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.auth.project.ProjectService.ExpandedProjectState;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.compute.cluster.ClusterUtils;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.service.common.HbrApiProxyService;
import com.vmware.admiral.service.common.mock.MockHbrApiProxyService;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTemplate;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

public class ProjectUtilWithServicesTest extends AuthBaseTest {

    private static final String TEST_PROJECT_NAME = "testProject";
    private static final String TEST_CLUSTER_NAME = "testClustet";
    private static final String TEST_TEMPLATE_NAME = "testTemplate";

    @Test
    public void testRetrieveUserStatesForGroup() throws Throwable {
        // become admin
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        String groupName = "test-group";
        List<String> users = Arrays.asList("test-user-a@example.com", "test-user-b@local");

        // create a test gropup
        UserGroupState groupState = doPost(AuthUtil.buildUserGroupState(groupName),
                UserGroupService.FACTORY_LINK);

        // create user states
        List<UserState> userStates = users.stream().map((userName) -> {
            UserState userState = new UserState();
            userState.email = userName;
            userState.userGroupLinks = Collections.singleton(groupState.documentSelfLink);
            try {
                return doPost(userState, buildUserServicePath(""));
            } catch (Throwable e) {
                e.printStackTrace();
                fail(Utils.toString(e));
                return null;
            }
        }).collect(Collectors.toList());

        // verify
        List<UserState> retrievedUsers = QueryTemplate.waitToComplete(ProjectUtil
                .retrieveUserStatesForGroup(host, AuthUtil.buildUserGroupState(groupName)));
        assertNotNull(retrievedUsers);
        assertEquals(userStates.size(), retrievedUsers.size());
        assertTrue(retrievedUsers.stream()
                .allMatch((user) -> user.userGroupLinks.contains(groupState.documentSelfLink)
                        && users.contains(user.email)));
    }

    @Test
    public void testExpandProjectState() throws Throwable {
        waitForServiceAvailability(ProjectFactoryService.SELF_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        ProjectState project = createTestProject();
        ResourcePoolState cluster = createCluster(project.documentSelfLink);
        CompositeDescription template = createTemplate(project.documentSelfLink);

        ExpandedProjectState expandedState = QueryTemplate
                .waitToComplete(ProjectUtil.expandProjectState(host, project, host.getUri()));

        // verify admins
        assertNotNull(expandedState.administrators);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);

        // verify members
        assertNotNull(expandedState.members);
        assertEquals(1, expandedState.members.size());
        assertEquals(USER_EMAIL_ADMIN, expandedState.members.iterator().next().email);

        // verify viewers
        assertNotNull(expandedState.viewers);
        assertEquals(1, expandedState.viewers.size());
        assertEquals(USER_EMAIL_BASIC_USER, expandedState.viewers.iterator().next().email);

        // verify clusters
        assertNotNull(expandedState.clusterLinks);
        assertEquals(1, expandedState.clusterLinks.size());
        assertEquals(ClusterUtils.toClusterSelfLink(cluster.documentSelfLink),
                expandedState.clusterLinks.iterator().next());

        // verify templates
        assertNotNull(expandedState.templateLinks);
        assertEquals(1, expandedState.templateLinks.size());
        assertEquals(template.documentSelfLink, expandedState.templateLinks.iterator().next());

        // verify repositories
        assertNotNull(expandedState.repositories);
        assertEquals(MockHbrApiProxyService.mockedRepositories.size(),
                expandedState.repositories.size());
        assertTrue(expandedState.repositories.stream()
                .allMatch(MockHbrApiProxyService.mockedRepositories::containsKey));
        assertNotNull(expandedState.numberOfImages);
        long expectedNumImages = MockHbrApiProxyService.mockedRepositories.values().stream()
                .map((entry) -> (long) entry.get(HbrApiProxyService.HARBOR_RESP_PROP_TAGS_COUNT))
                .reduce(0L, Long::sum);
        assertEquals(expectedNumImages, expandedState.numberOfImages.longValue());
    }

    private ProjectState createTestProject() throws Throwable {
        // Prepare harbor id for this project
        HashMap<String, String> customProperties = new HashMap<>();
        customProperties.put(ProjectService.CUSTOM_PROPERTY_PROJECT_INDEX,
                "" + MockHbrApiProxyService.MOCKED_PROJECT_ID);

        // Create project
        ProjectState project = createProject(TEST_PROJECT_NAME, customProperties);

        // Add the ADMIN as admin and a member
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.viewers = new PrincipalRoleAssignment();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators = new PrincipalRoleAssignment();

        projectRoles.administrators.add = Collections.singletonList(USER_EMAIL_ADMIN);
        projectRoles.members.add = Collections.singletonList(USER_EMAIL_ADMIN);
        projectRoles.viewers.add = Collections.singletonList(USER_EMAIL_BASIC_USER);

        doPatch(projectRoles, project.documentSelfLink);

        // return the result
        return getDocument(ProjectState.class, project.documentSelfLink);
    }

    private ResourcePoolState createCluster(String projectLink) throws Throwable {
        ResourcePoolState cluster = new ResourcePoolState();
        cluster.name = TEST_CLUSTER_NAME;
        cluster.tenantLinks = Collections.singletonList(projectLink);
        return doPost(cluster, ResourcePoolService.FACTORY_LINK);
    }

    private CompositeDescription createTemplate(String projectLink) throws Throwable {
        CompositeDescription template = new CompositeDescription();
        template.name = TEST_TEMPLATE_NAME;
        template.tenantLinks = Collections.singletonList(projectLink);
        return doPost(template, CompositeDescriptionService.SELF_LINK);
    }
}
