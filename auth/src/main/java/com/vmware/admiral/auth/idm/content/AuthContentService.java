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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.DeferredResult;
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

        public List<String> viewers;
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

        List<DeferredResult<Void>> result = new ArrayList<>();
        for (Entry<String, PrincipalRoleAssignment> role : body.roles.entrySet()) {
            if (role.getValue().add != null && !role.getValue().add.isEmpty()) {
                for (String principalId : role.getValue().add) {
                    result.add(assignPrincipal(role.getKey(), principalId));
                }
            }

            if (role.getValue().remove != null && !role.getValue().remove.isEmpty()) {
                for (String principalId : role.getValue().remove) {
                    result.add(unassignPrincipal(role.getKey(), principalId));
                }
            }
        }

        DeferredResult.allOf(result)
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        logWarning("Error while assigning/unassigning roles: %s",
                                Utils.toString(ex));
                        post.fail(ex);
                        return;
                    }
                    handleProjects(body, post);
                });
    }

    private DeferredResult<Void> assignPrincipal(String role, String principalId) {

        PrincipalRoleAssignment rolePatch = new PrincipalRoleAssignment();
        rolePatch.add = new ArrayList<>();
        rolePatch.add.add(role);

        return sendWithDeferredResult(Operation.createPatch(this,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, principalId,
                        PrincipalService.ROLES_SUFFIX))
                .setBody(rolePatch))
                        .thenAccept(ignore -> {
                        });
    }

    private DeferredResult<Void> unassignPrincipal(String role, String principalId) {
        PrincipalRoleAssignment rolePatch = new PrincipalRoleAssignment();
        rolePatch.remove = new ArrayList<>();
        rolePatch.remove.add(role);

        return sendWithDeferredResult(Operation.createPatch(this,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, principalId,
                        PrincipalService.ROLES_SUFFIX))
                .setBody(rolePatch))
                        .thenAccept(ignore -> {
                        });
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
        if (projectContent.administrators != null) {
            projectRoles.administrators = new PrincipalRoleAssignment();
            projectRoles.administrators.add = new ArrayList<>();
            projectRoles.administrators.add.addAll(projectContent.administrators);
        }
        if (projectContent.members != null) {
            projectRoles.members = new PrincipalRoleAssignment();
            projectRoles.members.add = new ArrayList<>();
            projectRoles.members.add.addAll(projectContent.members);
        }
        if (projectContent.viewers != null) {
            projectRoles.viewers = new PrincipalRoleAssignment();
            projectRoles.viewers.add = new ArrayList<>();
            projectRoles.viewers.add.addAll(projectContent.viewers);
        }

        if ((projectRoles.administrators == null) && (projectRoles.members == null)
                && (projectRoles.viewers == null)) {
            callback.run();
            return;
        }

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
