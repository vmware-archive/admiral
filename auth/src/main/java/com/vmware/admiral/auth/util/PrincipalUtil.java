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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.Principal.PrincipalType;
import com.vmware.admiral.auth.idm.local.LocalPrincipalFactoryService;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalType;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.UserService;

public class PrincipalUtil {

    public static Principal fromLocalPrincipalToPrincipal(LocalPrincipalState state) {

        if (state == null) {
            return null;
        }

        Principal principal = new Principal();
        principal.email = state.email;
        principal.name = state.name;
        principal.id = state.id;
        principal.password = state.password;
        principal.type = PrincipalType.valueOf(state.type.name());

        if (state.type == LocalPrincipalType.GROUP && state.groupMembersLinks != null && !state
                .groupMembersLinks.isEmpty()) {
            principal.groupMembers = state.groupMembersLinks.stream()
                    .map(Service::getId)
                    .collect(Collectors.toList());
        }

        return principal;
    }

    public static List<Principal> fromQueryResultToPrincipalList(ServiceDocumentQueryResult
            queryResult) {

        List<Principal> principals = new ArrayList<>();

        for (Object serializedState : queryResult.documents.values()) {
            LocalPrincipalState state = Utils.fromJson(serializedState, LocalPrincipalState.class);
            Principal principal = fromLocalPrincipalToPrincipal(state);
            principals.add(principal);
        }

        return principals;

    }

    public static LocalPrincipalState fromPrincipalToLocalPrincipal(Principal principal) {
        if (principal == null) {
            return null;
        }

        LocalPrincipalState state = new LocalPrincipalState();
        state.email = principal.email;
        state.id = principal.id;
        state.password = principal.password;
        state.name = principal.name;
        state.type = LocalPrincipalType.valueOf(principal.type.name());

        if (principal.type == PrincipalType.GROUP && principal.groupMembers != null
                && !principal.groupMembers.isEmpty()) {
            state.groupMembersLinks = principal.groupMembers.stream()
                    .map(m -> LocalPrincipalFactoryService.SELF_LINK + "/" + m)
                    .collect(Collectors.toList());
        }

        return state;
    }

    public static URI buildLocalPrincipalStateSelfLink(ServiceHost host, String id) {
        return UriUtils.buildUri(host, LocalPrincipalFactoryService.SELF_LINK + "/" + id);
    }

    public static URI buildUserStateSelfLinks(ServiceHost host, String id) {
        return UriUtils.buildUri(host, UserService.FACTORY_LINK + "/" + id);
    }
}
