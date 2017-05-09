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

package com.vmware.admiral.auth.project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

/**
 * Handles assignment/unassignment of multiple users with multiple roles to/from a project. Used for
 * PATCH and PUT requests for the {@link ProjectService}.
 */
public class ProjectRolesHandler {

    private static final String BODY_IS_REQUIRED_MESSAGE = "Body is required.";
    private static final String BODY_IS_REQURED_MESSAGE_CODE = "auth.body.required";


    /**
     * DTO for custom PATCH and PUT requests to {@link ProjectService} instances. Used for
     * assignment/unassigment of users with various role.
     */
    public static class ProjectRoles extends ServiceDocument {

        /** Assignment/unassignment of project administrators. */
        @Documentation(description = "Assignment/unassignment of project administrators.")
        @PropertyOptions(usage = PropertyUsageOption.OPTIONAL)
        public RolesAssignment administrators;

        /** Assignment/unassignment of project members. */
        @Documentation(description = "Assignment/unassignment of project members.")
        @PropertyOptions(usage = PropertyUsageOption.OPTIONAL)
        public RolesAssignment members;

        public static class RolesAssignment {

            /** List of principal IDs to assign to this project with a given role. */
            @Documentation(description = "List of principal IDs to "
                    + "assign to this project with a given role.")
            @PropertyOptions(usage = PropertyUsageOption.OPTIONAL)
            public List<String> add;

            /** List of principal IDs with a given role to unassign from this project. */
            @Documentation(description = "List of principal IDs with "
                    + "a given role to unassign from this project.")
            @PropertyOptions(usage = PropertyUsageOption.OPTIONAL)
            public List<String> remove;
        }
    }

    private ServiceHost serviceHost;
    private String projectLink;

    public ProjectRolesHandler(ServiceHost serviceHost, String projectLink) {
        AssertUtil.assertNotNull(serviceHost, "serviceHost");
        AssertUtil.assertNotEmpty(projectLink, "projectLink");
        this.serviceHost = serviceHost;
        this.projectLink = projectLink;
    }

    public DeferredResult<Void> handleRolesUpdate(ProjectRoles patchBody) {
        if (patchBody == null) {
            return DeferredResult.failed(new LocalizableValidationException(
                    BODY_IS_REQUIRED_MESSAGE, BODY_IS_REQURED_MESSAGE_CODE));
        }
        return getProjectState()
                .thenCompose((projectState) -> handleRolesAssignment(projectState, patchBody));
    }

    private DeferredResult<ProjectState> getProjectState() {
        return getHost().sendWithDeferredResult(
                Operation.createGet(getHost(), getProjectLink()).setReferer(getProjectLink()),
                ProjectState.class);
    }

    private DeferredResult<Void> handleRolesAssignment(ProjectState projectState, ProjectRoles patchBody) {
        return DeferredResult.allOf(
                applyBatchUserOperationOnGroup(patchBody.administrators,
                        projectState.administratorsUserGroupLink, null, null, null, null),
                applyBatchUserOperationOnGroup(patchBody.members, projectState.membersUserGroupLink,
                        null, null, null, null));
    }

    private DeferredResult<Void> applyBatchUserOperationOnGroup(
            ProjectRoles.RolesAssignment roleAssignment, String groupLink,
            Map<String, UserState> cachedUsers, List<UserState> groupMembers,
            List<UserState> removedMembers, List<UserState> newMembers) {

        try {
            AssertUtil.assertNotEmpty(groupLink, "groupLink");
        } catch (LocalizableValidationException ex) {
            return DeferredResult.failed(ex);
        }

        if (roleAssignment == null
                || ((roleAssignment.add == null || roleAssignment.add.isEmpty())
                        && (roleAssignment.remove == null || roleAssignment.remove.isEmpty()))) {
            // no operation to apply
            return DeferredResult.completed(null);
        }

        // Start building cache of loaded user states
        if (cachedUsers == null) {
            // recursion is used because cachedUsers needs to be effectively final
            return applyBatchUserOperationOnGroup(roleAssignment, groupLink, new HashMap<>(),
                    groupMembers, removedMembers, newMembers);
        }

        // retrieve current group members
        if (groupMembers == null) {
            return retrieveUserGroupMembers(groupLink)
                    .thenApply((retrievedUsers) -> {
                        // extend the cache with the retrieved users
                        retrievedUsers.forEach(user -> cachedUsers.put(user.email, user));
                        return retrievedUsers;
                    })
                    .thenCompose((retrievedUsers) -> applyBatchUserOperationOnGroup(roleAssignment,
                            groupLink, cachedUsers, retrievedUsers, removedMembers, newMembers));
        }

        // retrieve users that are to be removed from the group
        if (removedMembers == null) {
            return retrieveUserStatesByPrincipalId(roleAssignment.remove, cachedUsers)
                    .thenApply((retrievedUsers) -> {
                        // extend the cache with the retrieved users
                        retrievedUsers.forEach(user -> cachedUsers.put(user.email, user));
                        return retrievedUsers;
                    })
                    .thenCompose((retrievedUsers) -> applyBatchUserOperationOnGroup(roleAssignment,
                            groupLink, cachedUsers, groupMembers, retrievedUsers, newMembers));
        }

        // retrieve users that are to be added to the group
        if (newMembers == null) {
            return retrieveUserStatesByPrincipalId(roleAssignment.add, cachedUsers)
                    .thenApply((retrievedUsers) -> {
                        // extend the cache with the retrieved users
                        retrievedUsers.forEach(user -> cachedUsers.put(user.email, user));
                        return retrievedUsers;
                    })
                    .thenCompose((retrievedUsers) -> applyBatchUserOperationOnGroup(roleAssignment,
                            groupLink, cachedUsers, groupMembers, removedMembers, retrievedUsers));
        }

        // Prepare group updates
        groupMembers.removeAll(removedMembers);
        groupMembers.addAll(newMembers);

        // Do update the members of the group
        return updateUserGroupMembers(groupLink, groupMembers);
    }

