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
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.UserService.UserState;

public class UserGroupsUpdater {

    private boolean skipPrincipalVerification;
    private Service service;
    private String groupLink;
    private List<String> usersToAdd;
    private List<String> usersToRemove;

    private UserGroupsUpdater(Service service, String groupLink, List<String> usersToAdd,
            List<String> usersToRemove, Boolean skipPrincipalVerification) {
        this.setService(service);
        this.setGroupLink(groupLink);
        this.setUsersToAdd(usersToAdd);
        this.setUsersToRemove(usersToRemove);
        this.setSkipPrincipalVerification(skipPrincipalVerification);
    }

    public UserGroupsUpdater setService(Service service) {
        this.service = service;
        return this;
    }

    public UserGroupsUpdater setGroupLink(String groupLink) {
        this.groupLink = groupLink;
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
        this.skipPrincipalVerification = skipPrincipalVerification;
        return this;
    }

    public static UserGroupsUpdater create() {
        return new UserGroupsUpdater(null, null, null, null, false);
    }

    public static UserGroupsUpdater create(Service service, String groupLink,
            List<String> usersToAdd, List<String> usersToRemove,
            Boolean skipPrincipalVerification) {
        return new UserGroupsUpdater(service, groupLink, usersToAdd,
                usersToRemove, skipPrincipalVerification);
    }

    public DeferredResult<Void> update() {
        return handleUpdate();
    }

    private DeferredResult<Void> handleUpdate() {
        try {
            AssertUtil.assertNotEmpty(groupLink, "groupLink");
            AssertUtil.assertNotNull(service, "service");
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
    private DeferredResult<UserState> patchUserState(String user, boolean isRemove) {
        String userStateLink = AuthUtil.buildUserServicePathFromPrincipalId(user);

        DeferredResult<UserState> result;
        if (!this.skipPrincipalVerification) {
            String principalUri = UriUtils.buildUriPath(PrincipalService.SELF_LINK, user);
            Operation getPrincipal = Operation.createGet(service, principalUri);
            result = service.sendWithDeferredResult(getPrincipal, Principal.class)
                    .thenCompose(ignore -> {
                        Operation getUserState = Operation.createGet(service, userStateLink);
                        return service.sendWithDeferredResult(getUserState, UserState.class);
                    });
        } else {
            Operation getUserState = Operation.createGet(service, userStateLink);
            result = service.sendWithDeferredResult(getUserState, UserState.class);
        }

        result.thenApply(us -> new Pair<>(us, (Throwable) null))
                .exceptionally(ex -> new Pair<>(null, ex))
                .thenCompose(pair -> {
                    if (pair.right != null) {
                        if (pair.right.getCause() instanceof ServiceNotFoundException) {
                            UserState userState = new UserState();
                            userState.email = user;
                            userState.documentSelfLink = user;
                            Operation createUser = Operation.createPost(service,
                                    AuthUtil.buildUserServicePathFromPrincipalId(""))
                                    .setBody(userState);
                            addReplicationFactor(createUser);
                            return service.sendWithDeferredResult(createUser, UserState.class);
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
                        patch = ServiceStateCollectionUpdateRequest.create(null, patchGroupLinks);
                    } else {
                        Map<String, Collection<Object>> patchGroupLinks = new HashMap<>();
                        patchGroupLinks.put(UserState.FIELD_NAME_USER_GROUP_LINKS,
                                Collections.singletonList(groupLink));
                        patch = ServiceStateCollectionUpdateRequest.create(patchGroupLinks, null);
                    }
                    Operation patchOp = Operation.createPatch(service, userStateLink)
                            .setBody(patch);

                    return service.sendWithDeferredResult(patchOp, UserState.class);
                });

        return result;
    }

    private void validateUsersForDuplicates() {
        if (!Collections.disjoint(usersToAdd, usersToRemove)) {
            throw new IllegalStateException("Unable to assign and unassign role for same user.");
        }
    }

}
