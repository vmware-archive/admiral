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

import static com.vmware.admiral.common.util.AssertUtil.assertNotNullOrEmpty;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.logging.Level;

import com.vmware.admiral.auth.idm.AuthConfigProvider;
import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.SessionService;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.image.service.PopularImagesService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
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
import com.vmware.xenon.services.common.UserService.UserState;

public class AuthUtil {
    public static final String DEFAULT_IDENTIFIER = "default";

    public static final String DEFAULT_CLOUD_ADMINS = AuthRole.CLOUD_ADMIN
            .buildRoleWithSuffix(DEFAULT_IDENTIFIER);

    public static final String CLOUD_ADMINS_RESOURCE_GROUP_LINK = UriUtils
            .buildUriPath(ResourceGroupService.FACTORY_LINK, AuthRole.CLOUD_ADMIN.getSuffix());

    public static final String CLOUD_ADMINS_USER_GROUP_LINK = UriUtils
            .buildUriPath(UserGroupService.FACTORY_LINK, AuthRole.CLOUD_ADMIN.getSuffix());

    public static final String DEFAULT_CLOUD_ADMINS_ROLE_LINK = UriUtils
            .buildUriPath(RoleService.FACTORY_LINK, DEFAULT_CLOUD_ADMINS);

    public static final String DEFAULT_BASIC_USERS = AuthRole.BASIC_USER
            .buildRoleWithSuffix(DEFAULT_IDENTIFIER);

    public static final String BASIC_USERS_RESOURCE_GROUP_LINK = UriUtils
            .buildUriPath(ResourceGroupService.FACTORY_LINK, AuthRole.BASIC_USER.getSuffix());

    public static final String BASIC_USERS_USER_GROUP_LINK = UriUtils
            .buildUriPath(UserGroupService.FACTORY_LINK, AuthRole.BASIC_USER.getSuffix());

    public static final String DEFAULT_BASIC_USERS_ROLE_LINK = UriUtils
            .buildUriPath(RoleService.FACTORY_LINK, DEFAULT_BASIC_USERS);

    public static final String DEFAULT_BASIC_USERS_EXTENDED = AuthRole.BASIC_USER_EXTENDED
            .buildRoleWithSuffix(DEFAULT_IDENTIFIER);

    public static final String BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK = UriUtils
            .buildUriPath(ResourceGroupService.FACTORY_LINK,
                    AuthRole.BASIC_USER_EXTENDED.getSuffix());

    public static final String DEFAULT_BASIC_USERS_EXTENDED_ROLE_LINK = UriUtils
            .buildUriPath(RoleService.FACTORY_LINK, DEFAULT_BASIC_USERS_EXTENDED);

    public static final Map<AuthRole, String> MAP_ROLE_TO_SYSTEM_USER_GROUP;

    public static final Map<AuthRole, String> MAP_ROLE_TO_SYSTEM_RESOURCE_GROUP;

    public static final String FIELD_NAME_USER_GROUP_LINK = "userGroupLinks";

    public static final String USERS_QUERY_NO_USERS_SELF_LINK = "__no-users";

    public static final String LOCAL_USERS_FILE = "localUsers";

    public static final String AUTH_CONFIG_FILE = "authConfig";

    private static final String PREFERRED_PROVIDER_PACKAGE = "com.vmware.admiral.auth.idm.psc";

