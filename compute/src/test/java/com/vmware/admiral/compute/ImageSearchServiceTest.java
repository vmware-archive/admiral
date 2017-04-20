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

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.xenon.common.ODataFactoryQueryResult;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for the {@link ImageSearchService} class.
 */
public class ImageSearchServiceTest extends ComputeBaseTest {

    private EndpointState endpoint;

    private void beforeTest() throws Throwable {

        waitForServiceAvailability(ImageSearchService.SELF_LINK);

        // Those images should be found by search
        {
            endpoint = doPost(createEndpoint("image-endpoint"), EndpointService.FACTORY_LINK);

            createImage(true, endpoint);
            createImage(false, endpoint);
        }

        // Those images should NOT be found by search
        {
            EndpointState shouldNotMatchEndpoint = createEndpoint(
                    endpoint.name + "-shouldNotMatch");
            shouldNotMatchEndpoint.endpointType = EndpointType.azure.name();
            shouldNotMatchEndpoint = doPost(shouldNotMatchEndpoint, EndpointService.FACTORY_LINK);

            createImage(true, shouldNotMatchEndpoint);
            createImage(false, shouldNotMatchEndpoint);
        }
    }

    @Test
    public void testParenthesis() throws Throwable {

        final ImageSearchService instance = new ImageSearchService();

        assertEquals((String) null, instance.parenthesis(null));

        assertEquals((String) null, instance.parenthesis(" "));

        // Should add brackets

        assertEquals("(A and B)", instance.parenthesis(" A and B "));

        assertEquals("((A and C) and B)", instance.parenthesis("(A and C) and B"));

        assertEquals("(A and (C and B))", instance.parenthesis("A and (C and B)"));

        assertEquals("((A) and (B))", instance.parenthesis("(A) and (B)"));
        assertEquals("((A and (C)) and (B))", instance.parenthesis("(A and (C)) and (B)"));

        // No need to add brackets

        assertEquals("(A)", instance.parenthesis("(A)"));

        assertEquals("((A and (C)) and B)", instance.parenthesis("((A and (C)) and B)"));
    }

    // The generic format is: (((EP) and (filter)) and (tenant)) OR ((EPType) and (filter))
    @Test
    public void testCalculateImagesFilter() throws Throwable {

        final ImageSearchService instance = new ImageSearchService();

        final String FILTER = "name eq 'image-name'";
        final String TENANT_LINK = "image-tenantLink";
        final EndpointState ENDPOINT_STATE = createEndpoint("image-endpoint");
        ENDPOINT_STATE.documentSelfLink = "image-endpointLink";

        {
            String $filter = FILTER;
            String tenantLink = TENANT_LINK;
            EndpointState endpointState = ENDPOINT_STATE;

            String imagesFilter = instance.calculateImagesFilter($filter, tenantLink,
                    endpointState);

            assertEquals(
                    "(((endpointLink eq 'image-endpointLink') and (name eq 'image-name')) and (tenantLinks/item eq 'image-tenantLink')) or ((endpointType eq 'aws') and (name eq 'image-name'))",
                    imagesFilter);
        }
        {
            String $filter = FILTER;
            String tenantLink = null;
            EndpointState endpointState = ENDPOINT_STATE;

            String imagesFilter = instance.calculateImagesFilter($filter, tenantLink,
                    endpointState);

            assertEquals(
                    "((endpointLink eq 'image-endpointLink') and (name eq 'image-name')) or ((endpointType eq 'aws') and (name eq 'image-name'))",
                    imagesFilter);
        }
        {
            String $filter = null;
            String tenantLink = TENANT_LINK;
            EndpointState endpointState = ENDPOINT_STATE;

            String imagesFilter = instance.calculateImagesFilter($filter, tenantLink,
                    endpointState);

            assertEquals(
                    "((endpointLink eq 'image-endpointLink') and (tenantLinks/item eq 'image-tenantLink')) or (endpointType eq 'aws')",
                    imagesFilter);
        }
        {
            String $filter = null;
            String tenantLink = null;
            EndpointState endpointState = ENDPOINT_STATE;

            String imagesFilter = instance.calculateImagesFilter($filter, tenantLink,
                    endpointState);

            assertEquals(
                    "(endpointLink eq 'image-endpointLink') or (endpointType eq 'aws')",
                    imagesFilter);
        }
    }

    private static class TestRunCtx {

        final String imageSearchQuery;

        int expectedTotalCount = 2;

        Class<? extends Throwable> expectedExc = IllegalArgumentException.class;

        TestRunCtx(String imageSearchQuery) {
            this.imageSearchQuery = imageSearchQuery;
        }
    }

    private Consumer<TestRunCtx> runTest = (ctx) -> {

        host.log(Level.INFO, "run testImageSearch: query=%s", ctx.imageSearchQuery);

        URI uri = UriUtils.buildUri(host, ImageSearchService.SELF_LINK,
                ctx.imageSearchQuery);

        try {
            ODataFactoryQueryResult images = getDocument(ODataFactoryQueryResult.class, uri);

            assertEquals("expectedTotalCount mismatch",
                    ctx.expectedTotalCount,
                    images.totalCount.intValue());

        } catch (Throwable e) {
            throw new IllegalStateException("Test execution failed with unexpected "
                    + e.getClass().getSimpleName() + ".", e);
        }
    };

