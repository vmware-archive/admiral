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

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.junit.Before;

import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.host.HostInitAuthServiceConfig;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.service.common.AuthBootstrapService;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;

import com.vmware.xenon.services.common.UserService;

public abstract class AuthBaseTest extends BaseTestCase {
    public static final int DEFAULT_WAIT_SECONDS_FOR_AUTH_SERVICES = 180;

    protected static final String ADMIN_USERNAME = "administrator@admiral.com";

    private static final String LOCAL_USERS_FILE = "/local-users.json";

    @Before
    public void beforeForAuthBase() throws Throwable {
        host.setSystemAuthorizationContext();

        startServices(host);

        waitForServiceAvailability(AuthInitialBootService.SELF_LINK);
        waitForInitialBootServiceToBeSelfStopped(AuthInitialBootService.SELF_LINK);
        TestContext ctx = new TestContext(1,
                Duration.ofSeconds(DEFAULT_WAIT_SECONDS_FOR_AUTH_SERVICES));
        AuthBootstrapService.waitForInitConfig(host, ((CustomizationVerificationHost) host)
                .localUsers, ctx::completeIteration, ctx::failIteration);
        ctx.await();
        host.resetAuthorizationContext();
    }

    @Override
    protected VerificationHost createHost() throws Throwable {
        String[] customArgs = {
                CommandLineArgumentParser.ARGUMENT_PREFIX
                + AuthBootstrapService.LOCAL_USERS_FILE
                + CommandLineArgumentParser.ARGUMENT_ASSIGNMENT
                + AuthBaseTest.class.getResource(LOCAL_USERS_FILE).toURI().getPath()
        };
        return createHost(customArgs);
    }

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    private static void startServices(VerificationHost host) throws Throwable {
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

    protected ProjectState createProject(String name, String description, boolean isPublic)
            throws Throwable {
        return createProject(name, description, isPublic, null, null);
    }

    protected ProjectState createProject(String name, String description, boolean isPublic,
            String adminsGroupLink, String membersGroupLink) throws Throwable {
        ProjectState projectState = new ProjectState();

        projectState.id = UUID.randomUUID().toString();
        projectState.name = name;
        projectState.description = description;
        projectState.isPublic = isPublic;
        projectState.administratorsUserGroupLink = adminsGroupLink;
        projectState.membersUserGroupLink = membersGroupLink;

        projectState = doPost(projectState, ProjectService.FACTORY_LINK);

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
}
