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

import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

import com.vmware.admiral.auth.idm.AuthConfigProvider;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.AuthorizationSetupHelper;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserService;

public class LocalAuthConfigProvider implements AuthConfigProvider {

    public static class Config {
        public List<User> users;
    }

    public static class User {
        public String email;
        public String password;
    }

    @Override
    public void initConfig(ServiceHost host, Operation post) {
        // TODO Auto-generated method stub

        String localUsers = AuthUtil.getLocalUsersFile(host);

        if (!AuthUtil.useLocalUsers(host)) {
            host.log(Level.INFO,
                    "No users configuration file is specified. AuthX services are disabled!");
            post.complete();
            return;
        }

        List<User> users = getUsers(host, localUsers);
        if (users == null) {
            post.complete();
            return;
        }

        EncryptionUtils.initEncryptionService();

        final AtomicInteger usersCounter = new AtomicInteger(users.size());

        users.forEach(user -> {
            try {
                createUserIfNotExist(host, user);
                if (usersCounter.decrementAndGet() == 0) {
                    post.complete();
                }
            } catch (Exception e) {
                post.fail(e);
            }
        });
    }

    private static List<User> getUsers(ServiceHost host, String localUsers) {
        Config config;
        try {
            String content = new String(Files.readAllBytes((new File(localUsers)).toPath()));
            config = Utils.fromJson(content, Config.class);
        } catch (Exception e) {
            host.log(Level.SEVERE, "Failed to load users configuration file '%s'!. Error: %s",
                    localUsers, Utils.toString(e));
            return null;
        }

        if (config.users == null || config.users.isEmpty()) {
            host.log(Level.SEVERE, "No users found in the configuration file!");
            return null;
        }

        return config.users;
    }

