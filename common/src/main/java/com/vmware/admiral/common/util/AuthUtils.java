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

package com.vmware.admiral.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.UserService.UserState;

public class AuthUtils {
    public static final String FIELD_NAME_USER_GROUP_LINK = "userGroupLinks";

    protected static final String USERS_QUERY_NO_USERS_SELF_LINK = "__no-users";

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

    /**
     * TODO Currently all authorized non-guest users are devOpsAdmins. Needs to be changed after roles are introduced.
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

    public static Query buildQueryForUsers(String... userLinks) {
        return buildUsersQuery(
                userLinks == null ? new ArrayList<>(0) : Arrays.asList(userLinks));
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

    public static String getUserStateDocumentLink(String principalId) {
        return UriUtils.buildUriPath(UserService.FACTORY_LINK, principalId);
    }

}
