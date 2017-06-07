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

package com.vmware.admiral.auth.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.content.AuthContentService;
import com.vmware.admiral.auth.idm.content.AuthContentService.AuthContentBody;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;

public class AuthContentServiceTest extends AuthBaseTest {
    private String projectOnlyContent;
    private String authContent;

    @Before
    public void setup() throws GeneralSecurityException, IOException {
        host.assumeIdentity(buildUserServicePath(USERNAME_ADMIN));
        projectOnlyContent = new BufferedReader(new InputStreamReader(getClass().getClassLoader()
                .getResourceAsStream("content-projects-only.json")))
                .lines().collect(Collectors.joining("\n"));

        authContent = new BufferedReader(new InputStreamReader(getClass().getClassLoader()
                .getResourceAsStream("auth-content.json")))
                .lines().collect(Collectors.joining("\n"));

    }

    @Test
    public void testImportContentWithProjectsOnly() throws Throwable {
        AuthContentBody body = Utils.fromJson(projectOnlyContent, AuthContentBody.class);
        TestContext ctx = testCreate(1);
        host.send(Operation.createPost(host, AuthContentService.SELF_LINK)
                .setBody(projectOnlyContent)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                    } else {
                        ctx.completeIteration();
                    }
                }));
        ctx.await();

        List<String> projectLinks = getDocumentLinksOfType(ProjectState.class);
        projectLinks.remove("/projects/default-project");

        assertEquals(body.projects.size(), projectLinks.size());

        List<String> projectToImportNames = body.projects.stream()
                .map(p -> p.name)
                .collect(Collectors.toList());

        for (String link : projectLinks) {
            ProjectState state = getDocument(ProjectState.class, link);
            assertTrue(projectToImportNames.contains(state.name));
        }
    }

    @Test
    public void testImportContentWithProjectAndUsers() throws Throwable {
        AuthContentBody body = Utils.fromJson(authContent, AuthContentBody.class);
        TestContext ctx = testCreate(1);
        host.send(Operation.createPost(host, AuthContentService.SELF_LINK)
                .setBody(authContent)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                    } else {
                        ctx.completeIteration();
                    }
                }));
        ctx.await();

        List<String> projectLinks = getDocumentLinksOfType(ProjectState.class);
        projectLinks.remove("/projects/default-project");

        assertEquals(body.projects.size(), projectLinks.size());

        List<String> projectToImportNames = body.projects.stream()
                .map(p -> p.name)
                .collect(Collectors.toList());

        for (String link : projectLinks) {
            ProjectState state = getDocument(ProjectState.class, link);
            assertTrue(projectToImportNames.contains(state.name));
        }

        //TODO: Assert users are correctly distributed when roles are implemented.
    }
}
