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

import static com.vmware.admiral.auth.util.PrincipalUtil.fromLocalPrincipalToPrincipal;
import static com.vmware.admiral.auth.util.PrincipalUtil.fromPrincipalToLocalPrincipal;
import static com.vmware.admiral.auth.util.PrincipalUtil.fromQueryResultToPrincipalList;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNullOrEmpty;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.PrincipalProvider;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.authn.AuthenticationRequest;
import com.vmware.xenon.services.common.authn.AuthenticationRequest.AuthenticationRequestType;
import com.vmware.xenon.services.common.authn.BasicAuthenticationService;
import com.vmware.xenon.services.common.authn.BasicAuthenticationUtils;

public class LocalPrincipalProvider implements PrincipalProvider {
    private static final String EXPAND_QUERY_KEY = "expand";
    private static final String FILTER_QUERY_KEY = "$filter";

    private Service service;

    @Override
    public void init(Service service) {
        this.service = service;
    }

    @Override
    public DeferredResult<Principal> getPrincipal(Operation op, String principalId) {
        assertNotNullOrEmpty(principalId, "principalId");

        Operation get = Operation.createGet(service,
                UriUtils.buildUriPath(LocalPrincipalFactoryService.SELF_LINK, principalId));

        service.setAuthorizationContext(get, service.getSystemAuthorizationContext());

        return service.sendWithDeferredResult(get, LocalPrincipalState.class)
                .thenApply((s) -> fromLocalPrincipalToPrincipal(s));
    }

    @Override
    public DeferredResult<List<Principal>> getPrincipals(Operation op, String criteria) {
        String filterQuery = buildFilterBasedOnCriteria(criteria);

        URI uri = UriUtils.buildUri(service.getHost(), LocalPrincipalFactoryService.SELF_LINK);
        uri = UriUtils.extendUriWithQuery(uri, EXPAND_QUERY_KEY, Boolean.TRUE.toString());
        uri = UriUtils.extendUriWithQuery(uri, FILTER_QUERY_KEY, filterQuery);

        Operation get = Operation.createGet(uri);

        service.setAuthorizationContext(get, service.getSystemAuthorizationContext());

        return service.sendWithDeferredResult(get, ServiceDocumentQueryResult.class)
                .thenApply((q) -> fromQueryResultToPrincipalList(q));
    }

    @Override
    public DeferredResult<Principal> createPrincipal(Operation op, Principal principal) {
        LocalPrincipalState stateToCreate = fromPrincipalToLocalPrincipal(principal);

        Operation post = Operation.createPost(service, LocalPrincipalFactoryService.SELF_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(stateToCreate);

        service.setAuthorizationContext(post, service.getSystemAuthorizationContext());

        return service.sendWithDeferredResult(post, LocalPrincipalState.class)
                .thenApply((s) -> fromLocalPrincipalToPrincipal(s));
    }

    @Override
    public DeferredResult<Principal> updatePrincipal(Operation op, Principal principal) {
        LocalPrincipalState stateToPatch = fromPrincipalToLocalPrincipal(principal);

        Operation patch = Operation.createPatch(service,
                UriUtils.buildUriPath(LocalPrincipalFactoryService.SELF_LINK, stateToPatch.id))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(stateToPatch);

        service.setAuthorizationContext(patch, service.getSystemAuthorizationContext());

        return service.sendWithDeferredResult(patch, LocalPrincipalState.class)
                .thenApply((s) -> fromLocalPrincipalToPrincipal(s));
    }

    @Override
    public DeferredResult<Principal> deletePrincipal(Operation op, String principalId) {
        assertNotNullOrEmpty(principalId, "principalId");

        Operation delete = Operation.createDelete(service,
                UriUtils.buildUriPath(LocalPrincipalFactoryService.SELF_LINK, principalId));

        service.setAuthorizationContext(delete, service.getSystemAuthorizationContext());

        return service.sendWithDeferredResult(delete, LocalPrincipalState.class)
                .thenApply((s) -> fromLocalPrincipalToPrincipal(s));
    }

    @Override
    public DeferredResult<Set<String>> getAllGroupsForPrincipal(Operation op, String principalId) {
        assertNotNullOrEmpty(principalId, "principalId");

        return getDirectlyAssignedGroupsForPrincipal(principalId)
                .thenCompose(groups -> getIndirectlyAssignedGroupsForPrincipal(groups, null, null));
    }

    @Override
    public DeferredResult<Principal> getPrincipalByCredentials(Operation get, String principalId,
            String password) {

        return tryLogin(principalId, password)
                .thenCompose(isAuthenticated -> {
                    if (!isAuthenticated) {
                        return DeferredResult.failed(new IllegalAccessError("Unable to "
                                + "authenticate for " + principalId));
                    }
                    return getPrincipal(get, principalId);
                });
    }

    private DeferredResult<Boolean> tryLogin(String principalId, String password) {
        String auth = BasicAuthenticationUtils.constructBasicAuth(principalId, password);

        AuthenticationRequest body = new AuthenticationRequest();
        body.requestType = AuthenticationRequestType.LOGIN;

        Operation login = Operation.createPost(service.getHost(),
                BasicAuthenticationService.SELF_LINK)
                .setReferer(service.getHost().getUri())
                .setBody(body)
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, auth);

        return service.sendWithDeferredResult(login)
                .thenApply(op -> new Pair<>(op, null))
                .exceptionally(ex -> new Pair<>(null, ex))
                .thenApply(pair -> {
                    if (pair.right != null) {
                        return false;
                    }
                    return true;
                });
    }

