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

package com.vmware.admiral.auth.idm.local;

import static com.vmware.admiral.auth.idm.AuthConfigProvider.PROPERTY_SCOPE;
import static com.vmware.admiral.auth.util.AuthUtil.BASIC_USERS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.CLOUD_ADMINS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.addReplicationFactor;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.idm.AuthConfigProvider.CredentialsScope;
import com.vmware.admiral.auth.util.UserGroupsUpdater;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.Policy;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.UserService.UserState;

public class LocalPrincipalService extends StatefulService {

    public enum LocalPrincipalType {
        USER,
        GROUP
    }

    public static class LocalPrincipalState extends ServiceDocument {

        /**
         * ID of the user or group. In case of user the ID is the email,
         * in case of group the ID is generated.
         */
        public String id;

        /**
         * Name of the user or group.
         */
        public String name;

        /**
         * Email is user specific.
         */
        public String email;

        /**
         * Password in case the principal is user.
         */
        public String password;

        /**
         * List holding links to Users which are part of the group.
         * This property is group specific.
         */
        public List<String> groupMembersLinks;

        /**
         * Type of the principal, either User or Group.
         */
        public LocalPrincipalType type;

        /**
         * FLag specifying if the user is cloud admin.
         */
        public Boolean isAdmin;

    }

    public static class LocalPrincipalUserGroupAssignment {

        public List<String> groupLinksToAdd;

        public List<String> groupLinksToRemove;
    }

