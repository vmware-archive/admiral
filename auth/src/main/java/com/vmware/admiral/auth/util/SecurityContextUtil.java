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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;

import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.idm.SecurityContext;
import com.vmware.admiral.auth.idm.SecurityContext.ProjectEntry;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.UserService.UserState;

public class SecurityContextUtil {

    protected static class SecurityContextBuilderCache {
        public Principal principal;
        public UserState userState;
        public SecurityContext context = new SecurityContext();
    }

    /** Gets the {@link SecurityContext} for the currently authenticated user */
    public static DeferredResult<SecurityContext> getSecurityContext(Service requestorService,
            AuthorizationContext authContext) {
        return getSecurityContext(requestorService, AuthUtil.getAuthorizedUserId(authContext));
    }

    /** Gets the {@link SecurityContext} for the denoted user */
    public static DeferredResult<SecurityContext> getSecurityContext(Service requestorService,
            String userId) {
        return DeferredResult.completed(new SecurityContextBuilderCache())
                .thenCompose(
                        (cache) -> buildBasicUserInfo(requestorService, userId, cache))
                .thenCompose(
                        (cache) -> buildDirectSystemRoles(requestorService, userId, cache))
                .thenApply((cache) -> cache.context);
    }

    /**
     * Retrieve basic principal info (id, name, email) and set them to the {@link SecurityContext}
     * instance in the provided {@link SecurityContextBuilderCache}. If the provided
     * {@link SecurityContextBuilderCache} is <code>null</code>, a new instance will be created.
     */
    protected static DeferredResult<SecurityContextBuilderCache> buildBasicUserInfo(
            Service requestorService, String userId, SecurityContextBuilderCache cache) {
        if (cache == null) {
            return buildBasicUserInfo(requestorService, userId, new SecurityContextBuilderCache());
        }
        SecurityContext context = cache.context;

        return getPrincipal(requestorService, userId, cache)
                .thenApply((principal) -> {
                    context.id = principal.id;
                    context.email = principal.email;
                    context.name = principal.name;
                    return cache;
                });
    }

    /**
     * Retrieve the directly assigned roles to the specified user and store them in the
     * {@link SecurityContext} instance in the provided {@link SecurityContextBuilderCache}. If the
     * provided {@link SecurityContextBuilderCache} is <code>null</code>, a new instance will be
     * created.
     */
    protected static DeferredResult<SecurityContextBuilderCache> buildDirectSystemRoles(
            Service requestorService, String userId, SecurityContextBuilderCache cache) {
        if (cache == null) {
            return buildDirectSystemRoles(requestorService, userId, new SecurityContextBuilderCache());
        }
        SecurityContext context = cache.context;

        return getUserState(requestorService, userId, cache)
                .thenApply((userState) -> {
                    if (context.roles == null) {
                        context.roles = new HashSet<>();
                    }

                    if (userState.userGroupLinks != null && !userState.userGroupLinks.isEmpty()) {
                        AuthUtil.MAP_ROLE_TO_SYSTEM_USER_GROUP.entrySet().stream()
                                .forEach((entry) -> {
                                    if (userState.userGroupLinks.contains(entry.getValue())) {
                                        context.roles.add(entry.getKey());
                                    }
                                });
                    }
                    cache.userState = userState;
                    return cache;
                });
    }

    /**
     * Retrieve the projects that the specified user is directly assigned to and determine the roles
     * of the user for those projects. Store the findings in the {@link SecurityContext} instance in
     * the provided {@link SecurityContextBuilderCache}. If the provided
     * {@link SecurityContextBuilderCache} is <code>null</code>, a new instance will be created.
     */
    protected static DeferredResult<SecurityContextBuilderCache> buildBasicProjectInfo(
            Service requestorService, String userId, SecurityContextBuilderCache cache) {
        if (cache == null) {
            return buildBasicProjectInfo(requestorService, userId,
                    new SecurityContextBuilderCache());
        }

        // first get the user state
        return getUserState(requestorService, userId, cache)
                .thenCompose((userState) -> {
                    if (userState.userGroupLinks == null || userState.userGroupLinks.isEmpty()) {
                        return DeferredResult.completed(cache);
                    }

                    // then select all projects that this user is directly assigned to
                    Query query = ProjectUtil
                            .buildQueryProjectsFromGroups(userState.userGroupLinks);
                    return new QueryByPages<>(requestorService.getHost(), query, ProjectState.class,
                            null)
                                    .collectDocuments(Collectors.toList())
                                    .thenApply((projects) -> {
                                        // finally check what is the user role and store findings
                                        cache.context.projects = buildProjectEntries(projects,
                                                userState.userGroupLinks);
                                        return cache;
                                    });
                });
    }

    private static DeferredResult<Principal> getPrincipal(Service requestorService, String userId,
            SecurityContextBuilderCache cache) {
        // return from cache if already cached
        if (cache.principal != null) {
            return DeferredResult.completed(cache.principal);
        }

        // otherwise get the state and cache it
        Operation getOp = Operation
                .createGet(requestorService,
                        UriUtils.buildUriPath(PrincipalService.SELF_LINK, userId))
                .setReferer(requestorService.getUri());
        authorizeOperation(requestorService, getOp);

        return requestorService.getHost()
                .sendWithDeferredResult(getOp, Principal.class)
                .thenApply((principal) -> {
                    cache.principal = principal;
                    return principal;
                });
    }

    private static DeferredResult<UserState> getUserState(Service requestorService, String userId,
            SecurityContextBuilderCache cache) {
        // return from cache if already cached
        if (cache.userState != null) {
            return DeferredResult.completed(cache.userState);
        }

        // otherwise get the state and cache it
        Operation getOp = Operation
                .createGet(requestorService, AuthUtils.getUserStateDocumentLink(userId))
                .setReferer(requestorService.getUri());
        authorizeOperation(requestorService, getOp);

        return requestorService.getHost()
                .sendWithDeferredResult(getOp, UserState.class)
                .thenApply((userState) -> {
                    cache.userState = userState;
                    return userState;
                });
    }

    private static List<SecurityContext.ProjectEntry> buildProjectEntries(
            Collection<ProjectState> projects, Collection<String> userGroupLinks) {
        return projects.stream()
                .map((project) -> {
                    ProjectEntry projectEntry = new SecurityContext.ProjectEntry();
                    projectEntry.documentSelfLink = project.documentSelfLink;
                    projectEntry.name = project.name;
                    projectEntry.roles = new HashSet<>();

                    if (CollectionUtils.containsAny(
                            project.administratorsUserGroupLinks, userGroupLinks)) {
                        projectEntry.roles.add(AuthRole.PROJECT_ADMINS);
                    }
                    if (CollectionUtils.containsAny(
                            project.membersUserGroupLinks, userGroupLinks)) {
                        projectEntry.roles.add(AuthRole.PROJECT_MEMBERS);
                    }

                    return projectEntry;
                }).collect(Collectors.toList());
    }

    // TODO this will become obsolete when we make the PrincipleService non-privileged and introduce
    // the session service (privileged) which will be setting the system authorization context
    // before issuing the security context for the current user from the PrincipalService
    private static void authorizeOperation(Service requestorService, Operation op) {
        requestorService.setAuthorizationContext(op,
                requestorService.getSystemAuthorizationContext());
    }
}
