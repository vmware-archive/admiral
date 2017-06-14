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

import static com.vmware.admiral.common.util.AuthUtils.buildQueryForUsers;

import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.ServiceLoader;
import java.util.logging.Level;

import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.Policy;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;

public class AuthUtil {
    public static final String DEFAULT_IDENTIFIER = "default";

    public static final String DEFAULT_CLOUD_ADMINS = AuthRole.CLOUD_ADMINS
            .buildRoleWithSuffix(DEFAULT_IDENTIFIER);

    public static final String CLOUD_ADMINS_RESOURCE_GROUP_LINK = UriUtils
            .buildUriPath(ResourceGroupService.FACTORY_LINK, AuthRole.CLOUD_ADMINS.getSuffix());

    public static final String CLOUD_ADMINS_USER_GROUP_LINK = UriUtils
            .buildUriPath(UserGroupService.FACTORY_LINK, AuthRole.CLOUD_ADMINS.getSuffix());

    public static final String DEFAULT_CLOUD_ADMINS_ROLE_LINK = UriUtils
            .buildUriPath(RoleService.FACTORY_LINK, DEFAULT_CLOUD_ADMINS);


    public static final String DEFAULT_BASIC_USERS = AuthRole.BASIC_USERS
            .buildRoleWithSuffix(DEFAULT_IDENTIFIER);

    public static final String BASIC_USERS_RESOURCE_GROUP_LINK = UriUtils
            .buildUriPath(ResourceGroupService.FACTORY_LINK, AuthRole.BASIC_USERS.getSuffix());

    public static final String BASIC_USERS_USER_GROUP_LINK = UriUtils
            .buildUriPath(UserGroupService.FACTORY_LINK, AuthRole.BASIC_USERS.getSuffix());

    public static final String DEFAULT_BASIC_USERS_ROLE_LINK = UriUtils
            .buildUriPath(RoleService.FACTORY_LINK, DEFAULT_BASIC_USERS);

    public static final String DEFAULT_BASIC_USERS_EXTENDED = AuthRole.BASIC_USERS_EXTENDED
            .buildRoleWithSuffix(DEFAULT_IDENTIFIER);

    public static final String BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK = UriUtils
            .buildUriPath(ResourceGroupService.FACTORY_LINK,
                    AuthRole.BASIC_USERS_EXTENDED.getSuffix());

    public static final String DEFAULT_BASIC_USERS_EXTENDED_ROLE_LINK = UriUtils
            .buildUriPath(RoleService.FACTORY_LINK, DEFAULT_BASIC_USERS_EXTENDED);

    public static final String LOCAL_USERS_FILE = "localUsers";

    public static final String AUTH_CONFIG_FILE = "authConfig";

    private static final String PREFERRED_PROVIDER_PACKAGE = "com.vmware.admiral.auth.idm.psc";

    public static String createAuthorizationHeader(AuthCredentialsServiceState authState) {
        if (authState == null) {
            return null;
        }

        AuthCredentialsType authCredentialsType = AuthCredentialsType.valueOf(authState.type);
        if (AuthCredentialsType.Password.equals(authCredentialsType)) {
            String username = authState.userEmail;
            String password = EncryptionUtils.decrypt(authState.privateKey);

            String code = new String(Base64.getEncoder().encode(
                    new StringBuffer(username).append(":").append(password).toString().getBytes()));
            String headerValue = new StringBuffer("Basic ").append(code).toString();

            return headerValue;
        }

        return null;
    }

    public static boolean isAuthxEnabled(ServiceHost host) {
        return useLocalUsers(host) || useAuthConfig(host);
    }

    public static boolean useLocalUsers(ServiceHost host) {
        String field = getLocalUsersFile(host);
        return (field != null) && (!field.isEmpty());
    }

    public static boolean useAuthConfig(ServiceHost host) {
        String field = getAuthConfigFile(host);
        return (field != null) && (!field.isEmpty());
    }

