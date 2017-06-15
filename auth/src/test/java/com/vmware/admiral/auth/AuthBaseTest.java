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

package com.vmware.admiral.auth;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import static com.vmware.admiral.auth.util.AuthUtil.BASIC_USERS_RESOURCE_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.BASIC_USERS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.CLOUD_ADMINS_RESOURCE_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.CLOUD_ADMINS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.DEFAULT_BASIC_USERS_ROLE_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.DEFAULT_CLOUD_ADMINS_ROLE_LINK;
import static com.vmware.admiral.auth.util.ProjectUtil.retrieveUserStatesForGroup;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;

import com.vmware.admiral.auth.idm.AuthConfigProvider;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.idm.local.LocalAuthConfigProvider.Config;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.host.HostInitAuthServiceConfig;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.service.common.AuthBootstrapService;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.UserService.UserState;

public abstract class AuthBaseTest extends BaseTestCase {
    public static final int DEFAULT_WAIT_SECONDS_FOR_AUTH_SERVICES = 180;

    protected static final String USER_EMAIL_ADMIN = "fritz@admiral.com";
    protected static final String USER_EMAIL_BASIC_USER = "tony@admiral.com";
    protected static final String USER_EMAIL_GLORIA = "gloria@admiral.com";
    protected static final String USER_EMAIL_CONNIE = "connie@admiral.com";

    protected static final String USER_NAME_ADMIN = "Fritz";
    protected static final String USER_NAME_BASIC_USER = "Tony";
    protected static final String USER_NAME_GLORIA = "Gloria";
    protected static final String USER_NAME_CONNIE = "Connie";

    private static final String LOCAL_USERS_FILE = "/local-users.json";

    protected List<String> loadedUsers;
    protected List<String> loadedGroups;

    @Before
    public void beforeForAuthBase() throws Throwable {
        host.setSystemAuthorizationContext();

        setPrivilegedServices();
        startServices(host);

        waitForServiceAvailability(AuthInitialBootService.SELF_LINK);
        waitForInitialBootServiceToBeSelfStopped(AuthInitialBootService.SELF_LINK);
        waitForDefaultRoles();
        waitForDefaultUsersAndGroups();
        TestContext ctx = new TestContext(1,
                Duration.ofSeconds(DEFAULT_WAIT_SECONDS_FOR_AUTH_SERVICES));
        AuthUtil.getPreferredProvider(AuthConfigProvider.class).waitForInitConfig(host,
                ((CustomizationVerificationHost) host).localUsers,
                ctx::completeIteration, ctx::failIteration);
        ctx.await();
        host.resetAuthorizationContext();
    }

    @Override
    protected VerificationHost createHost() throws Throwable {
        String[] customArgs = {
                CommandLineArgumentParser.ARGUMENT_PREFIX
                        + AuthUtil.LOCAL_USERS_FILE
                        + CommandLineArgumentParser.ARGUMENT_ASSIGNMENT
                        + AuthBaseTest.class.getResource(LOCAL_USERS_FILE).toURI().getPath()
        };
        return createHost(customArgs);
    }

    protected void setPrivilegedServices() {
        // TODO remove the Principal service from this list once the security context gets exposed
        // trough the session service
        host.addPrivilegedService(PrincipalService.class);
    }

