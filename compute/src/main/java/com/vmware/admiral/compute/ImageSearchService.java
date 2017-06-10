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

package com.vmware.admiral.compute;

import static com.vmware.xenon.common.UriUtils.URI_PARAM_ODATA_EXPAND;
import static com.vmware.xenon.common.UriUtils.URI_PARAM_ODATA_FILTER;
import static com.vmware.xenon.common.UriUtils.URI_PARAM_ODATA_TENANTLINKS;
import static com.vmware.xenon.common.UriUtils.buildFactoryUri;
import static com.vmware.xenon.common.UriUtils.buildUri;
import static com.vmware.xenon.common.UriUtils.extendUriWithQuery;
import static com.vmware.xenon.common.UriUtils.parseUriQueryParams;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.xenon.common.ODataFactoryQueryResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Image search service providing search logic on top of both public and private images. The goal is
 * to be able to search all images with a single query/request.
 */
public class ImageSearchService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.IMAGE_SEARCH;

    public static final String URI_PARAM_ENDPOINT = "endpoint";

    /**
     * Input query parameters supported:
     * <ul>
     * <li>{@value #URI_PARAM_ENDPOINT}: mandatory end-point query which should be resolved to
     * single end-point state</li>
     * <li>{@value UriUtils#URI_PARAM_ODATA_FILTER}: optional image specific filter</li>
     * <li>{@value UriUtils#URI_PARAM_ODATA_TENANTLINKS}: optional single tenant</li>
     * </ul>
     *
     * @return ODataFactoryQueryResult object encapsulating images found.
     */
    @Override
    public void handleGet(Operation getOp) {

        Map<String, String> queryParams = parseUriQueryParams(getOp.getUri());

        //
        // Get and validate input query params {{
        //

        // Strip URI_PARAM_ENDPOINT from queryParams

        final String endpointQuery = queryParams.remove(URI_PARAM_ENDPOINT);

        if (endpointQuery == null || endpointQuery.isEmpty()) {
            getOp.fail(new IllegalArgumentException(
                    "'" + URI_PARAM_ENDPOINT + "' query param is required"));
            return;
        }

        // Strip URI_PARAM_ODATA_FILTER from queryParams

        final String filterParam = queryParams.remove(URI_PARAM_ODATA_FILTER);

        final String filter;

        if (filterParam == null || filterParam.isEmpty()) {
            filter = null;
        } else {
            filter = filterParam;
        }

        // Strip URI_PARAM_ODATA_TENANTLINKS from queryParams

        final String tenantLinksParam = queryParams.remove(URI_PARAM_ODATA_TENANTLINKS);

        final String tenantLink;

        if (tenantLinksParam != null && !tenantLinksParam.isEmpty()) {

            List<String> tenantLinks = Arrays.asList(tenantLinksParam.trim().split("\\s*,\\s*"));

            if (tenantLinks.size() == 0) {
                tenantLink = null;
            } else if (tenantLinks.size() == 1) {
                tenantLink = tenantLinks.get(0);
            } else {
                getOp.fail(new IllegalArgumentException(
                        "'" + URI_PARAM_ODATA_TENANTLINKS
                                + "' query param accepts single tenant only, but got '"
                                + tenantLinks + "'"));
                return;
            }
        } else {
            tenantLink = null;
        }
        // }} END validation

        // Resolve actual EndpoinStates from passed 'endpoint' filter
        final URI uri = extendUriWithQuery(
                buildFactoryUri(getHost(), EndpointService.class),
                URI_PARAM_ODATA_FILTER, endpointQuery,
                URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());

        sendRequest(Operation.createGet(uri)
                .setReferer(getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        getOp.fail(ex);
                        return;
                    }

                    Map<String, Object> endpointStates = op
                            .getBody(ODataFactoryQueryResult.class).documents;

                    if (endpointStates.size() == 0) {
                        getOp.fail(new IllegalArgumentException(
                                "'" + URI_PARAM_ENDPOINT + "' query param ("
                                        + endpointQuery + ") is not resolved to EndpointState"));
                        return;
                    }

                    if (endpointStates.size() > 1) {
                        getOp.fail(new IllegalArgumentException(
                                "'" + URI_PARAM_ENDPOINT + "' query param ("
                                        + endpointQuery + ") is resolved to multiple ("
                                        + endpointStates.size() + ") EndpointStates"));
                        return;
                    }

                    // Once end-point is resolved continue with image search
                    EndpointState endpointState = Utils.fromJson(
                            endpointStates.values().iterator().next(), EndpointState.class);

                    logFine("'%s' endpoint query resolved to '%s' endpoint.", endpointQuery,
                            endpointState.name);

                    String imagesFilter = calculateImagesFilter(filter, tenantLink, endpointState);

                    searchImages(getOp, queryParams, imagesFilter);
                }));
    }

    /**
     * Calculate images filter so it can fetch both public (tenant-less) and private (constraint
     * with endpointLinks and tenantLinks) images with a single query. The filter is build based on
     * original filter, tenant links and target end-point.
     *
     * <pre>
     * (filter) and ((EPType) or ((EP) and (tenant)))
     * </pre>
     */
    String calculateImagesFilter(
            String filter,
            String tenantLink,
            EndpointState endpointState) {

        final String endpointLinkFilter = ImageState.FIELD_NAME_ENDPOINT_LINK + " eq '"
                + endpointState.documentSelfLink + "'";

        final String endpointTypeFilter = ImageState.FIELD_NAME_ENDPOINT_TYPE + " eq '"
                + endpointState.endpointType + "'";

        String tenantLinkFilter = null;

        if (tenantLink != null && !tenantLink.isEmpty()) {
            tenantLinkFilter = ImageState.FIELD_NAME_TENANT_LINKS + "/item eq '" + tenantLink + "'";
        }

        // IMPORTANT: The order of boolean clauses and the brackets used is of HIGH importance!
        // Also tenantLinks related clauses are very sensitive.
        // Please, touch with care.

        String publicCriteria = endpointTypeFilter;
        String privateCriteria = bool(endpointLinkFilter, "and", tenantLinkFilter);

        return bool(filter, "and", bool(publicCriteria, "or", privateCriteria));
    }

    /**
     * Add starting and ending brackets around an expression, if needed.
     */
    String parenthesis(String expression) {

        if (expression == null) {
            return null;
        }

        expression = expression.trim();

        if (expression.isEmpty()) {
            return null;
        }

        final String OPEN_BRACKET = "(";
        final String CLOSE_BRACKET = ")";

        if (!expression.startsWith(OPEN_BRACKET) || !expression.endsWith(CLOSE_BRACKET)) {
            // Neither starts not ends with bracket -> so add brackets
            expression = OPEN_BRACKET + expression + CLOSE_BRACKET;
        } else {
            // Starts and ends with brackets. Still need to validate that brackets are needed
            for (int counter = 1, i = 1; i < expression.length() - 1; i++) {
                if (expression.charAt(i) == OPEN_BRACKET.charAt(0)) {
                    counter++;
                } else if (expression.charAt(i) == CLOSE_BRACKET.charAt(0)) {
                    counter--;
                }
                if (counter == 0) {
                    expression = OPEN_BRACKET + expression + CLOSE_BRACKET;
                    break;
                }
            }
        }

        return expression;
    }

    /**
     * Build composite bool expression considering the values of params passed.
     */
    private String bool(String lhs, String op, String rhs) {

        if (lhs != null && rhs != null) {
            return String.format("%s %s %s", parenthesis(lhs), op, parenthesis(rhs));
        }
        if (lhs != null) {
            return parenthesis(lhs);
        }
        if (rhs != null) {
            return parenthesis(rhs);
        }
        return null;
    }

    /**
     * Delegate to underlying {@link ImageService}'s factory.
     */
    private void searchImages(Operation getImagesOp, Map<String, String> queryParams,
            String imagesFilter) {

        queryParams.put(URI_PARAM_ODATA_FILTER, imagesFilter);

        String imagesQuery = queryParams.entrySet().stream()
                .map(queryParam -> queryParam.getKey() + "=" + queryParam.getValue())
                .reduce((p1, p2) -> p1 + "&" + p2)
                .orElse("");

        URI uri = buildUri(getHost(), ImageService.FACTORY_LINK, imagesQuery);

        final String msg = "Delegate to ImageService factory with [%s] query: %s";

        logFine(msg, imagesQuery, "STARTING");

        sendRequest(Operation.createGet(uri).setCompletion((o, e) -> {
            if (e != null) {
                logFine(msg, imagesQuery, "FAILED");
                getImagesOp.fail(e);
            } else {
                logFine(msg, imagesQuery, "SUCCESS");
                // ODataFactoryQueryResult is propagated as returned by ImageService factory
                getImagesOp.setBodyNoCloning(o.getBodyRaw()).complete();
            }
        }));
    }
}
