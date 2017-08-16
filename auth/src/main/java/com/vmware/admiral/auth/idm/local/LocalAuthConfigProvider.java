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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.UserService.UserState;
import com.vmware.xenon.services.common.authn.BasicAuthenticationService;

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
    public void initBootConfig(ServiceHost host, Operation post) {

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

        String[] servicePaths = new String[] {
                CLOUD_ADMINS_RESOURCE_GROUP_LINK,
                CLOUD_ADMINS_USER_GROUP_LINK,
                DEFAULT_CLOUD_ADMINS_ROLE_LINK,
                BASIC_USERS_RESOURCE_GROUP_LINK,
                BASIC_USERS_USER_GROUP_LINK,
                DEFAULT_BASIC_USERS_ROLE_LINK,
                BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK,
                DEFAULT_BASIC_USERS_EXTENDED_ROLE_LINK };

        AtomicInteger counter = new AtomicInteger(servicePaths.length);
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
        }, true, servicePaths);

    }

    @Override
    public void initConfig(ServiceHost host, Operation post) {
        // Nothing to do here...
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

        List<DeferredResult<Operation>> usersDeferredResults = new ArrayList<>();

        config.users.forEach((user) -> usersDeferredResults.add(
                createUserIfNotExist(host, user).whenComplete((op, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Could not initialize user '%s': %s", user.email,
                                Utils.toString(ex));
                    }
                })));

        DeferredResult.allOf(usersDeferredResults)
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        post.fail(ex);
                        return;
                    }
                    createGroups(host, config, post);
                });
    }

    private static void createGroups(ServiceHost host, Config config, Operation post) {
        if (config == null || config.groups == null) {
            post.complete();
            return;
        }

        List<DeferredResult<Operation>> groupsDeferredResult = new ArrayList<>();

        config.groups.forEach((group) -> groupsDeferredResult.add(
                createGroup(host, group).whenComplete((op, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Could not initialize group '%s': %s",
                                group.name, Utils.toString(ex));
                    }
                })));

        DeferredResult.allOf(groupsDeferredResult)
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        post.fail(ex);
                        return;
                    }
                    post.complete();
                });
    }

    private static DeferredResult<Operation> createUserIfNotExist(ServiceHost host, User user) {

        host.log(Level.INFO, "createUserIfNotExist - User '%s'...", user.email);

        LocalPrincipalState state = new LocalPrincipalState();
        state.email = user.email;
        state.password = user.password;
        state.name = user.name;
        state.type = LocalPrincipalType.USER;
        state.isAdmin = user.isAdmin;

        Operation op = Operation.createPost(host, LocalPrincipalFactoryService.SELF_LINK)
                .setBody(state)
                .setReferer(host.getUri());

        return host.sendWithDeferredResult(op);
    }

    private static DeferredResult<Operation> createGroup(ServiceHost host, Group group) {
        host.log(Level.INFO, "createGroup - Group '%s'...", group.name);

        LocalPrincipalState state = new LocalPrincipalState();
        state.name = group.name;
        state.type = LocalPrincipalType.GROUP;
        state.groupMembersLinks = group.members.stream()
                .map(u -> UriUtils.buildUriPath(LocalPrincipalFactoryService.SELF_LINK, u))
                .collect(Collectors.toList());

        Operation op = Operation.createPost(host, LocalPrincipalFactoryService.SELF_LINK)
                .setBody(state)
                .setReferer(host.getUri());

        return host.sendWithDeferredResult(op);
    }

    @Override
    public void waitForInitBootConfig(ServiceHost host, String localUsers,
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

    private static void waitForUser(ServiceHost host, User user, Runnable successfulCallback,
            Consumer<Throwable> failureCallback) {

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
        return null;
    }

    @Override
    public String getAuthenticationServiceSelfLink() {
        return BasicAuthenticationService.SELF_LINK;
    }

    @Override
    public Function<Claims, String> getAuthenticationServiceUserLinkBuilder() {
        return LocalAuthConfigProvider::buildUserUri;
    }

    private static String buildUserUri(Claims claims) {
        return UserService.FACTORY_LINK + "/" + claims.getSubject().toLowerCase();
    }

    @Override
    public Collection<FactoryService> createServiceFactories() {
        return Collections.emptyList();
    }

    @Override
    public Collection<Service> createServices() {
        return Collections.emptyList();
    }

    @Override
    public Class<? extends UserState> getUserStateClass() {
        return UserState.class;
    }

}
