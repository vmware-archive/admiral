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

package com.vmware.admiral.auth.idm.content;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.idm.PrincipalRoles;
import com.vmware.admiral.auth.idm.PrincipalRoles.PrincipalRoleAssignment;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles.RolesAssignment;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class AuthContentService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.AUTH_CONTENT;

    public static class AuthContentBody {

        public Map<String, PrincipalRoleAssignment> roles;

        public List<ProjectContentBody> projects;
    }

    public static class ProjectContentBody {

        public String name;

        public String description;

        public Boolean isPublic;

        public List<String> administrators;

        public List<String> members;
    }

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            post.fail(new IllegalArgumentException("body is required"));
            return;
        }

        AuthContentBody body = post.getBody(AuthContentBody.class);
        if (body == null) {
            post.fail(new IllegalArgumentException("body not supported"));
            return;
        }

        if (body.roles != null && !body.roles.isEmpty()) {
            handleRoles(body, post);
            return;
        }

        handleProjects(body, post);

    }

    private void handleRoles(AuthContentBody body, Operation post) {

        AtomicInteger counter = new AtomicInteger(body.roles.size());

        for (Entry<String, PrincipalRoleAssignment> roleWithUsers : body.roles.entrySet()) {
            verifyRole(roleWithUsers, () -> {
                if (counter.decrementAndGet() == 0) {
                    patchRoles(body, post);
                }
            });
        }

    }

    private void verifyRole(Entry<String, PrincipalRoleAssignment> roleWithUsers,
            Runnable callback) {

        verifyUsers(roleWithUsers.getValue().add, roleWithUsers.getValue().remove,
                (verifiedUsers) -> {
                    List<String> verifiedUsersToAdd = roleWithUsers.getValue().add.stream()
                            .filter(u -> verifiedUsers.contains(u))
                            .collect(Collectors.toList());

                    List<String> verifiedUsersToRemove = roleWithUsers.getValue().remove.stream()
                            .filter(u -> verifiedUsers.contains(u))
                            .collect(Collectors.toList());

                    roleWithUsers.getValue().add = verifiedUsersToAdd;
                    roleWithUsers.getValue().remove = verifiedUsersToRemove;

                    callback.run();
                });
    }

    private void verifyUsers(List<String> usersToAdd, List<String> usersToRemove,
            Consumer<Set<String>> verifiedUsersCallback) {

        Set<String> verifiedUsers = new HashSet<>();
        Set<String> usersToVerify = new HashSet<>();
        usersToVerify.addAll(usersToAdd);
        usersToVerify.addAll(usersToRemove);

        AtomicInteger counter = new AtomicInteger(usersToVerify.size());
        for (String user : usersToVerify) {
            verifyUser(user, (ex) -> {
                if (ex == null) {
                    verifiedUsers.add(user);
                } else {
                    logWarning("Exception when verifying user %s: %s",
                            user, Utils.toString(ex));
                }
                if (counter.decrementAndGet() == 0) {
                    verifiedUsersCallback.accept(verifiedUsers);
                }
            });
        }

    }

    private void verifyUser(String user, Consumer<Throwable> error) {
        URI uri = UriUtils.buildUri(getHost(), PrincipalService.SELF_LINK);
        uri = UriUtils.extendUri(uri, user);
        sendRequest(Operation.createGet(uri)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        error.accept(ex);
                    } else {
                        error.accept(null);
                    }
                }));
    }

    private void patchRoles(AuthContentBody body, Operation post) {
        PrincipalRoles roles = new PrincipalRoles();
        roles.roles = body.roles;
        sendRequest(Operation.createPatch(this, PrincipalService.SELF_LINK)
                .setReferer(getHost().getUri())
                .setBody(roles)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to patch roles: %s", Utils.toString(ex));
                    } else {
                        handleProjects(body, post);
                    }
                }));
    }

    private void handleProjects(AuthContentBody body, Operation post) {
        if (body.projects == null || body.projects.isEmpty()) {
            post.complete();
            return;
        }

        AtomicInteger counter = new AtomicInteger(body.projects.size());

        for (ProjectContentBody projectContent : body.projects) {
            createProject(projectContent, () -> {
                if (counter.decrementAndGet() == 0) {
                    post.complete();
                }
            });
        }

    }

    private void createProject(ProjectContentBody projectContent, Runnable callback) {
        ProjectState state = new ProjectState();
        state.name = projectContent.name;
        state.description = projectContent.description;
        state.isPublic = projectContent.isPublic;

        sendRequest(Operation.createPost(this, ProjectFactoryService.SELF_LINK)
                .setBody(state)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to create project: %s", Utils.toString(ex));
                        callback.run();
                    } else {
                        ProjectState created = o.getBody(ProjectState.class);
                        updateProjectWithMembers(created, projectContent, callback);
                    }
                }));
    }

    private void updateProjectWithMembers(ProjectState state, ProjectContentBody projectContent,
            Runnable callback) {
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.administrators = new RolesAssignment();
        projectRoles.administrators.add = new ArrayList<>();
        projectRoles.administrators.add.addAll(projectContent.administrators);
        projectRoles.members = new RolesAssignment();
        projectRoles.members.add = new ArrayList<>();
        projectRoles.members.add.addAll(projectContent.members);

        sendRequest(Operation.createPatch(this, state.documentSelfLink)
                .setReferer(getHost().getUri())
                .setBody(projectRoles)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to patch project roles: %s", Utils.toString(ex));
                    }
                    callback.run();
                }));

    }
}