    private DeferredResult<Set<String>> getDirectlyAssignedGroupsForPrincipal(String principalId) {
        String principalSelfLink = UriUtils.buildUriPath(LocalPrincipalFactoryService.SELF_LINK,
                principalId);

        DeferredResult<Set<String>> result = new DeferredResult<>();

        Set<String> groups = new HashSet<>();

        Query query = Query.Builder.create()
                .addInCollectionItemClause(LocalPrincipalState.FIELD_NAME_GROUP_MEMBERS_LINKS,
                        Collections.singleton(principalSelfLink))
                .build();

        QueryTask queryTask = QueryUtil.buildQuery(LocalPrincipalState.class, true, query);

        QueryUtil.addExpandOption(queryTask);

        new ServiceDocumentQuery<>(service.getHost(), LocalPrincipalState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        result.fail(r.getException());
                    } else if (r.hasResult()) {
                        groups.add(r.getResult().id);
                    } else {
                        result.complete(groups);
                    }
                });

        return result;
    }

    private DeferredResult<Set<String>> getIndirectlyAssignedGroupsForPrincipal(
            Set<String> groupsToCheck, Set<String> foundGroups, Set<String> alreadyChecked) {

        if (groupsToCheck == null || groupsToCheck.isEmpty()) {
            return DeferredResult.completed(new HashSet<>());
        }

        if (foundGroups == null) {
            Set<String> fg = new HashSet<>();
            fg.addAll(groupsToCheck);
            return getIndirectlyAssignedGroupsForPrincipal(groupsToCheck, fg, alreadyChecked);
        }

        if (alreadyChecked == null) {
            return getIndirectlyAssignedGroupsForPrincipal(groupsToCheck, foundGroups,
                    new HashSet<>());
        }

        DeferredResult<Set<String>> result = new DeferredResult<>();

        List<String> groupsToCheckLinks = groupsToCheck.stream()
                .map(g -> UriUtils.buildUriPath(LocalPrincipalFactoryService.SELF_LINK, g))
                .collect(Collectors.toList());

        Query query = Query.Builder.create()
                .addInCollectionItemClause(LocalPrincipalState.FIELD_NAME_GROUP_MEMBERS_LINKS,
                        groupsToCheckLinks)
                .build();

        QueryTask queryTask = QueryUtil.buildQuery(LocalPrincipalState.class, true, query);
        QueryUtil.addExpandOption(queryTask);

        groupsToCheck.clear();

        new ServiceDocumentQuery<>(service.getHost(), LocalPrincipalState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        result.fail(r.getException());
                    } else if (r.hasResult()) {
                        foundGroups.add(r.getResult().id);
                        groupsToCheck.add(r.getResult().id);
                    } else {
                        result.complete(foundGroups);
                    }
                });

        return result.thenCompose(resultGroups -> {
            foundGroups.addAll(resultGroups);
            if (groupsToCheck.isEmpty() || alreadyChecked.containsAll(groupsToCheck)) {
                alreadyChecked.addAll(groupsToCheck);
                return DeferredResult.completed(resultGroups);
            }
            alreadyChecked.addAll(groupsToCheck);
            return getIndirectlyAssignedGroupsForPrincipal(groupsToCheck, foundGroups,
                    alreadyChecked);
        });
    }

    private static String buildFilterBasedOnCriteria(String criteria) {
        return String.format("name eq '*%s*' or email eq '*%s*' or id eq '*%s*'",
                criteria, criteria, criteria);
    }

}
