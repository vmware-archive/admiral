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

import static com.vmware.admiral.adapter.registry.mock.MockRegistryPathConstants.V1_SEARCH_PATH;

import java.util.ArrayList;
import java.util.Collections;

import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse.Result;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 * Mock for servicing registry search requests
 */
public class MockRegistrySearchService extends StatelessService {
    public static final String SELF_LINK = V1_SEARCH_PATH;

    @Override
    public void handleGet(Operation get) {
        RegistrySearchResponse response = new RegistrySearchResponse();
        response.numResults = 10;
        response.numPages = 1;
        response.pageSize = 25;
        response.query = "ubuntu";
        response.page = 1;

        Result result = new Result();
        result.name = "ubuntu";
        result.official = true;
        result.starCount = 1889;
        result.description = "Ubuntu is a Debian-based Linux operating system based on free software.";

        response.results = new ArrayList<>(Collections.nCopies(10, result));

        get.setBody(response);
        get.complete();
    }

}
