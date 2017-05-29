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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.junit.Before;
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

    private static final String TENANT_LINK = "image-tenantLink";
    private static final String IMAGE_NAME = "image-name";

    private EndpointState endpoint;
    private EndpointState shouldNotMatchEndpoint;

    @Before
    public void beforeTest() throws Throwable {
        {
            // Create AWS end-point
            endpoint = createEndpoint("image-endpoint");
            endpoint.documentSelfLink = "image-endpointLink";

            endpoint = doPost(endpoint, EndpointService.FACTORY_LINK);
        }

        {
            shouldNotMatchEndpoint = createEndpoint(endpoint.name + "-shouldNotMatch");
            shouldNotMatchEndpoint.endpointType = EndpointType.azure.name();

            shouldNotMatchEndpoint = doPost(shouldNotMatchEndpoint, EndpointService.FACTORY_LINK);
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

        final String FILTER = "name eq '" + IMAGE_NAME + "'";

        {
            String $filter = FILTER;
            String tenantLink = TENANT_LINK;

            String imagesFilter = instance.calculateImagesFilter($filter, tenantLink, endpoint);

            assertEquals(
                    "(((endpointLink eq '/resources/endpoints/image-endpointLink') and (name eq 'image-name')) and (tenantLinks/item eq 'image-tenantLink')) or ((endpointType eq 'aws') and (name eq 'image-name'))",
                    imagesFilter);
        }
        {
            String $filter = FILTER;
            String tenantLink = null;

            String imagesFilter = instance.calculateImagesFilter($filter, tenantLink, endpoint);

            assertEquals(
                    "((endpointLink eq '/resources/endpoints/image-endpointLink') and (name eq 'image-name')) or ((endpointType eq 'aws') and (name eq 'image-name'))",
                    imagesFilter);
        }
        {
            String $filter = null;
            String tenantLink = TENANT_LINK;

            String imagesFilter = instance.calculateImagesFilter($filter, tenantLink, endpoint);

            assertEquals(
                    "((endpointLink eq '/resources/endpoints/image-endpointLink') and (tenantLinks/item eq 'image-tenantLink')) or (endpointType eq 'aws')",
                    imagesFilter);
        }
        {
            String $filter = null;
            String tenantLink = null;

            String imagesFilter = instance.calculateImagesFilter($filter, tenantLink, endpoint);

            assertEquals(
                    "(endpointLink eq '/resources/endpoints/image-endpointLink') or (endpointType eq 'aws')",
                    imagesFilter);
        }
    }

    @Test
    public void testImageSearch() throws Throwable {

        Collection<ImageState> createdImages = createImage("");

        final String FILTER = "name eq '" + IMAGE_NAME + "'";
        final String FILTER_INVALID = "name eq '" + IMAGE_NAME + "-invalid'";

        final String TENANT_LINK_INVALID = TENANT_LINK + "-invalid";

        final String ENDPOINT_QUERY = "documentSelfLink eq '" + endpoint.documentSelfLink + "'";
        final String ENDPOINT_QUERY_INVALID = "documentSelfLink eq '"
                + endpoint.documentSelfLink
                + "-invalid'";
        final String ENDPOINT_QUERY_MULTIPLE = "name eq '" + endpoint.name + "*'";

        try {
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
                String endpointQuery = ENDPOINT_QUERY + "&" + UriUtils.URI_PARAM_ODATA_TOP + "="
                        + 1;

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

                runTestWithExc
                        .accept(new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery)));
            }
            {
                String $filter = FILTER;
                String tenantLink = TENANT_LINK;
                String endpointQuery = ENDPOINT_QUERY_INVALID;

                runTestWithExc
                        .accept(new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery)));
            }
            {
                String $filter = FILTER;
                String tenantLink = TENANT_LINK;
                // Endpoint filter matches 2 endpoints, but single one is expected
                String endpointQuery = ENDPOINT_QUERY_MULTIPLE;

                runTestWithExc
                        .accept(new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery)));
            }

            // 'tenantLinks' query param validation
            {
                String $filter = FILTER;
                // tenantLinks should be single
                String tenantLink = TENANT_LINK + "," + TENANT_LINK;
                String endpointQuery = ENDPOINT_QUERY;

                runTestWithExc
                        .accept(new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery)));
            }
        } finally {
            deleteImages(createdImages);
        }
    }

    @Test
    public void testImageSearch_multipleNamesQuery() throws Throwable {

        final Collection<ImageState> createdImages = new ArrayList<>();
        createdImages.addAll(createImage("-apple"));
        createdImages.addAll(createImage("-orange"));

        final String FILTER = "name any '" + createdImages.stream()
                .map(imageSt -> imageSt.name)
                .distinct()
                .collect(Collectors.joining(";"))
                + "'";

        final String ENDPOINT_QUERY = "documentSelfLink eq '" + endpoint.documentSelfLink + "'";

        try {
            {
                String $filter = FILTER;
                String tenantLink = TENANT_LINK;
                String endpointQuery = ENDPOINT_QUERY;

                TestRunCtx ctx = new TestRunCtx(buildQuery($filter, tenantLink, endpointQuery));
                // 2 Private + 2 Public
                ctx.expectedTotalCount = 4;

                runTest.accept(ctx);
            }
        } finally {
            deleteImages(createdImages);
        }
    }

    private static class TestRunCtx {

        final String imageSearchQuery;

        /**
         * Defaults to one for private and one for public image.
         */
        int expectedTotalCount = 2;

        /**
         * Defaults to IllegalArgumentException.
         */
        Class<? extends Throwable> expectedExc = IllegalArgumentException.class;

        TestRunCtx(String imageSearchQuery) {
            this.imageSearchQuery = imageSearchQuery;
        }
    }

    private Consumer<TestRunCtx> runTest = (ctx) -> {

        host.log(Level.INFO, "R-U-N testImageSearch: query=%s", ctx.imageSearchQuery);

        URI uri = UriUtils.buildUri(host, ImageSearchService.SELF_LINK,
                ctx.imageSearchQuery);

        ODataFactoryQueryResult images;
        try {
            images = getDocument(ODataFactoryQueryResult.class, uri);
        } catch (Throwable e) {
            throw new IllegalStateException("Test execution failed with unexpected "
                    + e.getClass().getSimpleName() + ".", e);
        }

        try {
            assertEquals("expectedTotalCount mismatch",
                    ctx.expectedTotalCount,
                    images.totalCount.intValue());
        } catch (AssertionError assertErr) {
            try {
                ODataFactoryQueryResult allImages = getDocument(
                        ODataFactoryQueryResult.class,
                        UriUtils.buildExpandLinksQueryUri(
                                UriUtils.buildUri(host, ImageService.FACTORY_LINK)));
                allImages.documents.values().forEach(
                        doc -> host.log(Level.INFO, "EXISTING image = %s", doc));
            } catch (Throwable e) {
                host.log(Level.INFO, "EXISTING image = E_R_R_O_R (%s)", e.getMessage());
            }

            images.documents.values().forEach(
                    doc -> host.log(Level.INFO, "RETURNED image = %s", doc));

            throw assertErr;
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

    /**
     * Create one public and one private image in the main end-point, and one public and one private
     * image in the "shouldNotMatch" end-point.
     *
     * @return unique suffix of the image created
     */
    private Collection<ImageState> createImage(String suffix) throws Throwable {

        List<ImageState> preCreateImages = new ArrayList<>();

        // Those images should be found by search
        {
            // Create it as public
            ImageState publicImage = createImage(suffix, true, endpoint);
            preCreateImages.add(publicImage);

            // Create it as private
            ImageState privateImage = createImage(suffix, false, endpoint);
            preCreateImages.add(privateImage);
        }

        // Those images should NOT be found by search
        // cause they are in different End-point and End-point Type
        {
            // Use same suffix as above!
            ImageState publicImage = createImage(suffix, true, shouldNotMatchEndpoint);
            preCreateImages.add(publicImage);

            ImageState privateImage = createImage(suffix, false, shouldNotMatchEndpoint);
            preCreateImages.add(privateImage);
        }

        return preCreateImages;
    }

    private ImageState createImage(String uniqueSuffix, boolean isPublic, EndpointState endpoint)
            throws Throwable {

        final ImageState imageState = new ImageState();

        imageState.id = "image-id" + uniqueSuffix;
        imageState.name = IMAGE_NAME + uniqueSuffix;
        imageState.description = "image-desc" + uniqueSuffix;
        imageState.regionId = "image-region" + uniqueSuffix;

        if (isPublic) {
            imageState.endpointType = endpoint.endpointType;
        } else {
            imageState.endpointLink = endpoint.documentSelfLink;
            imageState.tenantLinks = Collections.singletonList(TENANT_LINK);
        }

        return doPost(imageState, ImageService.FACTORY_LINK);
    }

    private void deleteImages(Collection<ImageState> imageStates) {
        imageStates.forEach(imageSt -> {
            try {
                delete(imageSt.documentSelfLink);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });
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