    public static String getLocalUsersFile(ServiceHost host) {
        try {
            return PropertyUtils.getValue(host, LOCAL_USERS_FILE);
        } catch (Exception e) {
            host.log(Level.SEVERE, Utils.toString(e));
            return null;
        }
    }

    public static String getAuthConfigFile(ServiceHost host) {
        return PropertyUtils.getValue(host, AUTH_CONFIG_FILE);
    }

    public static <T> T getPreferredProvider(Class<T> clazz) {

        ServiceLoader<T> loader = ServiceLoader.load(clazz);

        T provider = null;

        for (T loaderProvider : loader) {
            if (provider != null
                    && provider.getClass().getName().startsWith(PREFERRED_PROVIDER_PACKAGE)) {
                Utils.logWarning("Ignoring provider '%s'.", loaderProvider.getClass().getName());
                continue;
            }

            Utils.logWarning("Using provider '%s'.", loaderProvider.getClass().getName());
            provider = loaderProvider;
        }

        if (provider == null) {
            throw new IllegalStateException("No provider found!");
        }

        return provider;
    }

    public static UserGroupState buildEmptyCloudAdminsUserGroup() {
        String id = AuthRole.CLOUD_ADMINS.getSuffix();

        return buildUserGroupState(id);
    }

    public static ResourceGroupState buildCloudAdminsResourceGroup() {
        Query resourceGroupQuery = Query.Builder
                .create()
                .setTerm(ServiceDocument.FIELD_NAME_SELF_LINK, UriUtils.URI_WILDCARD_CHAR,
                        QueryTerm.MatchType.WILDCARD)
                .build();

        ResourceGroupState resourceGroupState = ResourceGroupState.Builder
                .create()
                .withQuery(resourceGroupQuery)
                .withSelfLink(CLOUD_ADMINS_RESOURCE_GROUP_LINK)
                .build();

        return resourceGroupState;
    }

    public static RoleState buildCloudAdminsRole(String identifier, String userGroupLink) {
        String id = AuthRole.CLOUD_ADMINS.buildRoleWithSuffix(identifier);
        String selfLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, id);
        EnumSet<Action> verbs = EnumSet.allOf(Action.class);
        Collections.addAll(verbs, Action.values());

        RoleState cloudAdminRole = RoleState.Builder
                .create()
                .withPolicy(Policy.ALLOW)
                .withSelfLink(selfLink)
                .withVerbs(verbs)
                .withResourceGroupLink(CLOUD_ADMINS_RESOURCE_GROUP_LINK)
                .withUserGroupLink(userGroupLink)
                .build();

