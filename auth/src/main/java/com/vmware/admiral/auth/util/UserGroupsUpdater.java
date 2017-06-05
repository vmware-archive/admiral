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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

public class UserGroupsUpdater {

    private ServiceHost host;

    private String groupLink;

    private String referrer;

    private List<String> usersToAdd;

    private List<String> usersToRemove;

    private Map<String, UserState> cachedUsers;

    private UserGroupsUpdater(ServiceHost serviceHost, String groupLink, String referrer,
            List<String> usersToAdd, List<String> usersToRemove) {
        AssertUtil.assertNotNull(serviceHost, "serviceHost");
        this.setHost(serviceHost);
        this.setGroupLink(groupLink);
        this.setReferrer(referrer);
        this.setUsersToAdd(usersToAdd);
        this.setUsersToRemove(usersToRemove);
        this.cachedUsers = new HashMap<>();
    }

    public void setHost(ServiceHost host) {
        this.host = host;
    }

    public void setGroupLink(String groupLink) {
        this.groupLink = groupLink;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public void setUsersToAdd(List<String> usersToAdd) {
        if (usersToAdd == null) {
            this.usersToAdd = new ArrayList<>();
            return;
        }
        this.usersToAdd = usersToAdd;
    }

    public void setUsersToRemove(List<String> usersToRemove) {
        if (usersToRemove == null) {
            this.usersToRemove = new ArrayList<>();
            return;
        }
        this.usersToRemove = usersToRemove;
    }

    public static UserGroupsUpdater create() {
        return new UserGroupsUpdater(null, null, null, null, null);
    }

    public static UserGroupsUpdater create(ServiceHost host, String groupLink, String referrer,
            List<String> usersToAdd, List<String> usersToRemove) {
        return new UserGroupsUpdater(host, groupLink, referrer, usersToAdd, usersToRemove);
    }

    public DeferredResult<Void> update() {
        return applyBatchUserOperationOnGroup(null, null, null);
    }

    private DeferredResult<Void> applyBatchUserOperationOnGroup(List<UserState> groupMembers,
            List<UserState> removedMembers, List<UserState> newMembers) {

        try {
            AssertUtil.assertNotEmpty(groupLink, "groupLink");
        } catch (LocalizableValidationException ex) {
            return DeferredResult.failed(ex);
        }

        if ((usersToAdd == null || usersToAdd.isEmpty())
                && (usersToRemove == null || usersToRemove.isEmpty())) {
            // no operation to apply
            return DeferredResult.completed(null);
        }

        // retrieve current group members
        if (groupMembers == null) {
            return retrieveUserGroupMembers(host, referrer, groupLink)
                    .thenApply((retrievedUsers) -> {
                        // extend the cache with the retrieved users
                        retrievedUsers.forEach(user -> cachedUsers.put(user.email, user));
                        return retrievedUsers;
                    })
                    .thenCompose((retrievedUsers) -> applyBatchUserOperationOnGroup(retrievedUsers,
                            removedMembers, newMembers));
        }

        // retrieve users that are to be removed from the group
        if (removedMembers == null) {
            return retrieveUserStatesByPrincipalId(usersToRemove)
                    .thenApply((retrievedUsers) -> {
                        // extend the cache with the retrieved users
                        retrievedUsers.forEach(user -> cachedUsers.put(user.email, user));
                        return retrievedUsers;
                    })
                    .thenCompose((retrievedUsers) -> applyBatchUserOperationOnGroup(groupMembers,
                            retrievedUsers, newMembers));
        }

        // retrieve users that are to be added to the group
        if (newMembers == null) {
            return retrieveUserStatesByPrincipalId(usersToAdd)
                    .thenApply((retrievedUsers) -> {
                        // extend the cache with the retrieved users
                        retrievedUsers.forEach(user -> cachedUsers.put(user.email, user));
                        return retrievedUsers;
                    })
                    .thenCompose((retrievedUsers) -> applyBatchUserOperationOnGroup(groupMembers,
                            removedMembers, retrievedUsers));
        }

        // Prepare group updates
        groupMembers.removeAll(removedMembers);
        groupMembers.addAll(newMembers);

        // Do update the members of the group
        return updateUserGroupMembers(groupMembers);
    }

    private DeferredResult<Void> updateUserGroupMembers(List<UserState> groupMembers) {

        List<String> membersLinks = groupMembers.stream()
                .map(userState -> userState.documentSelfLink)
                .collect(Collectors.toList());

        Operation getUserGroup = Operation.createGet(host, groupLink)
                .setReferer(referrer == null ? host.getUri().toString() : referrer);

        return host.sendWithDeferredResult(getUserGroup, UserGroupState.class)
                .thenCompose(groupState -> {
                    groupState.query = AuthUtils.buildUsersQuery(membersLinks);
                    return host.sendWithDeferredResult(
                            Operation.createPut(host, groupLink)
                                    .setReferer(
                                            referrer == null ? host.getUri().toString() : referrer)
                                    .setBody(groupState),
                            UserGroupState.class);
                }).thenCompose((ignore) -> DeferredResult.completed(null));
    }

    /**
     * Retrieves the list of members for the specified by document link user group.
     *
     * @see #retrieveUserStatesForGroup(ServiceHost, UserGroupState)
     */
    private static DeferredResult<List<UserState>> retrieveUserGroupMembers(ServiceHost host,
            String referrer, String groupLink) {
        if (groupLink == null) {
            return DeferredResult.completed(new ArrayList<>(0));
        }

        Operation groupGet = Operation.createGet(host, groupLink)
                .setReferer(referrer == null ? host.getUri().toString() : referrer)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.WARNING, "Failed to retrieve UserGroupState %s: %s",
                                groupLink, Utils.toString(e));
                    }
                });

        return host.sendWithDeferredResult(groupGet, UserGroupState.class)
                .thenCompose((groupState) -> retrieveUserStatesForGroup(host, groupState));
    }

    /**
     * Retrieves the list of members for the specified user group.
     */
    private static DeferredResult<List<UserState>> retrieveUserStatesForGroup(ServiceHost host,
            UserGroupState groupState) {

        DeferredResult<List<UserState>> deferredResult = new DeferredResult<>();
        ArrayList<UserState> resultList = new ArrayList<>();

        QueryTask queryTask = QueryUtil.buildQuery(UserState.class, true, groupState.query);
        QueryUtil.addExpandOption(queryTask);
        new ServiceDocumentQuery<UserState>(host, UserState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        host.log(Level.WARNING,
                                "Failed to retrieve members of UserGroupState %s: %s",
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
            List<String> principalIds) {

        if (principalIds == null || principalIds.isEmpty()) {
            return DeferredResult.completed(new ArrayList<>(0));
        }

        if (cachedUsers == null || cachedUsers.isEmpty()) {
            return retrieveUserStatesByPrincipalIds(principalIds);
        }

        ArrayList<String> remainingPrincipalIds = new ArrayList<>(principalIds.size());
        ArrayList<UserState> resultStates = new ArrayList<>(principalIds.size());

        // retrieve users from cache and build the list of non-cached users
        principalIds.forEach((principalId) -> {
            UserState cachedState = cachedUsers.get(principalId);
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
                .map((documentLink) -> Operation.createGet(host, documentLink)
                        .setReferer(referrer == null ? host.getUri().toString() : referrer))
                .map((getOp) -> host.sendWithDeferredResult(getOp, UserState.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(deferredStates);
    }
}
