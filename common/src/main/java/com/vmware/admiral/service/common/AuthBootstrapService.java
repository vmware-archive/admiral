/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.AuthorizationSetupHelper;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserService;

/**
 * One-time node group setup (bootstrap) for the auth settings.
 *
 * This service is guaranteed to be performed only once within entire node group, in a consistent
 * safe way. Durable for restarting the owner node or even complete shutdown and restarting of all
 * nodes. Following the SampleBootstrapService.
 */
public class AuthBootstrapService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.CONFIG + "/auth-bootstrap";

    public static FactoryService createFactory() {
        return FactoryService.create(AuthBootstrapService.class);
    }

    public static final String LOCAL_USERS_FILE = "localUsers";

    public static final String PROPERTY_SCOPE = "scope";

    public static enum CredentialsScope {
        SYSTEM
    }

    public static class Config {
        public List<User> users;
    }

    public static class User {
        public String email;
        public String password;
    }

    public static CompletionHandler startTask(ServiceHost host) {
        return (o, e) -> {
            if (e != null) {
                host.log(Level.SEVERE, Utils.toString(e));
                return;
            }
            // create service with fixed link
            // POST will be issued multiple times but will be converted to PUT after the first one.
            ServiceDocument doc = new ServiceDocument();
            doc.documentSelfLink = "auth-preparation-task";
            Operation.createPost(host, AuthBootstrapService.FACTORY_LINK)
                    .setBody(doc)
                    .setReferer(host.getUri())
                    .setCompletion((oo, ee) -> {
                        if (ee != null) {
                            host.log(Level.SEVERE, Utils.toString(ee));
                            return;
                        }
                        host.log(Level.INFO, "auth-preparation-task triggered");
                    })
                    .sendWith(host);
        };
    }

    public AuthBootstrapService() {
        super(ServiceDocument.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {
        getHost().log(Level.INFO, "handleStart");

        if (!ServiceHost.isServiceCreate(post)) {
            // do not perform bootstrap logic when the post is NOT from direct client, eg: node
            // restart
            post.complete();
            return;
        }
        initConfig(getHost(), post);
    }

    @Override
    public void handlePut(Operation put) {
        getHost().log(Level.INFO, "handlePut");

        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            // converted PUT due to IDEMPOTENT_POST option
            logInfo("Task has already started. Ignoring converted PUT.");
            put.complete();
            return;
        }
        // normal PUT is not supported
        put.fail(Operation.STATUS_CODE_BAD_METHOD);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

    private static void initConfig(ServiceHost host, Operation post) {

        String localUsers = getLocalUsers(host);

        if (!isAuthxEnabled(localUsers)) {
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

    private static String getLocalUsers(ServiceHost host) {
        try {
            return (String) host.getClass().getField(LOCAL_USERS_FILE).get(host);
        } catch (Exception e) {
            host.log(Level.SEVERE, Utils.toString(e));
            return null;
        }
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
                currentState.customProperties.get(AuthBootstrapService.PROPERTY_SCOPE)))) {
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

    /*
     * Helper method to check whether auth is enabled or not.
     */
    public static boolean isAuthxEnabled(String localUsers) {
        return (localUsers != null && !localUsers.isEmpty());
    }

    /*
     * Helper method to wait for the initial configuration to be completed.
     */
    public static void waitForInitConfig(ServiceHost host, String localUsers, Runnable
            successfulCallback, Consumer<Throwable> failureCallback) {

        if (!isAuthxEnabled(localUsers)) {
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

    /*
     * Helper method to wait for the provided user initialization to be completed.
     */
    public static void waitForUser(ServiceHost host, User user, Runnable successfulCallback,
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

}