    static {
        // map roles to system user groups
        HashMap<AuthRole, String> rolesToUserGroup = new HashMap<>();
        rolesToUserGroup.put(AuthRole.BASIC_USER, BASIC_USERS_USER_GROUP_LINK);
        // all users in the basic user group are also extended basic users
        rolesToUserGroup.put(AuthRole.BASIC_USER_EXTENDED, BASIC_USERS_USER_GROUP_LINK);
        rolesToUserGroup.put(AuthRole.CLOUD_ADMIN, CLOUD_ADMINS_USER_GROUP_LINK);
        MAP_ROLE_TO_SYSTEM_USER_GROUP = Collections.unmodifiableMap(rolesToUserGroup);

        HashMap<AuthRole, String> rolesToResourceGroup = new HashMap<>();
        // map roles to system resource groups
        rolesToResourceGroup.put(AuthRole.BASIC_USER, BASIC_USERS_RESOURCE_GROUP_LINK);
        rolesToResourceGroup.put(AuthRole.BASIC_USER_EXTENDED,
                BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK);
        rolesToResourceGroup.put(AuthRole.CLOUD_ADMIN, CLOUD_ADMINS_RESOURCE_GROUP_LINK);
        MAP_ROLE_TO_SYSTEM_RESOURCE_GROUP = Collections.unmodifiableMap(rolesToResourceGroup);
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

    public static UserGroupState buildCloudAdminsUserGroup() {
        String id = AuthRole.CLOUD_ADMIN.getSuffix();

        UserGroupState userGroupState = buildUserGroupState(id);

        return userGroupState;
    }

    public static ResourceGroupState buildCloudAdminsResourceGroup() {
        Query resourceGroupQuery = Query.Builder
                .create()
                .setTerm(ServiceDocument.FIELD_NAME_SELF_LINK, UriUtils.URI_WILDCARD_CHAR,
                        QueryTerm.MatchType.WILDCARD)
                .build();

        ResourceGroupState resourceGroupState = buildResourceGroupState(resourceGroupQuery,
                CLOUD_ADMINS_RESOURCE_GROUP_LINK);

        return resourceGroupState;
    }

    public static RoleState buildCloudAdminsRole(String identifier, String userGroupLink) {
        String id = AuthRole.CLOUD_ADMIN.buildRoleWithSuffix(identifier);
        String selfLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, id);
        EnumSet<Action> verbs = EnumSet.allOf(Action.class);

        RoleState roleState = buildRoleState(selfLink, userGroupLink,
                CLOUD_ADMINS_RESOURCE_GROUP_LINK, verbs);

        return roleState;
    }

    public static UserGroupState buildBasicUsersUserGroup() {
        String id = AuthRole.BASIC_USER.getSuffix();

        UserGroupState userGroupState = buildUserGroupState(id);

        return userGroupState;
    }