    private DeferredResult<Void> updateUserGroupMembers(String groupLink,
            List<UserState> groupMembers) {

        List<String> membersLinks = groupMembers.stream()
                .map(userState -> userState.documentSelfLink)
                .collect(Collectors.toList());

        return getHost().sendWithDeferredResult(
                Operation.createGet(getHost(), groupLink)
                        .setReferer(getProjectLink()),
                UserGroupState.class)
                .thenCompose((groupState) -> {
                    groupState.query = AuthUtils.buildUsersQuery(membersLinks);
                    return getHost().sendWithDeferredResult(
                            Operation.createPut(getHost(), groupLink)
                                    .setReferer(getProjectLink())
                                    .setBody(groupState),
                            UserGroupState.class);
                }).thenCompose((ignore) -> DeferredResult.completed(null));

    }

    /**
     * Retrieves the list of members for the specified by document link user group.
     *
     * @see #retrieveUserStatesForGroup(UserGroupState)
     */
    private DeferredResult<List<UserState>> retrieveUserGroupMembers(String groupLink) {
        if (groupLink == null) {
            return DeferredResult.completed(new ArrayList<>(0));
        }

        Operation groupGet = Operation.createGet(getHost(), groupLink)
                .setReferer(getProjectLink())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failed to retrieve UserGroupState %s: %s", groupLink,
                                Utils.toString(e));
                    }
                });

        return getHost().sendWithDeferredResult(groupGet, UserGroupState.class)
                .thenCompose(this::retrieveUserStatesForGroup);
    }

    /**
     * Retrieves the list of members for the specified user group.
     */
    private DeferredResult<List<UserState>> retrieveUserStatesForGroup(UserGroupState groupState) {
        DeferredResult<List<UserState>> deferredResult = new DeferredResult<>();
        ArrayList<UserState> resultList = new ArrayList<>();

        QueryTask queryTask = QueryUtil.buildQuery(UserState.class, true, groupState.query);
        QueryUtil.addExpandOption(queryTask);
        new ServiceDocumentQuery<UserState>(getHost(), UserState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        logWarning("Failed to retrieve members of UserGroupState %s: %s",
                                groupState.documentSelfLink, Utils.toString(r.getException()));
                        deferredResult.fail(r.getException());
                    } else if (r.hasResult()) {
                        resultList.add(r.getResult());
                    } else {
                        deferredResult.complete(resultList);
                    }
                });

        return deferredResult;
    }

    private DeferredResult<List<UserState>> retrieveUserStatesByPrincipalId(
            List<String> principalIds, Map<String, UserState> cachedStates) {

        if (principalIds == null || principalIds.isEmpty()) {
            return DeferredResult.completed(new ArrayList<>(0));
        }

        if (cachedStates == null || cachedStates.isEmpty()) {
            return retrieveUserStatesByPrincipalIds(principalIds);
        }

        ArrayList<String> remainingPrincipalIds = new ArrayList<>(principalIds.size());
        ArrayList<UserState> resultStates = new ArrayList<>(principalIds.size());

        // retrieve users from cache and build the list of non-cached users
        principalIds.forEach((principalId) -> {
            UserState cachedState = cachedStates.get(principalId);
            if (cachedState != null) {
                resultStates.add(cachedState);
            } else {
                remainingPrincipalIds.add(principalId);
            }
        });

        if (remainingPrincipalIds.isEmpty()) {
            // all users have been retrieved from cache.
            return DeferredResult.completed(resultStates);
        } else {
            // retrieve users that were not present in the cache
            return retrieveUserStatesByPrincipalIds(remainingPrincipalIds)
                    .thenApply((retrievedUsers) -> {
                        resultStates.addAll(retrievedUsers);
                        return resultStates;
                    });
        }

    }

    private DeferredResult<List<UserState>> retrieveUserStatesByPrincipalIds(
            List<String> principalIds) {

        if (principalIds == null || principalIds.isEmpty()) {
            return DeferredResult.completed(new ArrayList<>(0));
        }

        List<String> documentLinks = principalIds.stream()
                .map(AuthUtils::getUserStateDocumentLink)
                .collect(Collectors.toList());
        return retrieveUserStatesByDocumentLinks(documentLinks);
    }

    private DeferredResult<List<UserState>> retrieveUserStatesByDocumentLinks(
            List<String> documentLinks) {
        List<DeferredResult<UserState>> deferredStates = documentLinks.stream()
                .map((documentLink) -> Operation.createGet(getHost(), documentLink)
                        .setReferer(getProjectLink()))
                .map((getOp) -> getHost().sendWithDeferredResult(getOp, UserState.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(deferredStates);
    }

    private ServiceHost getHost() {
        return serviceHost;
    }

    private String getProjectLink() {
        return projectLink;
    }

    private void logWarning(String format, Object... args) {
        getHost().log(Level.WARNING, format, args);
    }

}
