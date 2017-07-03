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

import static com.vmware.admiral.auth.util.PrincipalRolesUtil.getAllRolesForPrincipal;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;

import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.PrincipalRoles;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.idm.SecurityContext;
import com.vmware.admiral.auth.idm.SecurityContext.ProjectEntry;
import com.vmware.admiral.auth.idm.SessionService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.UserService.UserState;

public class SecurityContextUtil {

    protected static class SecurityContextBuilderCache {
        public Principal principal;
        public UserState userState;
        public SecurityContext context = new SecurityContext();
    }

    /**
     * Gets the {@link SecurityContext} for the currently authenticated user
     */
    public static DeferredResult<SecurityContext> getSecurityContext(Service requestorService,
            AuthorizationContext authContext) {
        return getSecurityContext(requestorService, AuthUtil.getAuthorizedUserId(authContext));
    }

    /**
     * Gets the {@link SecurityContext} for the denoted user
     */
    public static DeferredResult<SecurityContext> getSecurityContext(Service requestorService,
            String userId) {
        return DeferredResult.completed(new SecurityContextBuilderCache())
                .thenCompose(cache -> getPrincipal(requestorService, userId, cache))
                .thenCompose(principal -> getAllRolesForPrincipal(
                        requestorService.getHost(), principal))
                .thenApply(SecurityContextUtil::fromPrincipalRolesToSecurityContext);
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
        authorizeOperationIfSessionService(requestorService, getOp);

        return requestorService.getHost()
                .sendWithDeferredResult(getOp, Principal.class)
                .thenApply((principal) -> {
                    cache.principal = principal;
                    return principal;
                });
    }

    public static List<SecurityContext.ProjectEntry> buildProjectEntries(
            Collection<ProjectState> projects, Collection<String> userGroupLinks) {
        return projects.stream()
                .map((project) -> {
                    ProjectEntry projectEntry = new SecurityContext.ProjectEntry();
                    projectEntry.documentSelfLink = project.documentSelfLink;
                    projectEntry.name = project.name;
                    projectEntry.roles = new HashSet<>();
                    projectEntry.customProperties = project.customProperties;

                    if (CollectionUtils.containsAny(
                            project.administratorsUserGroupLinks, userGroupLinks)) {
                        projectEntry.roles.add(AuthRole.PROJECT_ADMIN);
                    }
                    if (CollectionUtils.containsAny(
                            project.membersUserGroupLinks, userGroupLinks)) {
                        projectEntry.roles.add(AuthRole.PROJECT_MEMBER);
                    }
                    if (CollectionUtils.containsAny(
                            project.viewersUserGroupLinks, userGroupLinks)) {
                        projectEntry.roles.add(AuthRole.PROJECT_VIEWER);
                    }

                    return projectEntry;
                }).collect(Collectors.toList());
    }

    public static SecurityContext fromPrincipalRolesToSecurityContext(PrincipalRoles roles) {
        SecurityContext context = new SecurityContext();
        context.email = roles.email;
        context.id = roles.id;
        context.name = roles.name;
        context.projects = roles.projects;
        context.roles = roles.roles;
        return context;
    }

    /**
     * We want to authorize the operation with system context only when the requestorService is
     * SessionService, because SessionService is privileged, so even the basic users should be able
     * to get the SecurityContext for themselves.
     * <p>
     * In other cases where the caller is not authorized to required services to collect the
     * SecurityContext (e.g. UserService, PrincipalService), we should not authorize the
     * operation with system context.
     */
    private static void authorizeOperationIfSessionService(Service requestorService, Operation op) {
        if (requestorService instanceof SessionService) {
            requestorService.setAuthorizationContext(op,
                    requestorService.getSystemAuthorizationContext());
        }
    }
}
