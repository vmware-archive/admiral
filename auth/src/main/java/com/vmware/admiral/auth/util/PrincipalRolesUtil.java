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

import static com.vmware.admiral.auth.util.AuthUtil.extractDataFromRoleStateId;
import static com.vmware.admiral.auth.util.SecurityContextUtil.buildProjectEntries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.PrincipalRoles;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.idm.SecurityContext.ProjectEntry;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserService.UserState;

public class PrincipalRolesUtil {

    public static DeferredResult<Set<AuthRole>> getDirectlyAssignedSystemRoles(ServiceHost host,
            Principal principal) {

        return getUserState(host, principal.id).thenApply(userState -> {
            if (userState.userGroupLinks == null || userState.userGroupLinks.isEmpty()) {
                return Collections.emptySet();
            }

            Set<AuthRole> roles = new HashSet<>();
            AuthUtil.MAP_ROLE_TO_SYSTEM_USER_GROUP.entrySet()
                    .forEach((entry) -> {
                        if (userState.userGroupLinks.contains(entry.getValue())) {
                            roles.add(entry.getKey());
                        }
                    });
            return roles;
        });
    }

    public static DeferredResult<List<ProjectEntry>> getDirectlyAssignedProjectRoles(
            ServiceHost host, Principal principal) {

        return getUserState(host, principal.id).thenCompose(userState -> {
            Query query = ProjectUtil
                    .buildQueryProjectsFromGroups(userState.userGroupLinks);

            return new QueryByPages<>(host, query, ProjectState.class,
                    null)
                            .collectDocuments(Collectors.toList())
                            .thenApply((projects) -> buildProjectEntries(
                                    projects, userState.userGroupLinks));
        });
    }

    public static DeferredResult<List<PrincipalRoles>> getAllRolesForPrincipals(ServiceHost host,
            List<Principal> principals) {

        List<DeferredResult<PrincipalRoles>> deferredResults = new ArrayList<>();

        for (Principal principal : principals) {
            deferredResults.add(getAllRolesForPrincipal(host, principal));
        }

        return DeferredResult.allOf(deferredResults);
    }

    public static DeferredResult<PrincipalRoles> getAllRolesForPrincipal(ServiceHost host,
            Principal principal) {

        PrincipalRoles returnRoles = new PrincipalRoles();

        return getGroupsWherePrincipalBelongs(host, principal.id)
                .thenCompose(groups -> getRoleStatesForGroups(host, groups))
                .thenApply(groupsToRoles -> {
                    List<RoleState> roleStates = new ArrayList<>();
                    for (List<RoleState> rs : groupsToRoles.values()) {
                        roleStates.addAll(rs);
                    }
                    return roleStates;
                })
                .thenCompose(roleStates -> extractRolesIntoPrincipalRoles(
                        host, principal, roleStates))
                .thenAccept(principalRoles -> {
                    PrincipalUtil.copyPrincipalData(principalRoles, returnRoles);
                    returnRoles.roles = principalRoles.roles;
                    returnRoles.projects = principalRoles.projects;
                })
                .thenCompose(ignore -> getDirectlyAssignedSystemRoles(host, principal))
                .thenAccept(systemRoles -> returnRoles.roles.addAll(systemRoles))
                .thenCompose(ignore -> getDirectlyAssignedProjectRoles(host, principal))
                .thenApply(projectEntries -> {
                    returnRoles.projects.addAll(projectEntries);
                    returnRoles.projects = mergeProjectEntries(returnRoles.projects);
                    return returnRoles;
                });
    }

    public static DeferredResult<Map<String, List<RoleState>>> getRoleStatesForGroups(
            ServiceHost host, List<String> groups) {

        if (groups == null || groups.isEmpty()) {
            return DeferredResult.completed(new HashMap<>());
        }

        AtomicInteger counter = new AtomicInteger(groups.size());
        AtomicBoolean hasError = new AtomicBoolean(false);

        DeferredResult<Map<String, List<RoleState>>> returnResult = new DeferredResult<>();
        Map<String, List<RoleState>> result = new HashMap<>();

        for (String group : groups) {
            String groupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, group);
            Query query = QueryUtil.addListValueClause(RoleState.FIELD_NAME_USER_GROUP_LINK,
                    Collections.singletonList(groupLink), MatchType.TERM);

            QueryTask queryTask = QueryUtil.buildQuery(RoleState.class, true, query);
            QueryUtil.addExpandOption(queryTask);

            List<RoleState> roles = new ArrayList<>();

            new ServiceDocumentQuery<>(host, RoleState.class).query(queryTask, r -> {
                if (r.hasException()) {
                    if (hasError.compareAndSet(false, true)) {
                        returnResult.fail(r.getException());
                    } else {
                        host.log(Level.WARNING, "Getting roles for group: %s failed with: %s",
                                group, Utils.toString(r.getException()));
                    }
                } else if (r.hasResult()) {
                    roles.add(r.getResult());
                } else {
                    result.put(group, roles);
                    if (counter.decrementAndGet() == 0 && !hasError.get()) {
                        returnResult.complete(result);
                    }
                }
            });
        }

