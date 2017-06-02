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

import static com.vmware.admiral.auth.util.PrincipalUtil.buildLocalPrincipalStateSelfLink;
import static com.vmware.admiral.auth.util.PrincipalUtil.fromLocalPrincipalToPrincipal;
import static com.vmware.admiral.auth.util.PrincipalUtil.fromPrincipalToLocalPrincipal;
import static com.vmware.admiral.auth.util.PrincipalUtil.fromQueryResultToPrincipalList;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNullOrEmpty;

import java.net.URI;
import java.util.List;

import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.PrincipalProvider;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class LocalPrincipalProvider implements PrincipalProvider {
    private static final String EXPAND_QUERY_KEY = "expand";
    private static final String FILTER_QUERY_KEY = "$filter";
    private static final String EMAIL_FILTER_QUERY_FORMAT = "email eq '*%s*'";

    private ServiceHost host;

    public void setServiceHost(ServiceHost host) {
        this.host = host;
    }

    @Override
    public DeferredResult<Principal> getPrincipal(String principalId) {
        assertNotNullOrEmpty(principalId, "principalId");
        URI uri = buildLocalPrincipalStateSelfLink(host, principalId);

        Operation get = Operation.createGet(uri)
                .setReferer(host.getUri());

        return host.sendWithDeferredResult(get, LocalPrincipalState.class)
                .thenApply((s) -> fromLocalPrincipalToPrincipal(s));
    }

    @Override
    public DeferredResult<List<Principal>> getPrincipals(String criteria) {
        String filterQuery = String.format(EMAIL_FILTER_QUERY_FORMAT, criteria);

        URI uri = UriUtils.buildUri(host, LocalPrincipalFactoryService.SELF_LINK);
        uri = UriUtils.extendUriWithQuery(uri, EXPAND_QUERY_KEY, Boolean.TRUE.toString());
        uri = UriUtils.extendUriWithQuery(uri, FILTER_QUERY_KEY, filterQuery);

        Operation get = Operation.createGet(uri)
                .setReferer(host.getUri());

        return host.sendWithDeferredResult(get, ServiceDocumentQueryResult.class)
                .thenApply((q) -> fromQueryResultToPrincipalList(q));
    }

    @Override
    public DeferredResult<Principal> createPrincipal(Principal principal) {
        LocalPrincipalState stateToCreate = fromPrincipalToLocalPrincipal(principal);

        URI uri = UriUtils.buildUri(host, LocalPrincipalFactoryService.SELF_LINK);

        Operation post = Operation.createPost(uri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(stateToCreate)
                .setReferer(host.getUri());

        return host.sendWithDeferredResult(post, LocalPrincipalState.class)
                .thenApply((s) -> fromLocalPrincipalToPrincipal(s));
    }

    @Override
    public DeferredResult<Principal> updatePrincipal(Principal principal) {
        LocalPrincipalState stateToPatch = fromPrincipalToLocalPrincipal(principal);

        URI uri = buildLocalPrincipalStateSelfLink(host, stateToPatch.id);

        Operation post = Operation.createPatch(uri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(stateToPatch)
                .setReferer(host.getUri());

        return host.sendWithDeferredResult(post, LocalPrincipalState.class)
                .thenApply((s) -> fromLocalPrincipalToPrincipal(s));
    }

    @Override
    public DeferredResult<Principal> deletePrincipal(String principalId) {
        URI uri = buildLocalPrincipalStateSelfLink(host, principalId);

        Operation delete = Operation.createDelete(uri)
                .setReferer(host.getUri());

        return host.sendWithDeferredResult(delete, LocalPrincipalState.class)
                .thenApply((s) -> fromLocalPrincipalToPrincipal(s));
    }

}
