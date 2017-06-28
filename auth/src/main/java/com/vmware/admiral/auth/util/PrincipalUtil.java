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

import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.Principal.PrincipalType;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.idm.local.LocalPrincipalFactoryService;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalType;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

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

        return principal;
    }

    public static List<Principal> fromQueryResultToPrincipalList(
            ServiceDocumentQueryResult queryResult) {

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

        return state;
    }

    public static Principal copyPrincipalData(Principal src, Principal dst) {
        if (src == null) {
            return null;
        }
        if (dst == null) {
            dst = new Principal();
        }
        dst.id = src.id;
        dst.email = src.email;
        dst.type = src.type;
        dst.name = src.name;
        dst.password = src.password;

        return dst;
    }

    public static DeferredResult<Principal> getPrincipal(ServiceHost host, String principalId) {
        Operation getPrincipalOp = Operation.createGet(host, UriUtils.buildUriPath(
                PrincipalService.SELF_LINK, principalId))
                .setReferer(host.getUri());
        return host.sendWithDeferredResult(getPrincipalOp, Principal.class);
    }

    public static URI buildLocalPrincipalStateSelfLink(ServiceHost host, String id) {
        return UriUtils.buildUri(host, LocalPrincipalFactoryService.SELF_LINK + "/" + id);
    }

}