    private static void createUserIfNotExist(ServiceHost host, User user) {

        host.log(Level.INFO, "createUserIfNotExist - User '%s'...", user.email);

        try {
            user.password = EncryptionUtils.decrypt(user.password);
        } catch (Exception e) {
            host.log(Level.SEVERE, "Could not initialize user '%s': %s", user.email,
                    Utils.toString(e));
            throw new IllegalStateException(e);
        }

        AuthorizationSetupHelper.create()
                .setHost(host)
                .setUserEmail(user.email)
                .setUserPassword(user.password)
                .setUserSelfLink(user.email)
                .setUserGroupName(user.email + "-user-group")
                .setResourceGroupName(user.email + "-resource-group")
                .setRoleName(user.email + "-role")
                // TODO - for now all authenticated users can access everywhere
                .setIsAdmin(true)
                .setCompletion((e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE, "Could not initialize user '%s': %s", user.email,
                                Utils.toString(e));
                        throw new IllegalStateException(e);
                    }
                    updateCredentials(host, user.email);
                })
                .start();
    }

    /*
     * Next two methods update the credentials type to distinguish the local users credentials
     * (internal) from the credentials that can be created for configuration purposes (public key
     * or password), e.g. when adding a host.
     */

    private static void updateCredentials(ServiceHost host, String email) {

        QueryTask credentialsQuery = QueryUtil.buildQuery(AuthCredentialsServiceState.class, true);
        QueryUtil.addListValueClause(credentialsQuery,
                AuthCredentialsServiceState.FIELD_NAME_EMAIL, Arrays.asList(email));
        QueryUtil.addExpandOption(credentialsQuery);

        host.log(Level.INFO, "updateCredentials - User '%s'...", email);

        new ServiceDocumentQuery<AuthCredentialsServiceState>(host,
                AuthCredentialsServiceState.class).query(
                        credentialsQuery, (r) -> {
                            if (r.hasException()) {
                                host.log(Level.SEVERE, "Exception retrieving user '%s'. Error: %s",
                                        email, Utils.toString(r.getException()));
                                throw new IllegalStateException(r.getException());
                            } else if (r.hasResult()) {
                                patchStateType(host, r.getResult());
                            } else {
                                // nothing to do here
                            }
                        });
    }

    private static void patchStateType(ServiceHost host, AuthCredentialsServiceState currentState) {
        // Shouldn't update users which already have been updated.
        if ((currentState.customProperties != null) && (CredentialsScope.SYSTEM.toString().equals(
                currentState.customProperties.get(PROPERTY_SCOPE)))) {
            return;
        }

        AuthCredentialsServiceState patch = new AuthCredentialsServiceState();
        patch.customProperties = new HashMap<>();
        patch.customProperties.put(PROPERTY_SCOPE, CredentialsScope.SYSTEM.toString());

        // Credentials with SYSTEM scope need the password in plain text or they can't be used to
        // login into Admiral!
        patch.privateKey = EncryptionUtils.decrypt(currentState.privateKey);

        host.log(Level.INFO, "patchStateType - User '%s'...", currentState.userEmail);

        Operation.createPatch(UriUtils.buildUri(host, currentState.documentSelfLink))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setReferer(host.getUri())
                .setBody(patch)
                .setCompletion((o, e) -> {
                    if (e == null) {
                        host.log(Level.INFO, "User '%s' initialized!", currentState.userEmail);
                    } else {
                        host.log(Level.SEVERE, "Could not patch user '%s' credentials '%s': %s",
                                currentState.userEmail, currentState.documentSelfLink,
                                Utils.toString(e));
                        throw new IllegalStateException(e);
                    }
                })
                .sendWith(host);
    }

    @Override
    public void waitForInitConfig(ServiceHost host, String localUsers,
            Runnable successfulCallback, Consumer<Throwable> failureCallback) {

        if (!AuthUtil.useLocalUsers(host)) {
            host.log(Level.INFO,
                    "No users configuration file is specified. AuthX services are disabled!");
            successfulCallback.run();
            return;
        }

        List<User> users = getUsers(host, localUsers);
        if (users == null) {
            successfulCallback.run();
            return;
        }

        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicInteger counter = new AtomicInteger(users.size());

        Consumer<Throwable> failCallback = (e) -> {
            if (hasError.compareAndSet(false, true)) {
                host.log(Level.WARNING, "Failure on wait for user: %s, calling the failure "
                        + "callback", Utils.toString(e));
                failureCallback.accept(e);
            } else {
                host.log(Level.WARNING, "Failure on wait for user: %s", Utils.toString(e));
            }
        };

        Runnable callback = () -> {
            if (counter.decrementAndGet() == 0 && !hasError.get()) {
                successfulCallback.run();
            }
        };

        for (User user : users) {
            waitForUser(host, user, callback, failCallback);
        }
    }

    private static void waitForUser(ServiceHost host, User user, Runnable successfulCallback,
            Consumer<Throwable> failureCallback) {

        final AtomicInteger servicesCounter = new AtomicInteger(4);
        final AtomicBoolean hasError = new AtomicBoolean(false);

        host.registerForServiceAvailability((o, e) -> {
            if (e != null) {
                hasError.set(true);
                failureCallback.accept(e);
            } else {
                if (servicesCounter.decrementAndGet() == 0 && !hasError.get()) {
                    successfulCallback.run();
                }
            }
        }, true,
                buildUriPath(UserService.FACTORY_LINK, user.email),
                buildUriPath(UserGroupService.FACTORY_LINK, user.email + "-user-group"),
                buildUriPath(ResourceGroupService.FACTORY_LINK, user.email + "-resource-group"),
                buildUriPath(RoleService.FACTORY_LINK, user.email + "-role"));
    }

    @Override
    public Service getAuthenticationService() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getAuthenticationServiceSelfLink() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Function<Claims, String> getAuthenticationServiceUserLinkBuilder() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public FactoryService createUserServiceFactory() {
        // TODO Auto-generated method stub
        return null;
    }

}
