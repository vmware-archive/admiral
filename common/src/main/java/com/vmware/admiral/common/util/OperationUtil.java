/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;

import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.GuestUserService;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class OperationUtil {
    public static final String PROJECT_ADMIRAL_HEADER = "x-project";

    public static Operation createForcedPost(URI uri) {
        return Operation.createPost(uri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
    }

    public static Operation createForcedPost(Service sender, String targetPath) {
        return createForcedPost(UriUtils.buildUri(sender.getHost(), targetPath));
    }

    public static String extractProjectFromHeader(Operation op) {
        return op.getRequestHeader(PROJECT_ADMIRAL_HEADER);
    }

    /**
     * Helper method executing Operation.createGet.
     * Calls {@code callbackFunction} passing the result,
     * or in case of exception just logs it and returns.
     *
     * @param service          {@link Service}
     * @param link             document link
     * @param callbackFunction callback to receive the result
     * @param <T>              document state class, extends {@link ServiceDocument}
     */
    public static <T extends ServiceDocument> void getDocumentState(Service service, String link,
            Class<T> classT, Consumer<T> callbackFunction) {
        getDocumentState(service, link, classT, callbackFunction, null);
    }

    /**
     * Helper method executing Operation.createGet.
     * Calls {@code callbackFunction} passing the result,
     * or in case of exception logs it and calls the {@code failureFunction}.
     *
     * @param service          {@link Service}
     * @param link             document link
     * @param callbackFunction callback to receive the result
     * @param failureFunction  error callback
     * @param <T>              document state class, extends {@link ServiceDocument}
     */
    public static <T extends ServiceDocument> void getDocumentState(Service service, String link,
            Class<T> classT, Consumer<T> callbackFunction, Consumer<Throwable> failureFunction) {
        service.sendRequest(
                Operation
                        .createGet(service, link)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                service.getHost().log(
                                        Level.WARNING,
                                        "Failure retrieving document [%s], referrer: [%s],"
                                                + " context id [%s] : %s",
                                        link, o.getRefererAsString(),
                                        o.getContextId(), Utils.toString(e));

                                if (failureFunction != null) {
                                    failureFunction.accept(e);
                                }
                                return;
                            }

                            callbackFunction.accept(o.getBody(classT));
                        })
        );
    }

    public static boolean isApplicationYamlContent(String contentType) {
        return (contentType != null)
                && MEDIA_TYPE_APPLICATION_YAML.equals(contentType.split(";")[0]);
    }

    public static boolean isApplicationYamlAccpetHeader(Operation op) {
        String acceptHeader = op.getRequestHeader("Accept");
        if (acceptHeader == null || acceptHeader.trim().equals("")) {
            return false;
        }
        return MEDIA_TYPE_APPLICATION_YAML.equals(acceptHeader);
    }

    /**
     * Checks whether the specified {@link Operation} <code>op</code> comes from a guest or not.
     * Guest operations will either have no {@link AuthorizationContext} set or will have an
     * {@link AuthorizationContext} that is asociated with the Guest user (see
     * {@link GuestUserService}).
     *
     * @param op
     *            the caller {@link Operation}
     * @return whether this operaion was issued by a guest or not
     */
    public static boolean isGuestUser(Operation op) {
        AuthorizationContext ctx = op.getAuthorizationContext();
        return ctx == null || ctx.getClaims() == null
                || ctx.getClaims().getSubject() == null
                || ctx.getClaims().getSubject().isEmpty()
                || ctx.getClaims().getSubject().equals(GuestUserService.SELF_LINK);
    }

    /**
     * Extract the project link from the header and extend operation's URI
     * with filter that contains clause to return only documents which have the
     * project link from the header in their tenantLinks field.
     *
     * If there is no project link in header the URI is not modified.
     * If there is already filter query the method will not override it, but
     * extend it with 'and' and the new clause, example for this case:
     * If there operation's URI have existing filter: "name eq 'test'", after the
     * modification the filter will look like:
     * "name eq 'test' and tenantLinks.item eq '/projects/test'"
     * @param get
     */
    public static void transformProjectHeaderToFilterQuery(Operation get) {
        String projectLink = OperationUtil.extractProjectFromHeader(get);
        if (projectLink == null || projectLink.isEmpty()) {
            return;
        }

        URI opUri = get.getUri();
        String filterQuery = UriUtils.getODataFilterParamValue(opUri);
        if (filterQuery == null || filterQuery.isEmpty()) {
            filterQuery = constructFilterWithTenantLinks(projectLink);
        } else {
            filterQuery = filterQuery + " and " + constructFilterWithTenantLinks(projectLink);
        }
        Map<String, String> queryMap = UriUtils.parseUriQueryParams(opUri);
        queryMap.put(UriUtils.URI_PARAM_ODATA_FILTER, filterQuery);

        String[] queryKeyVals = new String[queryMap.size() * 2];
        int i = 0;
        for (Entry<String, String> entry : queryMap.entrySet()) {
            queryKeyVals[i] = entry.getKey();
            i++;
            queryKeyVals[i] = entry.getValue();
            i++;
        }

        opUri = UriUtils.buildUri(opUri.getScheme(), opUri.getHost(), opUri.getPort(),
                opUri.getPath(), UriUtils.buildUriQuery(queryKeyVals));

        get.setUri(opUri);
    }

    public static String constructFilterWithTenantLinks(String projectLink) {
        return ResourceState.FIELD_NAME_TENANT_LINKS
                + QuerySpecification.FIELD_NAME_CHARACTER
                + QuerySpecification.COLLECTION_FIELD_SUFFIX
                + " eq '" + projectLink + "'";

    }

    public static String extractTenantFromProjectHeader(Operation op) {
        String businessGroup = op.getRequestHeader(PROJECT_ADMIRAL_HEADER);
        if (businessGroup == null) {
            return null;
        }
        int index = businessGroup.indexOf("/", businessGroup.indexOf("/", 1) + 1);
        if (index == -1) {
            return null;
        }
        String tenantLink = businessGroup.substring(0, index);
        return tenantLink;
    }
}
