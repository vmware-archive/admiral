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
import java.util.UUID;

import org.junit.Before;

import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.host.HostInitAuthServiceConfig;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

public abstract class AuthBaseTest extends BaseTestCase {

    @Before
    public void beforeForAuthBase() throws Throwable {
        startServices(host);

        waitForServiceAvailability(AuthInitialBootService.SELF_LINK);
        waitForInitialBootServiceToBeSelfStopped(AuthInitialBootService.SELF_LINK);
    }

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    private static void startServices(VerificationHost host) throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);

        HostInitCommonServiceConfig.startServices(host);
        HostInitAuthServiceConfig.startServices(host);
    }

    protected ProjectState createProject(String name) throws Throwable {
        return createProject(name, null, false);
    }

    protected ProjectState createProject(String name, String description, boolean isPublic)
            throws Throwable {
        ProjectState projectState = new ProjectState();

        projectState.id = UUID.randomUUID().toString();
        projectState.name = name;
        projectState.description = description;
        projectState.isPublic = isPublic;

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
}