    public LocalPrincipalService() {
        super(LocalPrincipalState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleCreate(Operation post) {
        if (!checkForBody(post)) {
            return;
        }

        LocalPrincipalState state = post.getBody(LocalPrincipalState.class);
        try {
            validatePrincipal(state);

            if (state.type == null || LocalPrincipalType.USER == state.type) {
                createUserState(state, post);
            } else {
                createUserGroup(state, post);
            }

        } catch (Exception ex) {
            post.fail(ex);
        }

    }

    @Override
    public void handlePatch(Operation patch) {
        LocalPrincipalState currentState = getState(patch);

        LocalPrincipalState patchState = patch.getBody(LocalPrincipalState.class);
        try {
            validatePrincipalPatch(patchState, currentState);
            PropertyUtils.mergeServiceDocuments(currentState, patchState);
            patch.complete();
        } catch (Exception ex) {
            patch.fail(ex);
        }
    }

    @Override
    public void handleDelete(Operation delete) {
        LocalPrincipalState state = getState(delete);
        if (state.type == null || LocalPrincipalType.USER == state.type) {
            deleteUserState(state.id, delete);
        } else {
            deleteUserGroupState(state, delete);
        }
    }

    private void validatePrincipal(LocalPrincipalState principalState) {
        /*
          Keep these properties optional for now.
          Assume the type based on what is provided.
          If email is provided the type is USER if groupMemberLinks are provided the type is GROUP.

          assertNotNull(principalState.name, "name");
          assertNotNull(principalState.type, "type");
         */

        if (principalState.email != null) {
            principalState.type = LocalPrincipalType.USER;
        } else if (principalState.groupMembersLinks != null
                && !principalState.groupMembersLinks.isEmpty()) {
            principalState.type = LocalPrincipalType.GROUP;
        }

        if (principalState.type == LocalPrincipalType.GROUP) {
            principalState.id = principalState.name;
            return;
        }

        if (principalState.type == LocalPrincipalType.USER) {
            assertNotNull(principalState.email, "email");
            principalState.id = principalState.email;
        }

    }

    private void validatePrincipalPatch(LocalPrincipalState patchState, LocalPrincipalState
            currentState) {

        if (patchState.email != null && !patchState.email.equals(currentState.email)) {
            throw new IllegalStateException("Email property cannot be patched.");
        }

        if (patchState.id != null && !patchState.id.equals(currentState.id)) {
            throw new IllegalStateException("Id property cannot be patched");
        }

    }

    private void createUserState(LocalPrincipalState state, Operation op) {
        UserState user = new UserState();
        user.email = state.email;
        user.documentSelfLink = state.email;

        URI userFactoryUri = UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_AUTHZ_USERS);
        Operation postUser = Operation.createPost(userFactoryUri)
                .setBody(user)
                .setReferer(op.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Unable to create user state %s: %s", state.email, Utils
                                .toString(ex));
                        op.fail(ex);
                        return;
                    }
                    createUserCredentials(state, op);
                });

        addReplicationFactor(postUser);
        sendRequest(postUser);

    }

    private void createUserCredentials(LocalPrincipalState state, Operation op) {
        try {
            state.password = EncryptionUtils.decrypt(state.password);
        } catch (Exception e) {
            log(Level.SEVERE, "Could not initialize user '%s': %s", state.email,
                    Utils.toString(e));
            op.fail(e);
            return;
        }

        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.userEmail = state.email;
        auth.privateKey = state.password;
        auth.customProperties = new HashMap<>();
        auth.customProperties.put(PROPERTY_SCOPE, CredentialsScope.SYSTEM.toString());
        auth.documentSelfLink = state.email;

        URI credentialFactoryUri = UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_CREDENTIALS);
        Operation postCreds = Operation.createPost(credentialFactoryUri)
                .setBody(auth)
                .setReferer(op.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Unable to create user credentials: %s", Utils.toString(ex));
                        op.fail(ex);
                        return;
                    }
                    createUserSpecificRole(state, op);
                });
        addReplicationFactor(postCreds);
        sendRequest(postCreds);
    }

    private void createUserSpecificRole(LocalPrincipalState state, Operation op) {
        // Create query for user specific user group, which will match only this user.

        String userGroupUri = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, state.id);
        String resourceGroupUri = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                state.id);
        String roleUri = UriUtils.buildUriPath(RoleService.FACTORY_LINK, state.id);

        Query userGroupQuery = Query.Builder.create()
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        UriUtils.buildUriPath(UserService.FACTORY_LINK, state.id))
                .build();

        UserGroupState groupState = UserGroupState.Builder.create()
                .withSelfLink(userGroupUri)
                .withQuery(userGroupQuery).build();

        Query resourceGroupQuery = Query.Builder.create()
                .addFieldClause(ServiceDocument.FIELD_NAME_AUTH_PRINCIPAL_LINK,
                        state.documentSelfLink)
                .build();

        ResourceGroupState resourceGroupState = ResourceGroupState.Builder.create()
                .withSelfLink(resourceGroupUri)
                .withQuery(resourceGroupQuery).build();

        RoleState roleState = RoleState.Builder.create()
                .withPolicy(Policy.ALLOW)
                .withUserGroupLink(userGroupUri)
                .withResourceGroupLink(resourceGroupUri)
                .withSelfLink(roleUri)
                .withVerbs(EnumSet.allOf(Action.class)).build();

        Operation createUserGroup = Operation.createPost(this, UserGroupService.FACTORY_LINK)
                .setReferer(op.getUri())
                .setBody(groupState);

        Operation createResourceGroup = Operation
                .createPost(this, ResourceGroupService.FACTORY_LINK)
                .setReferer(op.getUri())
                .setBody(resourceGroupState);

        Operation createRole = Operation.createPost(this, RoleService.FACTORY_LINK)
                .setReferer(op.getUri())
                .setBody(roleState);

        sendWithDeferredResult(createUserGroup, UserGroupState.class)
                .thenCompose(ignore -> sendWithDeferredResult(createResourceGroup,
                        ResourceGroupState.class))
                .thenCompose(ignore -> sendWithDeferredResult(createRole, RoleState.class))
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        op.fail(ex);
                        return;
                    }
                    finalizeUserCreate(state, op);
                });
    }

    private void finalizeUserCreate(LocalPrincipalState state, Operation op) {
        List<DeferredResult<Void>> result = new ArrayList<>();
        if (state.isAdmin == null || state.isAdmin) {
            result.add(UserGroupsUpdater.create()
                    .setUsersToRemove(null)
                    .setUsersToAdd(Collections.singletonList(state.email))
                    .setReferrer(op.getUri().toString())
                    .setGroupLink(CLOUD_ADMINS_USER_GROUP_LINK)
                    .setHost(getHost())
                    .setSkipPrincipalVerification(true)
                    .update());
        }
        // We want always to add the user to basic users, even if he is cloud admin,
        // in case he is removed from cloud admins he will remain basic user.
        result.add(UserGroupsUpdater.create()
                .setUsersToRemove(null)
                .setUsersToAdd(Collections.singletonList(state.email))
                .setReferrer(op.getUri().toString())
                .setGroupLink(BASIC_USERS_USER_GROUP_LINK)
                .setHost(getHost())
                .setSkipPrincipalVerification(true)
                .update());

        DeferredResult.allOf(result)
                .whenComplete((ignore, ex) -> {
                    logInfo("User %s successfully created.", state.email);
                    state.password = null;
                    op.setBody(state);
                    op.complete();
                });
    }

    private void createUserGroup(LocalPrincipalState state, Operation op) {
        String userGroupSelfLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, state.name);
        Query userGroupQuery = AuthUtils.buildQueryForUsers(userGroupSelfLink);

        UserGroupState userGroupState = UserGroupState.Builder.create()
                .withSelfLink(userGroupSelfLink)
                .withQuery(userGroupQuery)
                .build();

        URI userGroupFactoryUri = UriUtils.buildUri(getHost(),
                ServiceUriPaths.CORE_AUTHZ_USER_GROUPS);
        Operation postGroup = Operation.createPost(userGroupFactoryUri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(userGroupState)
                .setReferer(op.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Unable to create user group %s: %s", state.name,
                                Utils.toString(ex));
                        return;
                    }
                    UserGroupState groupState = o.getBody(UserGroupState.class);
                    addUsersToGroup(state, groupState.documentSelfLink, op);
                });
        addReplicationFactor(postGroup);
        sendRequest(postGroup);
    }

    private void addUsersToGroup(LocalPrincipalState group, String groupLink, Operation op) {
        AtomicInteger counter = new AtomicInteger(group.groupMembersLinks.size());
        AtomicBoolean hasError = new AtomicBoolean(false);

        for (String localPrincipalLink : group.groupMembersLinks) {
            addUserToGroup(localPrincipalLink, groupLink,
                    (ex) -> {
                        if (ex != null) {
                            if (hasError.compareAndSet(false, true)) {
                                op.fail(ex);
                            } else {
                                logWarning("Error when adding user %s to group %s: %s",
                                        localPrincipalLink, groupLink, Utils.toString(ex));
                            }
                        } else {
                            if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                op.setBody(group);
                                op.complete();
                            }
                        }
                    });
        }
    }

    private void addUserToGroup(String localPrincipalLink, String groupLink, Consumer<Throwable>
            callback) {
        sendRequest(Operation.createGet(getHost(), localPrincipalLink)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        callback.accept(ex);
                        return;
                    }
                    LocalPrincipalState localPrincipalState = o.getBody(LocalPrincipalState.class);
                    DeferredResult<Void> result = UserGroupsUpdater.create()
                            .setGroupLink(groupLink)
                            .setHost(getHost())
                            .setReferrer(getHost().getUri().toString())
                            .setUsersToAdd(Collections.singletonList(localPrincipalState.email))
                            .update();
                    result.whenComplete((ignore, err) -> {
                        if (err != null) {
                            callback.accept(err);
                            return;
                        }
                        callback.accept(null);
                    });
                }));
    }

    private void deleteUserState(String id, Operation delete) {
        String userStateUri = UriUtils.buildUriPath(UserService.FACTORY_LINK, id);
        String userGroupUri = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, id);
        String resourceGroupUri = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, id);
        String roleUri = UriUtils.buildUriPath(RoleService.FACTORY_LINK, id);

        Operation deleteUserState = Operation.createDelete(this, userStateUri)
                .setReferer(delete.getUri());

        Operation deleteUserGroupState = Operation.createDelete(this, userGroupUri)
                .setReferer(delete.getUri());

        Operation deleteResourceGroupState = Operation.createDelete(this, resourceGroupUri)
                .setReferer(delete.getUri());

        Operation deleteRoleState = Operation.createDelete(this, roleUri)
                .setReferer(delete.getUri());

        sendWithDeferredResult(deleteRoleState, RoleState.class)
                .thenCompose(ignore -> sendWithDeferredResult(deleteResourceGroupState,
                        ResourceGroupState.class))
                .thenCompose(ignore -> sendWithDeferredResult(deleteUserGroupState,
                        UserGroupState.class))
                .thenCompose(ignore -> sendWithDeferredResult(deleteUserState,
                        UserState.class))
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        logSevere("Problem during user cleanup %s: %s",
                                id, Utils.toString(ex));
                        delete.fail(ex);
                        return;
                    }
                    super.handleDelete(delete);
                });

    }

    private void deleteUserGroupState(LocalPrincipalState state, Operation delete) {
        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, state.id);

        sendRequest(Operation.createDelete(getHost(), userGroupLink)
                .setReferer(delete.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        delete.fail(ex);
                        return;
                    }
                    removeUsersFromDeletedGroup(state, userGroupLink, delete);
                }));
    }

    private void removeUsersFromDeletedGroup(LocalPrincipalState localGroupState, String groupLink,
            Operation delete) {
        List<String> usersToRemoveFromGroup = localGroupState.groupMembersLinks.stream()
                .map(gm -> Service.getId(gm))
                .collect(Collectors.toList());

        DeferredResult<Void> result = UserGroupsUpdater.create()
                .setReferrer(delete.getUri().toString())
                .setHost(getHost())
                .setGroupLink(groupLink)
                .setUsersToRemove(usersToRemoveFromGroup)
                .update();

        result.whenComplete((ignore, ex) -> {
            if (ex != null) {
                delete.fail(ex);
                return;
            }
            super.handleDelete(delete);
        });
    }

    private boolean isRoleAssignmentUpdate(LocalPrincipalUserGroupAssignment patchGroups) {
        if (patchGroups == null) {
            return false;
        }
        boolean add = patchGroups.groupLinksToAdd != null && !patchGroups.groupLinksToAdd.isEmpty();
        boolean remove = patchGroups.groupLinksToRemove != null
                && !patchGroups.groupLinksToRemove.isEmpty();
        return add || remove;
    }
}

