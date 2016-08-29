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

package com.vmware.admiral.adapter.registry.mock;

import static com.vmware.admiral.adapter.registry.mock.MockRegistryPathConstants.V2_CATALOG_PATH;
import static com.vmware.admiral.adapter.registry.mock.MockRegistryPathConstants.V2_SEARCH_PATH;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 * Mock for servicing V2 registry search requests.
 */
public class MockV2RegistrySearchService extends StatelessService {
    public static final String SELF_LINK = V2_SEARCH_PATH;

    private static final String SECOND_PAGE_QUERY = "n=2&last=b";
    private static final String SECOND_PAGE_PATH =
            String.format("%s?%s", V2_CATALOG_PATH, SECOND_PAGE_QUERY);
    private static final String LINK_HEADER_VALUE =
            String.format("<%s>; rel=\"next\"", SECOND_PAGE_PATH);

    static class V2CatalogResponse {
        String[] repositories;
    }

    @Override
    public void handleGet(Operation get) {
        V2CatalogResponse response = new V2CatalogResponse();

        String query = get.getUri().getQuery();
        if (query != null) {
            switch (query) {
            case SECOND_PAGE_QUERY: // second page search results
                response.repositories = new String[] { "test/v2image", "v2image", "test/another" };
                break;

            case "n=1": // ping request
                response.repositories = new String[0];
                break;

            default:
                get.fail(new IllegalArgumentException("Unexpected query: " + query));
                break;
            }
        } else {
            response.repositories = new String[0];
            get.getResponseHeaders().put("Link", LINK_HEADER_VALUE);
        }

        get.setBody(response);
        get.complete();
    }
}
