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

import static com.vmware.admiral.auth.util.AuthUtil.addReplicationFactor;
import static com.vmware.admiral.auth.util.PrincipalUtil.buildUserStateSelfLinks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.UserService.UserState;

public class UserGroupsUpdater {

    private Boolean skipPrincipalVerification;

    private ServiceHost host;

    private String groupLink;

    private String referrer;

    private List<String> usersToAdd;

    private List<String> usersToRemove;

    private UserGroupsUpdater(ServiceHost serviceHost, String groupLink, String referrer,
            List<String> usersToAdd, List<String> usersToRemove,
            Boolean skipPrincipalVerification) {
        this.setHost(serviceHost);
        this.setGroupLink(groupLink);
        this.setReferrer(referrer);
        this.setUsersToAdd(usersToAdd);
        this.setUsersToRemove(usersToRemove);
        this.setSkipPrincipalVerification(skipPrincipalVerification);
    }

    public UserGroupsUpdater setHost(ServiceHost host) {
        this.host = host;
        return this;
    }

    public UserGroupsUpdater setGroupLink(String groupLink) {
        this.groupLink = groupLink;
        return this;
    }

    public UserGroupsUpdater setReferrer(String referrer) {
        this.referrer = referrer;
        return this;
    }

    public UserGroupsUpdater setUsersToAdd(List<String> usersToAdd) {
        if (usersToAdd == null) {
            this.usersToAdd = new ArrayList<>();
        } else {
            this.usersToAdd = usersToAdd;
        }
        return this;
    }

    public UserGroupsUpdater setUsersToRemove(List<String> usersToRemove) {
        if (usersToRemove == null) {
            this.usersToRemove = new ArrayList<>();
        } else {
            this.usersToRemove = usersToRemove;
        }
        return this;
    }

    public UserGroupsUpdater setSkipPrincipalVerification(Boolean skipPrincipalVerification) {
        if (skipPrincipalVerification == null) {
            this.skipPrincipalVerification = false;
        } else {
            this.skipPrincipalVerification = skipPrincipalVerification;
        }
        return this;
    }

    public static UserGroupsUpdater create() {
        return new UserGroupsUpdater(null, null, null,
                null, null, null);
    }

    public static UserGroupsUpdater create(ServiceHost host, String groupLink, String referrer,
            List<String> usersToAdd, List<String> usersToRemove,
            Boolean skipPrincipalVerification) {
        return new UserGroupsUpdater(host, groupLink, referrer, usersToAdd,
                usersToRemove, skipPrincipalVerification);
    }

    public DeferredResult<Void> update() {
        return handleUpdate();
    }

    private DeferredResult<Void> handleUpdate() {
        try {
            AssertUtil.assertNotEmpty(groupLink, "groupLink");
            AssertUtil.assertNotNull(host, "serviceHost");
            validateUsersForDuplicates();
        } catch (LocalizableValidationException ex) {
            return DeferredResult.failed(ex);
        }

        List<DeferredResult<UserState>> usersResults = new ArrayList<>();

        if (usersToAdd != null) {
            for (String userToAdd : usersToAdd) {
                usersResults.add(patchUserState(userToAdd, false));
            }
        }

        if (usersToRemove != null) {
            for (String userToRemove : usersToRemove) {
                usersResults.add(patchUserState(userToRemove, true));
            }
        }

        return DeferredResult.allOf(usersResults).thenAccept((ignore) -> {
        });
    }

    // TODO: Create the user if not exist.
    private DeferredResult<UserState> patchUserState(String user,
            boolean isRemove) {

        URI userStateUri = buildUserStateSelfLinks(host, user);

        DeferredResult<UserState> result;

        if (this.skipPrincipalVerification == null || !this.skipPrincipalVerification) {

            String principalUri = UriUtils.buildUriPath(PrincipalService.SELF_LINK, user);
            Operation getPrincipal = Operation.createGet(host, principalUri)
                    .setReferer(referrer == null ? host.getUri().toString() : referrer);

            result = host.sendWithDeferredResult(getPrincipal, Principal.class)
                    .thenCompose(ignore -> {
                        Operation getUserState = Operation
                                .createGet(buildUserStateSelfLinks(host, user))
                                .setReferer(referrer == null ? host.getUri().toString() : referrer);
                        return host.sendWithDeferredResult(getUserState, UserState.class);
                    });
        } else {

            Operation getUserState = Operation
                    .createGet(buildUserStateSelfLinks(host, user))
                    .setReferer(referrer == null ? host.getUri().toString() : referrer);

            result = host.sendWithDeferredResult(getUserState, UserState.class);
        }

        result.thenApply(us -> new Pair<>(us, (Throwable) null))
                .exceptionally(ex -> new Pair<>(null, ex))
                .thenCompose(pair -> {
                    if (pair.right != null) {
                        if (pair.right.getCause() instanceof ServiceNotFoundException) {
                            UserState userState = new UserState();
                            userState.email = user;
                            userState.documentSelfLink = user;
                            Operation createUser = Operation.createPost(host, UserService
                                    .FACTORY_LINK)
                                    .setReferer(
                                            referrer == null ? host.getUri().toString() : referrer)
                                    .setBody(userState);
                            addReplicationFactor(createUser);
                            return host.sendWithDeferredResult(createUser, UserState.class);
                        }
                        return DeferredResult.failed(pair.right);
                    }
                    return DeferredResult.completed(pair.left);
                })
                .thenCompose(us -> {
                    ServiceStateCollectionUpdateRequest patch;
                    if (isRemove) {
                        Map<String, Collection<Object>> patchGroupLinks = new HashMap<>();
                        patchGroupLinks.put(UserState.FIELD_NAME_USER_GROUP_LINKS,
                                Collections.singletonList(groupLink));
                        patch = ServiceStateCollectionUpdateRequest
                                .create(null, patchGroupLinks);
                    } else {
                        Map<String, Collection<Object>> patchGroupLinks = new HashMap<>();
                        patchGroupLinks.put(UserState.FIELD_NAME_USER_GROUP_LINKS,
                                Collections.singletonList(groupLink));
                        patch = ServiceStateCollectionUpdateRequest
                                .create(patchGroupLinks, null);
                    }
                    Operation patchOp = Operation.createPatch(userStateUri)
                            .setBody(patch)
                            .setReferer(
                                    referrer == null ? host.getUri().toString() : referrer);

                    return host.sendWithDeferredResult(patchOp, UserState.class);
                });

        return result;
    }

    private void validateUsersForDuplicates() {
        if (!Collections.disjoint(usersToAdd, usersToRemove)) {
            throw new IllegalStateException("Unable to assign and unassign role for same user.");
        }
    }
}
