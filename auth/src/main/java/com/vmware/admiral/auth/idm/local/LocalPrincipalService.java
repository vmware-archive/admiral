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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import com.vmware.admiral.auth.idm.AuthConfigProvider.CredentialsScope;
import com.vmware.admiral.auth.util.UserGroupsUpdater;
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
import com.vmware.xenon.services.common.ServiceUriPaths;
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

            if (LocalPrincipalType.USER == state.type) {
                createUserCredentials(state, post);
                return;
            }

            post.setBody(state).complete();
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
        if (LocalPrincipalType.USER == state.type) {
            deleteUserState(state.id, delete);
            return;
        }
        super.handleDelete(delete);
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
            assertNotNull(principalState.groupMembersLinks, "groupMembersLinks");
            principalState.id = Service.getId(principalState.documentSelfLink);
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
                    createUserState(state, op);
                });
        addReplicationFactor(postCreds);
        sendRequest(postCreds);
    }

    private void createUserState(LocalPrincipalState state, Operation op) {

        UserState userState = new UserState();
        userState.email = state.email;
        userState.documentSelfLink = state.email;
        URI userFactoryUri = UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_AUTHZ_USERS);
        Operation postUser = Operation.createPost(userFactoryUri)
                .setBody(userState)
                .setReferer(op.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Unable to create user state: %s", Utils.toString(ex));
                        op.fail(ex);
                        return;
                    }
                    finalizeUserCreate(state, op);
                });
        addReplicationFactor(postUser);
        sendRequest(postUser);
    }

    private void finalizeUserCreate(LocalPrincipalState state, Operation op) {
        String userGroupToAdd;
        DeferredResult<Void> updateUserRole;
        if (state.isAdmin == null || state.isAdmin) {
            userGroupToAdd = CLOUD_ADMINS_USER_GROUP_LINK;
            updateUserRole = UserGroupsUpdater.create(getHost(), CLOUD_ADMINS_USER_GROUP_LINK,
                    op.getUri().toString(), Collections.singletonList(state.email), null)
                    .update();
        } else {
            userGroupToAdd = BASIC_USERS_USER_GROUP_LINK;
            updateUserRole = UserGroupsUpdater.create(getHost(), BASIC_USERS_USER_GROUP_LINK,
                    op.getUri().toString(), Collections.singletonList(state.email), null)
                    .update();
        }

        updateUserRole.whenComplete((o, ex) -> {
            if (ex != null) {
                logWarning("Unable to add user %s to user group %s: %s", state.email,
                        userGroupToAdd, Utils.toString(ex));
                op.fail(ex);
                return;
            }
            state.password = null;
            op.setBody(state);
            op.complete();
        });
    }

    private void deleteUserState(String id, Operation delete) {
        URI uri = UriUtils.buildUri(getHost(), UserService.FACTORY_LINK);
        uri = UriUtils.extendUri(uri, id);

        sendRequest(Operation.createDelete(uri)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        delete.fail(ex);
                        return;
                    }
                    super.handleDelete(delete);
                }));
    }
}

