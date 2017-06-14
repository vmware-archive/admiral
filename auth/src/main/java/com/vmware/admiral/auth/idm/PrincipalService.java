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
import java.util.regex.Pattern;

import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.idm.local.LocalPrincipalProvider;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.auth.util.SecurityContextUtil;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class PrincipalService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.AUTH_PRINCIPALS;
    public static final String CRITERIA_QUERY = "criteria";
    public static final String SECURITY_CONTEXT_SUFFIX = "/security-context";
    public static final String ROLES_SUFFIX = "/roles";

    private static final String PRINCIPAL_ID_PATH_SEGMENT = "principalId";

    private static final String TEMPLATE_PRINCIPAL_ID_PATH_SEGMENT = UriUtils
            .buildUriPath(SELF_LINK,
                    String.format("{%s}", PRINCIPAL_ID_PATH_SEGMENT));
    private static final String TEMPLATE_PRINCIPAL_SECURITY_CONTEXT = UriUtils
            .buildUriPath(SELF_LINK,
                    String.format("{%s}", PRINCIPAL_ID_PATH_SEGMENT), SECURITY_CONTEXT_SUFFIX);

    /**
     * Matches /auth/idm/principals
     */
    private static final Pattern PATTERN_PRINCIPLE_SERVICE_BASE = Pattern
            .compile(String.format("^%s\\/?$", SELF_LINK.replaceAll("/", "\\\\/")));

    /**
     * Matches /auth/idm/principals/{principal-id}
     */
    private static final Pattern PATTERN_PRINCIPAL_GET_BY_ID = Pattern
            .compile(String.format("^%s\\/[^\\/]+\\/?$", SELF_LINK.replaceAll("/", "\\\\/")));

    /**
     * Matches /auth/idm/principals/{principal-id}/security-context
     */
    private static final Pattern PATTERN_PRINCIPAL_SECURITY_CONTEXT = Pattern
            .compile(String.format("^%s\\/[^\\/]+%s\\/?$", SELF_LINK.replaceAll("/", "\\\\/"),
                    SECURITY_CONTEXT_SUFFIX));

    /**
     * Matches /auth/idm/principals/{principal-id}/roles
     */
    private static final Pattern PATTERN_PRINCIPAL_ROLES = Pattern
            .compile(String.format("^%s\\/[^\\/]+%s\\/?$", SELF_LINK.replaceAll("/", "\\\\/"),
                    ROLES_SUFFIX));

    private PrincipalProvider provider;

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
        if (isPrincipalByIdRequest(get)) {
            handleSearchById(UriUtils.parseUriPathSegments(get.getUri(),
                    TEMPLATE_PRINCIPAL_ID_PATH_SEGMENT).get(PRINCIPAL_ID_PATH_SEGMENT), get);
        } else if (isPrincipalByCriteriaRequest(get)) {
            handleSearchByCriteria(UriUtils.parseUriQueryParams(get.getUri())
                    .get(CRITERIA_QUERY), get);
        } else if (isSecurityContextRequest(get)) {
            handleGetSecurityContext(get);
        } else {
            get.fail(new IllegalArgumentException(
                    "Provide either criteria or principalId to search for."));
        }
    }

    private void handleGetSecurityContext(Operation get) {
        String principalId = UriUtils
                .parseUriPathSegments(get.getUri(), TEMPLATE_PRINCIPAL_SECURITY_CONTEXT)
                .get(PRINCIPAL_ID_PATH_SEGMENT);
        SecurityContextUtil.getSecurityContext(this, principalId)
                .thenAccept((context) -> {
                    get.setBody(context);
                    get.complete();
                }).exceptionally((ex) -> {
                    logWarning("Failed to retrieve security context for user %s: %s",
                            principalId, Utils.toString(ex));
                    get.fail(ex);
                    return null;
                });
    }

    private boolean isPrincipalByIdRequest(Operation op) {
        return PATTERN_PRINCIPAL_GET_BY_ID.matcher(op.getUri().getPath()).matches();
    }

    private boolean isPrincipalByCriteriaRequest(Operation op) {
        if (!PATTERN_PRINCIPLE_SERVICE_BASE.matcher(op.getUri().getPath()).matches()) {
            return false;
        }
        String criteria = UriUtils.parseUriQueryParams(op.getUri()).get(CRITERIA_QUERY);
        return criteria != null && !criteria.isEmpty();
    }

    private boolean isSecurityContextRequest(Operation op) {
        return PATTERN_PRINCIPAL_SECURITY_CONTEXT.matcher(op.getUri().getPath()).matches();
    }

    private boolean isRolesRequest(Operation op) {
        return PATTERN_PRINCIPAL_ROLES.matcher(op.getUri().getPath()).matches();
    }

    private void handleSearchById(String principalId, Operation get) {
        DeferredResult<Principal> result = provider.getPrincipal(principalId);

        result.whenComplete((principal, ex) -> {
            if (ex != null) {
                if (ex instanceof ServiceNotFoundException) {
                    get.fail(Operation.STATUS_CODE_NOT_FOUND);
                    return;
                }
                get.fail(ex);
                return;
            }
            get.setBody(principal).complete();
        });
    }

    private void handleSearchByCriteria(String criteria, Operation get) {
        DeferredResult<List<Principal>> result = provider.getPrincipals(criteria);

        result.whenComplete((principals, ex) -> {
            if (ex != null) {
                get.fail(ex);
                return;
            }
            get.setBody(principals).complete();
        });
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!isRolesRequest(patch)) {
            // Not a role patch, we don't support principal patch.
            patch.fail(new LocalizableValidationException("Patching principal is not supported.",
                    "auth.patch.principal.not.supported"));
            return;
        }

        if (!patch.hasBody()) {
            patch.fail(new LocalizableValidationException("body is required",
                    "auth.body.required"));
            return;
        }

        String principalId = UriUtils.parseUriPathSegments(patch.getUri(),
                TEMPLATE_PRINCIPAL_ID_PATH_SEGMENT).get(PRINCIPAL_ID_PATH_SEGMENT);

        if (principalId == null || principalId.isEmpty()) {
            patch.fail(new LocalizableValidationException("Principal ID is required in URI path.",
                    "auth.principalId.required"));
            return;
        }

        if (!PrincipalRolesHandler.isPrincipalRolesUpdate(patch)) {
            patch.fail(new LocalizableValidationException("Unsupported body content.",
                    "auth.body.content.not.supported"));
            return;
        }

        PrincipalRoleAssignment body = patch.getBody(PrincipalRoleAssignment.class);

        DeferredResult<Void> result = PrincipalRolesHandler.create()
                .setHost(getHost())
                .setPrincipalId(principalId)
                .setRoleAssignment(body)
                .update();

        result.whenComplete((ignore, ex) -> {
            if (ex != null) {
                patch.fail(ex);
                return;
            }
            patch.complete();
        });
    }
}

