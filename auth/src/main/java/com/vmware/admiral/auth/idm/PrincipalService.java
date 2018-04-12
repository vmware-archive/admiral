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

import static com.vmware.admiral.auth.util.PrincipalRolesUtil.getAllRolesForPrincipals;
import static com.vmware.admiral.auth.util.PrincipalRolesUtil.getDirectlyAssignedProjectRolesForGroup;
import static com.vmware.admiral.auth.util.PrincipalRolesUtil.getDirectlyAssignedProjectRolesForUser;
import static com.vmware.admiral.auth.util.PrincipalRolesUtil.getDirectlyAssignedSystemRolesForGroup;
import static com.vmware.admiral.auth.util.PrincipalRolesUtil.getDirectlyAssignedSystemRolesForUser;
import static com.vmware.admiral.auth.util.PrincipalUtil.copyPrincipalData;
import static com.vmware.admiral.auth.util.PrincipalUtil.encode;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNullOrEmpty;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vmware.admiral.auth.idm.Principal.PrincipalType;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.idm.SecurityContext.SecurityContextPostDto;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.auth.util.PrincipalUtil;
import com.vmware.admiral.auth.util.SecurityContextUtil;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.service.common.RegistryService.RegistryAuthState;
import com.vmware.admiral.service.common.harbor.Harbor;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class PrincipalService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.AUTH_PRINCIPALS;
    public static final String CRITERIA_QUERY = "criteria";
    public static final String ROLES_QUERY = "roles";
    public static final String ROLES_QUERY_VALUE = "all";
    public static final String SECURITY_CONTEXT_SUFFIX = "/security-context";
    public static final String ROLES_SUFFIX = "/roles";

    private static final String PRINCIPAL_ID_PATH_SEGMENT = "principalId";

    private static final String TEMPLATE_PRINCIPAL_SECURITY_CONTEXT = UriUtils
            .buildUriPath(SELF_LINK,
                    String.format("{%s}", PRINCIPAL_ID_PATH_SEGMENT), SECURITY_CONTEXT_SUFFIX);

    /**
     * Matches /auth/idm/principals
     */
    private static final Pattern PATTERN_PRINCIPAL_SERVICE_BASE = Pattern
            .compile(String.format("^%s\\/?$", SELF_LINK.replaceAll("/", "\\\\/")));

    /**
     * Matches /auth/idm/principals/{principal-id}/security-context
     */
    private static final Pattern PATTERN_PRINCIPAL_SECURITY_CONTEXT = Pattern
            .compile(String.format("^%s\\/(?<%s>.+?)%s\\/?$", SELF_LINK.replaceAll("/", "\\\\/"),
                    PRINCIPAL_ID_PATH_SEGMENT, SECURITY_CONTEXT_SUFFIX));

    /**
     * Matches /auth/idm/principals/{principal-id}/roles
     */
    private static final Pattern PATTERN_PRINCIPAL_ROLES = Pattern
            .compile(String.format("^%s\\/(?<%s>.+?)%s\\/?$", SELF_LINK.replaceAll("/", "\\\\/"),
                    PRINCIPAL_ID_PATH_SEGMENT, ROLES_SUFFIX));

    private PrincipalProvider provider;

    public PrincipalService() {
        super();
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        provider = AuthUtil.getPreferredProvider(PrincipalProvider.class);
        provider.init(this);
        startPost.complete();
    }

    @Override
    public void handleGet(Operation get) {
        if (isSecurityContextRequest(get)) {
            handleGetSecurityContext(get);
        } else if (isRolesRequest(get)) {
            handleGetPrincipalRoles(get);

        } else if (isPrincipalByIdRequest(get)) {
            handleSearchById(get);

        } else if (isPrincipalByCriteriaRequest(get)) {
            Map<String, String> queryParams = UriUtils.parseUriQueryParams(get.getUri());

            String roleQueryValue = queryParams.getOrDefault(ROLES_QUERY, null);

            handleSearchByCriteria(queryParams.get(CRITERIA_QUERY), roleQueryValue, get);

        } else {
            get.fail(new IllegalArgumentException(
                    "Provide either criteria or principalId to search for."));
        }
    }

    /**
     * Checks whether a principal ID was specified. Ignores suffixes like {@link #ROLES_SUFFIX}
     * or {@link #SECURITY_CONTEXT_SUFFIX}. Additional checks are needed when those are expected.
     */
    private boolean isPrincipalByIdRequest(Operation op) {
        return op.getUri().getPath().length() > SELF_LINK.length() + 1;
    }

    private String getIdFromPrincipalByIdRequest(Operation op) {
        return op.getUri().getPath().substring(SELF_LINK.length() + 1);
    }

    private boolean isPrincipalByCriteriaRequest(Operation op) {
        if (!UriUtilsExtended.uriPathMatches(op.getUri(), PATTERN_PRINCIPAL_SERVICE_BASE)) {
            return false;
        }
        String criteria = UriUtils.parseUriQueryParams(op.getUri()).get(CRITERIA_QUERY);
        return criteria != null && !criteria.isEmpty();
    }

    private boolean isSecurityContextRequest(Operation op) {
        return UriUtilsExtended.uriPathMatches(op.getUri(), PATTERN_PRINCIPAL_SECURITY_CONTEXT);
    }

    private boolean isRolesRequest(Operation op) {
        return UriUtilsExtended.uriPathMatches(op.getUri(), PATTERN_PRINCIPAL_ROLES);
    }

    private void handleGetSecurityContext(Operation get) {
        String principalId = extractPropertyFromPath(PATTERN_PRINCIPAL_SECURITY_CONTEXT,
                PRINCIPAL_ID_PATH_SEGMENT, get.getUri().getPath());

        if (principalId == null) {
            get.fail(new LocalizableValidationException("Principal ID is required in URI path.",
                    "auth.principalId.required"));
            return;
        }

        SecurityContextUtil.getSecurityContext(this, get, principalId)
                .thenAccept((context) -> {
                    get.setBody(context);
                    get.complete();
                }).exceptionally((ex) -> {
                    if (ex.getCause() instanceof ServiceNotFoundException) {
                        logWarning(
                                "Failed to retrieve security context for user %s: user does not exist",
                                principalId);
                        // hide stacktrace from response
                        ServiceErrorResponse rsp = Utils.toServiceErrorResponse(ex);
                        rsp.stackTrace = null;
                        get.fail(Operation.STATUS_CODE_NOT_FOUND, ex, rsp);
                    } else {
                        logWarning("Failed to retrieve security context for user %s: %s",
                                principalId, Utils.toString(ex));
                        get.fail(ex);
                    }
                    return null;
                });
    }

    private void handleSearchById(Operation get) {
        String principalId = getIdFromPrincipalByIdRequest(get);
        DeferredResult<Principal> result = provider.getPrincipal(get, encode(principalId));
        result.whenComplete((principal, ex) -> {
            if (ex != null) {
                if (ex.getCause() instanceof ServiceNotFoundException) {
                    get.fail(Operation.STATUS_CODE_NOT_FOUND, ex.getCause(), ex.getCause());
                    return;
                }
                get.fail(ex);
                return;
            }

            if (principal == null) {
                get.fail(new Throwable("Principal does not exist!"));
                return;
            }

            get.setBody(principal).complete();
        });
    }

    private void handleSearchByCriteria(String criteria, String roles, Operation get) {
        DeferredResult<List<Principal>> result = provider.getPrincipals(get, criteria);

        if (roles != null && roles.equalsIgnoreCase(ROLES_QUERY_VALUE)) {
            result.thenCompose(principals -> getAllRolesForPrincipals(this, get, principals))
                    .thenAccept(principalRoles -> get.setBody(principalRoles))
                    .whenCompleteNotify(get);
            return;
        }

        result.thenAccept(principals -> get.setBody(principals))
                .whenCompleteNotify(get);
    }

    private void handleGetPrincipalRoles(Operation get) {
        String principalId = extractPropertyFromPath(PATTERN_PRINCIPAL_ROLES,
                PRINCIPAL_ID_PATH_SEGMENT, get.getUri().getPath());

        if (principalId == null) {
            get.fail(new LocalizableValidationException("Principal ID is required in URI path.",
                    "auth.principalId.required"));
            return;
        }
        PrincipalRoles rolesResponse = new PrincipalRoles();
        PrincipalUtil.getPrincipal(this, get, principalId)
                .thenAccept(principal -> copyPrincipalData(principal, rolesResponse))
                .thenCompose(ignore -> {
                    if (rolesResponse.type == PrincipalType.GROUP) {
                        return getDirectlyAssignedProjectRolesForGroup(this, rolesResponse);
                    }
                    return getDirectlyAssignedProjectRolesForUser(this, rolesResponse);
                })
                .thenAccept(projectEntries -> rolesResponse.projects = projectEntries)
                .thenCompose(ignore -> {
                    if (rolesResponse.type == PrincipalType.GROUP) {
                        return getDirectlyAssignedSystemRolesForGroup(this, rolesResponse);
                    }
                    return getDirectlyAssignedSystemRolesForUser(this, rolesResponse);
                })
                .thenAccept(systemRoles -> rolesResponse.roles = systemRoles)
                .thenAccept(ignore -> get.setBody(rolesResponse))
                .whenCompleteNotify(get);
    }

    @Override
    public void handlePost(Operation post) {
        if (isSecurityContextRequest(post)) {
            handlePostSecurityContext(post);
            return;
        }

        super.handlePost(post);
    }

    private void handlePostSecurityContext(Operation post) {
        if (!post.hasBody()) {
            post.fail(new LocalizableValidationException("body is required",
                    "auth.body.required"));
            return;
        }

        SecurityContextPostDto dto = post.getBody(SecurityContextPostDto.class);
        assertNotNullOrEmpty(dto.password, "password");
        String principalId = UriUtils
                .parseUriPathSegments(post.getUri(), TEMPLATE_PRINCIPAL_SECURITY_CONTEXT)
                .get(PRINCIPAL_ID_PATH_SEGMENT);

        URI getCredentials = UriUtils.extendUriWithQuery(
                UriUtils.buildUri(getHost(), Harbor.DEFAULT_REGISTRY_LINK),
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.toString(true));

        Operation op = Operation.createGet(getCredentials).setCompletion((o, e) -> {
            String username = null;
            String password = null;

            if (e != null) {
                if (e instanceof ServiceNotFoundException) {
                    // No default Harbor registry or its credentials configured.
                } else {
                    post.fail(e);
                    return;
                }
            } else {
                RegistryAuthState registry = o.getBody(RegistryAuthState.class);
                AuthCredentialsServiceState credentials = registry.authCredentials;

                if (credentials != null) {
                    username = credentials.userEmail;
                    password = credentials.privateKey;
                }
            }

            /*
             * If the provided credentials match the default Harbor registry credentials, the
             * default Harbor security context (as Cloud Admin) is returned.
             */

            if (principalId.equals(username)
                    && dto.password.equals(EncryptionUtils.decrypt(password))) {

                SecurityContext sc = new SecurityContext();
                sc.id = username;
                sc.roles = new HashSet<>(Arrays.asList(AuthRole.CLOUD_ADMIN));

                post.setBody(sc).complete();
                return;
            }

            /*
             * Otherwise, get the security context for the provided credentials.
             */

            provider.getPrincipalByCredentials(post, principalId, dto.password)
                    .thenCompose(
                            principal -> SecurityContextUtil.getSecurityContext(this, post,
                                    principal))
                    .thenAccept(securityContext -> post.setBody(securityContext))
                    .whenCompleteNotify(post);
        });

        sendRequest(op);
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

        String principalId = extractPropertyFromPath(PATTERN_PRINCIPAL_ROLES,
                PRINCIPAL_ID_PATH_SEGMENT, patch.getUri().getPath());

        if (principalId == null) {
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
                .setService(this)
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

    private static String extractPropertyFromPath(Pattern pattern, String groupName, String path) {
        Matcher matcher = pattern.matcher(path);
        if (!matcher.matches()) {
            return null;
        }

        return matcher.group(groupName);
    }
}
