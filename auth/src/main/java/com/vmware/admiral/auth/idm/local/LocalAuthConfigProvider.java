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

import static com.vmware.admiral.auth.util.AuthUtil.BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.BASIC_USERS_RESOURCE_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.BASIC_USERS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.CLOUD_ADMINS_RESOURCE_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.CLOUD_ADMINS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.DEFAULT_BASIC_USERS_EXTENDED_ROLE_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.DEFAULT_BASIC_USERS_ROLE_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.DEFAULT_CLOUD_ADMINS_ROLE_LINK;
import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.idm.AuthConfigProvider;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalType;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class LocalAuthConfigProvider implements AuthConfigProvider {

    public static class Config {
        public List<User> users;

        public List<Group> groups;
    }

    public static class User {
        public String name;
        public String email;
        public String password;
        public Boolean isAdmin;
    }

    public static class Group {
        public String name;
        public List<String> members;
    }

    @Override
    public void initConfig(ServiceHost host, Operation post) {

        String localUsers = AuthUtil.getLocalUsersFile(host);

        if (!AuthUtil.useLocalUsers(host)) {
            host.log(Level.INFO,
                    "No users configuration file is specified. AuthX services are disabled!");
            post.complete();
            return;
        }

        Config config = getConfig(host, localUsers);
        if (config == null || config.users == null) {
            post.complete();
            return;
        }

        AtomicInteger counter = new AtomicInteger(8);
        AtomicBoolean hasError = new AtomicBoolean(false);

        host.registerForServiceAvailability((o, ex) -> {
            if (ex != null) {
                hasError.set(true);
                host.log(Level.SEVERE, "Unable to create default user groups: %s",
                        Utils.toString(ex));
            } else {
                if (counter.decrementAndGet() == 0 && !hasError.get()) {
                    createUsers(host, config, post);
                }
            }
        }, true, CLOUD_ADMINS_RESOURCE_GROUP_LINK,
                CLOUD_ADMINS_USER_GROUP_LINK,
                DEFAULT_CLOUD_ADMINS_ROLE_LINK,
                BASIC_USERS_RESOURCE_GROUP_LINK,
                BASIC_USERS_USER_GROUP_LINK,
                DEFAULT_BASIC_USERS_ROLE_LINK,
                BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK,
                DEFAULT_BASIC_USERS_EXTENDED_ROLE_LINK);

    }

    private static Config getConfig(ServiceHost host, String localUsers) {
        Config config;
        try {
            String content = new String(Files.readAllBytes((new File(localUsers)).toPath()));
            config = Utils.fromJson(content, Config.class);
        } catch (Exception e) {
            host.log(Level.SEVERE, "Failed to load users configuration file '%s'!. Error: %s",
                    localUsers, Utils.toString(e));
            return null;
        }

        if (config == null || config.users == null || config.users.isEmpty()) {
            host.log(Level.SEVERE, "No users found in the configuration file!");
            return null;
        }

        return config;
    }

    private static void createUsers(ServiceHost host, Config config, Operation post) {
        if (config == null || config.users == null) {
            createGroups(host, config, post);
            return;
        }

        AtomicInteger counter = new AtomicInteger(config.users.size());

        for (User user : config.users) {
            createUserIfNotExist(host, user, () -> {
                if (counter.decrementAndGet() == 0) {
                    createGroups(host, config, post);
                }
            });
        }
    }

    private static void createGroups(ServiceHost host, Config config, Operation post) {
        if (config == null || config.groups == null) {
            post.complete();
            return;
        }

        AtomicInteger counter = new AtomicInteger(config.groups.size());

        for (Group group : config.groups) {
            createGroup(host, group, () -> {
                if (counter.decrementAndGet() == 0) {
                    post.complete();
                }
            });
        }
    }

    private static void createUserIfNotExist(ServiceHost host, User user,
            Runnable callback) {

        host.log(Level.INFO, "createUserIfNotExist - User '%s'...", user.email);

        LocalPrincipalState state = new LocalPrincipalState();
        state.email = user.email;
        state.password = user.password;
        state.name = user.name;
        state.type = LocalPrincipalType.USER;
        state.isAdmin = user.isAdmin;

        host.sendRequest(Operation.createPost(host, LocalPrincipalFactoryService.SELF_LINK)
                .setBody(state)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Could not initialize user '%s': %s", user.email,
                                Utils.toString(ex));
                    }
                    callback.run();
                }));
    }

    private static void createGroup(ServiceHost host, Group group, Runnable
            callback) {
        host.log(Level.INFO, "createGroup - Group '%s'...", group.name);

        LocalPrincipalState state = new LocalPrincipalState();
        state.name = group.name;
        state.type = LocalPrincipalType.GROUP;
        state.groupMembersLinks = group.members.stream()
                .map(u -> UriUtils.buildUriPath(LocalPrincipalFactoryService.SELF_LINK, u))
                .collect(Collectors.toList());

        host.sendRequest(Operation.createPost(host, LocalPrincipalFactoryService.SELF_LINK)
                .setBody(state)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Could not initialize group '%s': %s",
                                group.name, Utils.toString(ex));
                    }
                    callback.run();
                }));
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

        Config config = getConfig(host, localUsers);
        if (config == null || config.users == null) {
            successfulCallback.run();
            return;
        }

        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicInteger counter = new AtomicInteger(config.users.size());

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

        for (User user : config.users) {
            waitForUser(host, user, callback, failCallback);
        }
    }

    private static void waitForUser(ServiceHost host, User user, Runnable
            successfulCallback, Consumer<Throwable> failureCallback) {

        final AtomicInteger servicesCounter = new AtomicInteger(1);
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
                buildUriPath(LocalPrincipalFactoryService.SELF_LINK, user.email));
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
