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

package com.vmware.admiral.auth.idm;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.idm.local.LocalPrincipalProvider;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class PrincipalService extends StatelessService implements PrincipalProvider {
    public static final String SELF_LINK = ManagementUriParts.AUTH_PRINCIPALS;

    public static final String PRINCIPAL_ID_PATH_SEGMENT = "principalId";
    public static final String PRINCIPAL_ID_PATH_SEGMENT_TEMPLATE = SELF_LINK + "/{principalId}";

    public static final String CRITERIA_QUERY = "criteria";

    private PrincipalProvider provider;

    public static class PrincipalResponse {

        public String principalId;

        public PrincipalResponse() {
        }

        public PrincipalResponse(String principalId) {
            this.principalId = principalId;
        }

    }

    public PrincipalService() {
        super();
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        provider = AuthUtil.getPreferredProvider(PrincipalProvider.class);

        // TODO - replace it with some host-based init method perhaps
        if (provider instanceof LocalPrincipalProvider) {
            ((LocalPrincipalProvider) provider).setServiceHost(getHost());
        }

        startPost.complete();
    }

    @Override
    public void handleGet(Operation get) {
        Map<String, String> queryParams = UriUtils.parseUriQueryParams(get.getUri());
        Map<String, String> pathSegmentParams = UriUtils.parseUriPathSegments(get.getUri(),
                PRINCIPAL_ID_PATH_SEGMENT_TEMPLATE);

        String principalId = pathSegmentParams.get(PRINCIPAL_ID_PATH_SEGMENT);
        String criteria = queryParams.get(CRITERIA_QUERY);

        if (principalId != null && !principalId.isEmpty()) {
            handleSearchById(principalId, get);
        } else if (criteria != null && !criteria.isEmpty()) {
            handleSearchByCriteria(criteria, get);
        } else {
            get.fail(new IllegalArgumentException("Provide either criteria or principalId to "
                    + "search for."));
        }
    }

    private void handleSearchById(String principalId, Operation get) {
        getPrincipal(principalId, (principal, ex) -> {
            if (ex != null) {
                get.fail(ex);
                return;
            }
            PrincipalResponse response = new PrincipalResponse(principal);
            get.setBody(response).complete();
        });
    }

    private void handleSearchByCriteria(String criteria, Operation get) {
        getPrincipals(criteria, (principals, ex) -> {
            if (ex != null) {
                get.fail(ex);
                return;
            }
            List<PrincipalResponse> response = principals.stream()
                    .map(PrincipalResponse::new)
                    .collect(Collectors.toList());
            get.setBody(response).complete();
        });
    }

    @Override
    public void getPrincipal(String principalId, BiConsumer<String, Throwable> callback) {
        provider.getPrincipal(principalId, callback);
    }

    @Override
    public void getPrincipals(String criteria, BiConsumer<List<String>, Throwable> callback) {
        provider.getPrincipals(criteria, callback);
    }

}