    public static ResourceGroupState buildBasicUsersResourceGroup() {
        Query resourceGroupQuery = Query.Builder
                .create()
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        buildUriWithWildcard(ConfigurationFactoryService.SELF_LINK),
                        MatchType.WILDCARD, Occurance.SHOULD_OCCUR)
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        SessionService.SELF_LINK,
                        MatchType.TERM, Occurance.SHOULD_OCCUR)
                // TODO: Currently this breaks the UI. Remove this query, once
                // this call is skipped for basic user.
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        PopularImagesService.SELF_LINK,
                        MatchType.TERM, Occurance.SHOULD_OCCUR)
                // TODO: Remove this query, once
                // the call is skipped for basic user.
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        ManagementUriParts.NOTIFICATIONS,
                        MatchType.TERM, Occurance.SHOULD_OCCUR)
                .build();

        ResourceGroupState resourceGroupState = buildResourceGroupState(resourceGroupQuery,
                BASIC_USERS_RESOURCE_GROUP_LINK);

        return resourceGroupState;
    }

    public static RoleState buildBasicUsersRole(String identifier, String userGroupLink) {
        String id = AuthRole.BASIC_USER.buildRoleWithSuffix(identifier);
        String selfLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, id);
        EnumSet<Action> verbs = EnumSet.of(Action.GET);

        RoleState roleState = buildRoleState(selfLink, userGroupLink,
                BASIC_USERS_RESOURCE_GROUP_LINK, verbs);

        return roleState;
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
        String id = AuthRole.BASIC_USER_EXTENDED.buildRoleWithSuffix(identifier);
        String selfLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, id);

        EnumSet<Action> verbs = EnumSet.of(Action.GET, Action.POST);

        RoleState roleState = buildRoleState(selfLink, userGroupLink,
                BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK, verbs);

        return roleState;
    }

    public static UserGroupState buildProjectAdminsUserGroup(String projectId) {
        String id = AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(projectId);

        UserGroupState userGroupState = buildUserGroupState(id);

        return userGroupState;
    }

    public static UserGroupState buildProjectMembersUserGroup(String projectId) {
        String id = AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(projectId);

        UserGroupState userGroupState = buildUserGroupState(id);

        return userGroupState;
    }

    public static UserGroupState buildProjectViewersUserGroup(String projectId) {
        String id = AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(projectId);

        UserGroupState userGroupState = buildUserGroupState(id);
        return userGroupState;
    }

    public static UserGroupState buildProjectMembersUserGroupByGroupId(String groupId) {

        UserGroupState userGroupState = buildUserGroupState(groupId);
        return userGroupState;
    }

    public static ResourceGroupState buildProjectResourceGroup(String projectId) {
        String projectSelfLink = UriUtils.buildUriPath(ProjectFactoryService.SELF_LINK, projectId);
        Query resourceGroupQuery = Query.Builder
                .create()
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK, projectSelfLink,
                        Occurance.SHOULD_OCCUR)
                .addCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS, projectSelfLink,
                        Occurance.SHOULD_OCCUR)
                .build();

        ResourceGroupState resourceGroupState = buildResourceGroupState(projectId,
                resourceGroupQuery);

        return resourceGroupState;
    }

    public static RoleState buildProjectAdminsRole(String projectId, String userGroupLink,
            String resourceGroupLink) {
        EnumSet<Action> verbs = EnumSet.allOf(Action.class);
        return buildProjectRole(AuthRole.PROJECT_ADMIN, verbs, projectId, userGroupLink,
                resourceGroupLink);
    }

    public static RoleState buildProjectMembersRole(String projectId, String userGroupLink,
            String resourceGroupLink) {
        EnumSet<Action> verbs = EnumSet.of(Action.GET);
        return buildProjectRole(AuthRole.PROJECT_MEMBER, verbs, projectId, userGroupLink,
                resourceGroupLink);
    }

    public static RoleState buildProjectViewersRole(String projectId, String userGroupLink,
            String resourceGroupLink) {
        // TODO currently this is the same as the members role. Probably needs to be tweaked or
        // another resource group needs to be introduced
        EnumSet<Action> verbs = EnumSet.of(Action.GET);
        return buildProjectRole(AuthRole.PROJECT_VIEWER, verbs, projectId, userGroupLink,
                resourceGroupLink);
    }

    public static RoleState buildProjectRole(AuthRole role, EnumSet<Action> verbs, String projectId,
            String userGroupLink, String resourceGroupLink) {
        String id = role.buildRoleWithSuffix(projectId, Service.getId(userGroupLink));

        String selfLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, id);

        RoleState roleState = buildRoleState(selfLink, userGroupLink, resourceGroupLink, verbs);

        return roleState;
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

    public static ResourceGroupState buildResourceGroupState(String identifier, Query query) {
        String selfLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, identifier);

        ResourceGroupState resourceGroupState = ResourceGroupState.Builder
                .create()
                .withQuery(query)
                .withSelfLink(selfLink)
                .build();

        return resourceGroupState;
    }

    public static ResourceGroupState buildResourceGroupState(Query query, String selfLink) {

        ResourceGroupState resourceGroupState = ResourceGroupState.Builder
                .create()
                .withQuery(query)
                .withSelfLink(selfLink)
                .build();

        return resourceGroupState;
    }

    public static RoleState buildRoleState(String selfLink, String userGroupLink,
            String resourceGroupLink, EnumSet<Action> verbs) {
        RoleState roleState = RoleState.Builder
                .create()
                .withPolicy(Policy.ALLOW)
                .withSelfLink(selfLink)
                .withVerbs(verbs)
                .withResourceGroupLink(resourceGroupLink)
                .withUserGroupLink(userGroupLink)
                .build();

        return roleState;
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

    /**
     * TODO Currently all authorized non-guest users are devOpsAdmins. Needs to be changed after
     * roles are introduced.
     */
    public static boolean isDevOpsAdmin(Operation op) {
        return !OperationUtil.isGuestUser(op);
    }

    public static Query buildQueryForUsers(String userGroupLink) {
        Query resultQuery = new Query();

        Query kindClause = QueryUtil.createKindClause(UserState.class)
                .setOccurance(Occurance.MUST_OCCUR);

        Query matchUsers = Query.Builder.create()
                .addInCollectionItemClause(FIELD_NAME_USER_GROUP_LINK,
                        Collections.singletonList(userGroupLink), Occurance.MUST_OCCUR)
                .build();

        resultQuery.addBooleanClause(kindClause);
        resultQuery.addBooleanClause(matchUsers);
        return resultQuery;
    }

    public static Query buildUsersQuery(List<String> userLinks) {
        Query resultQuery = new Query();

        Query kindClause = QueryUtil.createKindClause(UserState.class)
                .setOccurance(Occurance.MUST_OCCUR);

        Query documentLinkClause = new Query().setOccurance(Occurance.MUST_OCCUR);

        if (userLinks == null || userLinks.isEmpty()) {
            // make a query that will match no users
            documentLinkClause.setTermMatchType(MatchType.TERM)
                    .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                    .setTermMatchValue(USERS_QUERY_NO_USERS_SELF_LINK);
        } else {
            userLinks.stream().map((documentLink) -> {
                return new Query().setTermPropertyName(UserState.FIELD_NAME_SELF_LINK)
                        .setTermMatchType(MatchType.TERM)
                        .setOccurance(Occurance.SHOULD_OCCUR)
                        .setTermMatchValue(documentLink);
            }).forEach(documentLinkClause::addBooleanClause);
        }

        resultQuery.addBooleanClause(kindClause);
        resultQuery.addBooleanClause(documentLinkClause);
        return resultQuery;
    }

    public static final Function<Claims, String> USER_LINK_BUILDER = AuthUtil
            .getPreferredProvider(AuthConfigProvider.class)
            .getAuthenticationServiceUserLinkBuilder();

    public static String buildUserServicePathFromPrincipalId(String principalId) {
        Claims claims = new Claims.Builder().setSubject(principalId).getResult();
        return USER_LINK_BUILDER.apply(claims);
    }

    /**
     * Extract data from RoleState ID, which RoleState was build as duplicate RoleState when
     * assigning groups to project roles, with ID following the pattern:
     * projectId_groupdId_roleSuffix
     *
     * @param roleStateId
     *
     * @return String[] with 3 elements, the first one is the project id, the second is the group
     *         id and third is the project role suffix.
     */
    public static String[] extractDataFromRoleStateId(String roleStateId) {
        assertNotNullOrEmpty(roleStateId, "roleStateId");

        int firstSeparatorIndex = roleStateId.indexOf(AuthRole.SUFFIX_SEPARATOR);
        int lastSeparatorIndex = roleStateId.lastIndexOf(AuthRole.SUFFIX_SEPARATOR);

        if (firstSeparatorIndex == -1 || lastSeparatorIndex == -1
                || firstSeparatorIndex == lastSeparatorIndex) {
            throw new IllegalArgumentException("Provided role state id is not following the "
                    + "pattern: projectId_groupdId_roleSuffix - " + roleStateId);
        }

        String projectId = roleStateId.substring(0, firstSeparatorIndex);
        String groupId = roleStateId.substring(firstSeparatorIndex + 1, lastSeparatorIndex);
        String roleSuffix = roleStateId.substring(lastSeparatorIndex + 1, roleStateId.length());

        return new String[] { projectId, groupId, roleSuffix };
    }

}
