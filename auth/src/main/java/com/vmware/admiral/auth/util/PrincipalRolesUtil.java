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

import static com.vmware.admiral.auth.util.SecurityContextUtil.buildProjectEntries;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.SecurityContext.ProjectEntry;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.UserService.UserState;

public class PrincipalRolesUtil {

    public static DeferredResult<Set<AuthRole>> getDirectlyAssignedSystemRoles(ServiceHost host,
            String principalId) {

        return PrincipalUtil.getPrincipal(host, principalId)
                .thenCompose(ignore -> getUserState(host, principalId))
                .thenApply(userState -> {
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
            ServiceHost host, String principalId) {

        return PrincipalUtil.getPrincipal(host, principalId)
                .thenCompose(ignore -> getUserState(host, principalId))
                .thenCompose(userState -> {
                    Query query = ProjectUtil
                            .buildQueryProjectsFromGroups(userState.userGroupLinks);

                    return new QueryByPages<>(host, query, ProjectState.class,
                            null)
                            .collectDocuments(Collectors.toList())
                            .thenApply((projects) -> buildProjectEntries(
                                    projects, userState.userGroupLinks));
                });
    }

    private static DeferredResult<UserState> getUserState(ServiceHost host, String principalId) {
        Operation getUserStateOp = Operation.createGet(host, UriUtils.buildUriPath(
                UserService.FACTORY_LINK, principalId))
                .setReferer(host.getUri());
        return host.sendWithDeferredResult(getUserStateOp, UserState.class);
    }
}