        return cloudAdminRole;
    }

    public static UserGroupState buildEmptyBasicUsersUserGroup() {
        String id = AuthRole.BASIC_USERS.getSuffix();

        return buildUserGroupState(id);
    }

    public static ResourceGroupState buildBasicUsersResourceGroup() {

        Query resourceGroupQuery = Query.Builder
                .create()
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        buildUriWithWildcard(ProjectFactoryService.SELF_LINK),
                        MatchType.WILDCARD, Occurance.SHOULD_OCCUR)
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        buildUriWithWildcard(ConfigurationFactoryService.SELF_LINK),
                        MatchType.WILDCARD, Occurance.SHOULD_OCCUR)

                .build();

        ResourceGroupState resourceGroupState = ResourceGroupState.Builder
                .create()
                .withQuery(resourceGroupQuery)
                .withSelfLink(BASIC_USERS_RESOURCE_GROUP_LINK)
                .build();

        return resourceGroupState;
    }

    public static RoleState buildBasicUsersRole(String identifier, String userGroupLink) {
        String id = AuthRole.BASIC_USERS.buildRoleWithSuffix(identifier);
        String selfLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, id);
        EnumSet<Action> verbs = EnumSet.noneOf(Action.class);
        verbs.add(Action.GET);

        RoleState basicUsersRole = RoleState.Builder
                .create()
                .withPolicy(Policy.ALLOW)
                .withSelfLink(selfLink)
                .withVerbs(verbs)
                .withResourceGroupLink(BASIC_USERS_RESOURCE_GROUP_LINK)
                .withUserGroupLink(userGroupLink)
                .build();

        return basicUsersRole;
    }

    /**
     * This is currently used to workaround xenon problem, where we can't
     * get documents if with OData query.
     */
    public static ResourceGroupState buildBasicUsersExtendedResourceGroup() {

        Query resourceGroupQuery = Query.Builder
                .create()
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        buildUriWithWildcard(ServiceUriPaths.CORE_QUERY_TASKS),
                        MatchType.WILDCARD, Occurance.SHOULD_OCCUR)
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        buildUriWithWildcard(ServiceUriPaths.CORE_QUERY_PAGE),
                        MatchType.WILDCARD, Occurance.SHOULD_OCCUR)
                .build();

        ResourceGroupState resourceGroupState = ResourceGroupState.Builder
                .create()
                .withQuery(resourceGroupQuery)
                .withSelfLink(BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK)
                .build();

        return resourceGroupState;
    }

    /**
     * This is currently used to workaround xenon problem, where we can't
     * get documents if with OData query.
     */
    public static RoleState buildBasicUsersExtendedRole(String identifier, String userGroupLink) {
        String id = AuthRole.BASIC_USERS_EXTENDED.buildRoleWithSuffix(identifier);
        String selfLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, id);

        EnumSet<Action> verbs = EnumSet.noneOf(Action.class);
        verbs.add(Action.GET);
        verbs.add(Action.POST);

        RoleState basicUsersRole = RoleState.Builder
                .create()
                .withPolicy(Policy.ALLOW)
                .withSelfLink(selfLink)
                .withVerbs(verbs)
                .withResourceGroupLink(BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK)
                .withUserGroupLink(userGroupLink)
                .build();

        return basicUsersRole;
    }

    public static UserGroupState buildProjectAdminsUserGroup(String projectId) {
        String id = AuthRole.PROJECT_ADMINS.buildRoleWithSuffix(projectId);

        return buildUserGroupState(id);
    }

    public static UserGroupState buildProjectMembersUserGroup(String projectId) {
        String id = AuthRole.PROJECT_MEMBERS.buildRoleWithSuffix(projectId);

        return buildUserGroupState(id);
    }

    public static UserGroupState buildUserGroupState(String identifier) {
        String selfLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, identifier);

        Query userGroupQuery = buildQueryForUsers(selfLink);

        UserGroupState userGroupState = UserGroupState.Builder
                .create()
                .withQuery(userGroupQuery)
                .withSelfLink(selfLink)
                .build();

        return userGroupState;
    }

    /**
     * Authorization related operations should take effect on all replicas, before they
     * complete. This method adds a special header that sets the quorum level to all
     * available nodes, avoiding a race where a client can reach a node that has not yet
     * received latest authorization changes, even if it received success from this auth
     * helper class
     */
    public static void addReplicationFactor(Operation op) {
        op.addRequestHeader(Operation.REPLICATION_QUORUM_HEADER,
                Operation.REPLICATION_QUORUM_HEADER_VALUE_ALL);
    }

    public static String buildUriWithWildcard(String link) {
        if (link.endsWith(UriUtils.URI_PATH_CHAR)) {
            return link + UriUtils.URI_WILDCARD_CHAR;
        }
        return link + UriUtils.URI_PATH_CHAR + UriUtils.URI_WILDCARD_CHAR;
    }

    public static String getAuthorizedUserId(AuthorizationContext authContext) {
        String userLink = getAuthorizedUserLink(authContext);
        if (userLink == null) {
            return null;
        }

        return UriUtils.getLastPathSegment(userLink);
    }

    public static String getAuthorizedUserLink(AuthorizationContext authContext) {
        if (authContext == null || authContext.getClaims() == null
                || authContext.getClaims().getSubject() == null) {
            return null;
        }
        return authContext.getClaims().getSubject();
    }

}