    private Consumer<TestRunCtx> runTestWithExc = (ctx) -> {

        host.log(Level.INFO, "run testImageSearch: expExc=%s, query=%s",
                ctx.expectedExc.getSimpleName(), ctx.imageSearchQuery);

        URI uri = UriUtils.buildUri(host, ImageSearchService.SELF_LINK,
                ctx.imageSearchQuery);

        try {
            getDocument(ODataFactoryQueryResult.class, uri);

            throw new IllegalStateException(
                    "Test execution should have failed with expected "
                            + ctx.expectedExc.getSimpleName()
                            + ".");

        } catch (Throwable e) {
            if (!ctx.expectedExc.isAssignableFrom(e.getClass())) {
                throw new IllegalStateException(
                        "Test execution expected " + ctx.expectedExc.getSimpleName()
                                + " but failed with " + e.getClass().getSimpleName() + ".",
                        e);
            }
        }
    };

    @Test
    public void testImageSearch() throws Throwable {

        beforeTest();

        final String FILTER = "name eq 'image-name'";
        final String FILTER_INVALID = "name eq 'image-name-invalid'";

        final String TENANT_LINK = "image-tenantLink";
        final String TENANT_LINK_INVALID = TENANT_LINK + "-invalid";

        final String ENDPOINT_QUERY = "documentSelfLink eq '" + endpoint.documentSelfLink + "'";
        final String ENDPOINT_QUERY_INVALID = "documentSelfLink eq '" + endpoint.documentSelfLink
                + "-invalid'";
        final String ENDPOINT_QUERY_MULTIPLE = "name eq '" + endpoint.name + "*'";

        // Positive tests
        {
            String $filter = FILTER;
            String tenantLink = TENANT_LINK;
            String endpointQuery = ENDPOINT_QUERY;

            runTest.accept(new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery)));
        }
        {
            String $filter = FILTER;
            String tenantLink = TENANT_LINK;
            String endpointQuery = ENDPOINT_QUERY + "&" + UriUtils.URI_PARAM_ODATA_TOP + "=" + 1;

            TestRunCtx ctx = new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery));
            ctx.expectedTotalCount = 1;

            runTest.accept(ctx);
        }
        {
            String $filter = FILTER;
            String tenantLink = null;
            String endpointQuery = ENDPOINT_QUERY;

            runTest.accept(new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery)));
        }
        {
            String $filter = null;
            String tenantLink = TENANT_LINK;
            String endpointQuery = ENDPOINT_QUERY;

            runTest.accept(new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery)));
        }
        {
            String $filter = null;
            String tenantLink = " ";
            String endpointQuery = ENDPOINT_QUERY;

            runTest.accept(new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery)));
        }
        {
            String $filter = null;
            String tenantLink = TENANT_LINK_INVALID;
            String endpointQuery = ENDPOINT_QUERY;

            // Hit the Public image with no tenant
            TestRunCtx ctx = new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery));
            ctx.expectedTotalCount = 1;

            runTest.accept(ctx);
        }
        {
            String $filter = FILTER_INVALID;
            String tenantLink = null;
            String endpointQuery = ENDPOINT_QUERY;

            TestRunCtx ctx = new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery));
            ctx.expectedTotalCount = 0;

            runTest.accept(ctx);
        }

        // Negative tests

        // 'endpoint' query param validation
        {
            String $filter = FILTER;
            String tenantLink = TENANT_LINK;
            // Endpoint filter is Mandatory!
            String endpointQuery = null;

            runTestWithExc.accept(new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery)));
        }
        {
            String $filter = FILTER;
            String tenantLink = TENANT_LINK;
            String endpointQuery = ENDPOINT_QUERY_INVALID;

            runTestWithExc.accept(new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery)));
        }
        {
            String $filter = FILTER;
            String tenantLink = TENANT_LINK;
            // Endpoint filter matches 2 endpoints, but single one is expected
            String endpointQuery = ENDPOINT_QUERY_MULTIPLE;

            runTestWithExc.accept(new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery)));
        }

        // 'tenantLinks' query param validation
        {
            String $filter = FILTER;
            // tenantLinks should be single
            String tenantLink = TENANT_LINK + "," + TENANT_LINK;
            String endpointQuery = ENDPOINT_QUERY;

            runTestWithExc.accept(new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery)));
        }
    }

    private ImageState createImage(boolean isPublic, EndpointState endpoint) throws Throwable {

        final ImageState imageState = new ImageState();
        imageState.id = "image-id";
        imageState.name = "image-name";
        imageState.description = "image-desc";
        imageState.regionId = "image-region";

        if (isPublic) {
            imageState.endpointType = endpoint.endpointType;
        } else {
            imageState.endpointLink = endpoint.documentSelfLink;
            imageState.tenantLinks = Collections.singletonList("image-tenantLink");
        }

        return doPost(imageState, ImageService.FACTORY_LINK);
    }

    private String buildQuery(String $filter, String tenantLink, String endpointQuery) {

        Map<String, String> queryParams = new LinkedHashMap<>();

        if ($filter != null) {
            queryParams.put(UriUtils.URI_PARAM_ODATA_FILTER, $filter);
        }
        if (tenantLink != null) {
            queryParams.put(UriUtils.URI_PARAM_ODATA_TENANTLINKS, tenantLink);
        }
        if (endpointQuery != null) {
            queryParams.put(ImageSearchService.URI_PARAM_ENDPOINT, endpointQuery);
        }

        return queryParams.entrySet().stream()
                .map(queryParam -> queryParam.getKey() + "=" + queryParam.getValue())
                .reduce((p1, p2) -> p1 + "&" + p2)
                .orElse("");
    }

}