        return returnResult;
    }

    private static DeferredResult<PrincipalRoles> extractRolesIntoPrincipalRoles(ServiceHost host,
            Principal principal, List<RoleState> roleStates) {

        PrincipalRoles result = new PrincipalRoles();
        PrincipalUtil.copyPrincipalData(principal, result);

        if (roleStates == null || roleStates.isEmpty()) {
            result.projects = new ArrayList<>();
            result.roles = new HashSet<>();
            return DeferredResult.completed(result);
        }

        Set<AuthRole> systemRoles = new HashSet<>();
        List<DeferredResult<ProjectEntry>> projectEntries = new ArrayList<>();

        for (RoleState roleState : roleStates) {
            if (isProjectRole(roleState)) {
                projectEntries.add(extractProjectEntryFromRoleState(host, roleState));
            } else {
                systemRoles.add(extractSystemRoleFromRoleState(roleState));
            }
        }

        return DeferredResult.allOf(projectEntries)
                .thenApply(entries -> mergeProjectEntries(entries))
                .thenAccept(entries -> result.projects = entries)
                .thenApply(ignore -> {
                    result.roles = systemRoles;
                    return result;
                });
    }

    private static DeferredResult<ProjectEntry> extractProjectEntryFromRoleState(ServiceHost host,
            RoleState roleState) {

        ProjectEntry entry = new ProjectEntry();

        String roleStateId = Service.getId(roleState.documentSelfLink);
        String[] roleStateIdData = extractDataFromRoleStateId(roleStateId);

        if (roleStateIdData.length != 3) {
            return DeferredResult.failed(new RuntimeException("Cannot extract project entry from "
                    + "role state with invalid id: " + roleState.documentSelfLink));
        }

        String projectId = roleStateIdData[0];
        AuthRole projectRole = AuthRole.fromSuffix(roleStateIdData[2]);

        entry.roles = Collections.singleton(projectRole);

        return getProjectState(host, projectId)
                .thenApply(projectState -> {
                    entry.documentSelfLink = projectState.documentSelfLink;
                    entry.name = projectState.name;
                    return entry;
                });
    }

    private static AuthRole extractSystemRoleFromRoleState(RoleState roleState) {

        if (roleState.documentSelfLink.contains(AuthRole.CLOUD_ADMINS.getSuffix())) {
            return AuthRole.CLOUD_ADMINS;
        } else if (roleState.documentSelfLink.contains(AuthRole.BASIC_USERS.getSuffix())) {
            return AuthRole.BASIC_USERS;
        } else if (roleState.documentSelfLink.contains(AuthRole.BASIC_USERS_EXTENDED.getSuffix())) {
            return AuthRole.BASIC_USERS_EXTENDED;
        }

        throw new RuntimeException("Cannot extract system role from role state with id: " +
                roleState.documentSelfLink);
    }

    private static boolean isProjectRole(RoleState roleState) {
        return roleState.documentSelfLink.contains(AuthRole.PROJECT_ADMINS.getSuffix())
                || roleState.documentSelfLink.contains(AuthRole.PROJECT_MEMBERS.getSuffix());
    }

    private static DeferredResult<ProjectState> getProjectState(ServiceHost host,
            String projectId) {

        String projectSelfLink = UriUtils.buildUriPath(ProjectFactoryService.SELF_LINK, projectId);
        Operation getOp = Operation.createGet(host, projectSelfLink)
                .setReferer(host.getUri());

        return host.sendWithDeferredResult(getOp, ProjectState.class);
    }

    private static List<ProjectEntry> mergeProjectEntries(List<ProjectEntry> projectEntries) {
        Map<String, ProjectEntry> mergedEntries = new HashMap<>();

        for (ProjectEntry projectEntry : projectEntries) {
            if (!mergedEntries.containsKey(projectEntry.documentSelfLink)) {
                mergedEntries.put(projectEntry.documentSelfLink, projectEntry);
                continue;
            }

            ProjectEntry tempEntry = mergedEntries.get(projectEntry.documentSelfLink);
            tempEntry.roles.addAll(projectEntry.roles);
            mergedEntries.put(projectEntry.documentSelfLink, tempEntry);
        }

        return mergedEntries.entrySet().stream().map(Entry::getValue)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static DeferredResult<List<String>> getGroupsWherePrincipalBelongs(ServiceHost host,
            String principalId) {
        String uri = UriUtils.buildUriPath(PrincipalService.SELF_LINK, principalId,
                PrincipalService.GROUPS_SUFFIX);

        Operation getGroupsOp = Operation.createGet(host, uri)
                .setReferer(host.getUri());

        return host.sendWithDeferredResult(getGroupsOp, List.class)
                .thenApply(groupsList -> (ArrayList<String>) groupsList);
    }

    private static DeferredResult<UserState> getUserState(ServiceHost host, String principalId) {
        Operation getUserStateOp = Operation.createGet(host,
                AuthUtil.buildUserServicePathFromPrincipalId(principalId))
                .setReferer(host.getUri());
        return host.sendWithDeferredResult(getUserStateOp, UserState.class);
    }
}
