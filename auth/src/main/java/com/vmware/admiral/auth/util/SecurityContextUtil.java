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
import com.vmware.admiral.auth.idm.PrincipalRoles;
import com.vmware.admiral.auth.idm.SecurityContext;
import com.vmware.admiral.auth.idm.SecurityContext.ProjectEntry;
import com.vmware.admiral.auth.idm.SessionService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

public class SecurityContextUtil {

    /**
     * Gets the {@link SecurityContext} for the currently authenticated user
     */
    public static DeferredResult<SecurityContext> getSecurityContext(Service requestorService,
            Operation requestorOperation) {
        return getSecurityContext(requestorService, requestorOperation,
                AuthUtil.getAuthorizedUserId(requestorOperation.getAuthorizationContext()));
    }

    /**
     * Gets the {@link SecurityContext} for the denoted user
     */
    public static DeferredResult<SecurityContext> getSecurityContext(Service requestorService,
            Operation requestorOperation, String userId) {


        return PrincipalUtil.getPrincipal(requestorService, requestorOperation, userId)
                .thenCompose(principal -> PrincipalRolesUtil.getAllRolesForPrincipal(
                        requestorService, requestorOperation, principal))
                .thenApply(SecurityContextUtil::fromPrincipalRolesToSecurityContext);
    }

    public static DeferredResult<SecurityContext> getSecurityContext(Service requestorService,
            Operation requestorOperation, Principal principal) {

        return PrincipalRolesUtil
                .getAllRolesForPrincipal(requestorService, requestorOperation, principal)
                .thenApply(SecurityContextUtil::fromPrincipalRolesToSecurityContext);
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
                        projectEntry.roles.add(AuthRole.PROJECT_MEMBER_EXTENDED);
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

    public static DeferredResult<SecurityContext> getSecurityContextForCurrentUser(Service service) {
        Operation getSecurityContext = Operation.createGet(service, SessionService.SELF_LINK);
        return service.sendWithDeferredResult(getSecurityContext, SecurityContext.class);
    }

}