    protected void startServices(VerificationHost host) throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);

        HostInitCommonServiceConfig.startServices(host);
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, true);
        HostInitAuthServiceConfig.startServices(host);

        host.registerForServiceAvailability(AuthBootstrapService.startTask(host), true,
                AuthBootstrapService.FACTORY_LINK);
    }

    protected ProjectState createProject(String name) throws Throwable {
        return createProject(name, null, false, null, null);
    }

    protected ProjectState createProject(String name, Map<String, String> customProperties)
            throws Throwable {
        return createProject(name, null, false, null, null, customProperties);
    }

    protected ProjectState createProject(String name, String description, boolean isPublic)
            throws Throwable {
        return createProject(name, description, isPublic, null, null);
    }

    protected ProjectState createProject(String name, String description, boolean isPublic,
            Map<String, String> customProperties) throws Throwable {
        return createProject(name, description, isPublic, null, null, customProperties);
    }

    protected ProjectState createProject(String name, String description, boolean isPublic,
            String adminsGroupLink, String membersGroupLink) throws Throwable {
        return createProject(name, description, isPublic, adminsGroupLink, membersGroupLink, null);
    }

    protected ProjectState createProject(String name, String description, boolean isPublic,
            String adminsGroupLink, String membersGroupLink, Map<String, String> customProperties)
            throws Throwable {
        ProjectState projectState = new ProjectState();

        projectState.id = UUID.randomUUID().toString();
        projectState.name = name;
        projectState.description = description;
        projectState.isPublic = isPublic;
        projectState.administratorsUserGroupLink = adminsGroupLink;
        projectState.membersUserGroupLink = membersGroupLink;
        projectState.customProperties = customProperties;

        projectState = doPost(projectState, ProjectFactoryService.SELF_LINK);

        return projectState;
    }

    protected ProjectState patchProject(ProjectState patchState, String projectSelfLink)
            throws Throwable {
        ProjectState patchedState = doPatch(patchState, projectSelfLink);

        return patchedState;
    }

    protected ProjectState updateProject(ProjectState updateState) throws Throwable {
        ProjectState updatedProject = doPut(updateState);

        return updatedProject;
    }

    protected void deleteProject(ProjectState projectToBeDeleted) throws Throwable {
        URI projectUri = UriUtils.buildUri(host, projectToBeDeleted.documentSelfLink);

        doDelete(projectUri, false);
    }

    protected void verifyExceptionMessage(String expected, String message) {
        if (!expected.equals(message)) {
            String errorMessage = String.format("Expected error '%s' but was '%s'", expected,
                    message);
            throw new IllegalStateException(errorMessage);
        }
    }

    protected String buildUserServicePath(String email) {
        return UriUtils.buildUriPath(UserService.FACTORY_LINK, email);
    }

    protected void doPatch(Object state, String documentSelfLink) {
        TestContext ctx = testCreate(1);
        Operation patch = Operation.createPatch(host, documentSelfLink)
                .setBody(state)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                        return;
                    }
                    ctx.completeIteration();
                });
        host.send(patch);
        ctx.await();
    }

    private void loadLocalUsers() {
        String localUsers = AuthUtil.getLocalUsersFile(host);
        assertNotNull(localUsers);
        Config config;
        try {
            String content = new String(Files.readAllBytes((new File(localUsers)).toPath()));
            config = Utils.fromJson(content, Config.class);
        } catch (Exception e) {
            fail(String.format("Failed to load users configuration file '%s'!. Error: %s",
                    localUsers, Utils.toString(e)));
            return;

        }

        if (config.users == null || config.users.isEmpty()) {
            fail("No users found in the configuration file!");
            return;
        }

        loadedUsers = config.users.stream()
                .map((u) -> u.email)
                .collect(Collectors.toList());

        loadedGroups = config.groups.stream()
                .map(u -> u.name)
                .collect(Collectors.toList());
    }

    private void waitForDefaultRoles() throws Throwable {
        waitForServiceAvailability(CLOUD_ADMINS_RESOURCE_GROUP_LINK,
                CLOUD_ADMINS_USER_GROUP_LINK,
                DEFAULT_CLOUD_ADMINS_ROLE_LINK,
                DEFAULT_BASIC_USERS_ROLE_LINK,
                BASIC_USERS_USER_GROUP_LINK,
                BASIC_USERS_RESOURCE_GROUP_LINK);
    }

    private void waitForDefaultUsersAndGroups() throws Throwable {
        loadLocalUsers();
        waitFor(() -> {
            List<String> stateLinks = getDocumentLinksOfType(LocalPrincipalState.class);
            int expectedSize = loadedUsers.size() + loadedGroups.size();
            if (stateLinks == null || stateLinks.isEmpty()
                    || stateLinks.size() != expectedSize) {
                return false;
            }
            return true;
        });
    }

    protected List<UserState> getUsersFromUserGroup(String userGroupLink) throws Throwable {
        UserGroupState state = getDocument(UserGroupState.class, userGroupLink);
        assertNotNull(state);
        assertNotNull(state.query);

        DeferredResult<List<UserState>> result = retrieveUserStatesForGroup(host, state);

        List<UserState> resultList = new ArrayList<>();

        TestContext ctx = testCreate(1);

        result.whenComplete((list, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            resultList.addAll(list);
            ctx.completeIteration();
        });
        ctx.await();
        return resultList;
    }

}
