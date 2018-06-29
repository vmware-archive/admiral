/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.image.service.mock;

import static com.vmware.admiral.adapter.registry.mock.MockRegistryPathConstants.MOCK_REGISTRY_ADAPTER_PATH;
import static com.vmware.admiral.adapter.registry.mock.MockRegistryPathConstants.MOCK_REGISTRY_PATH_HOSTNAME_AND_PORT;

import java.util.ArrayList;

import com.vmware.admiral.adapter.registry.mock.MockRegistryPathConstants;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse.Result;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class MockRegistryAdapterService extends StatelessService {
    public static final String SELF_LINK = MOCK_REGISTRY_ADAPTER_PATH;

    @Override
    public void handlePatch(Operation patch) {
        RegistrySearchResponse response = new RegistrySearchResponse();
        response.numResults = 5;
        response.numPages = 1;
        response.pageSize = 25;
        response.isPartialResult = true;
        response.page = 1;

        String imagePathInBaseRegistry1 = String.format("%s",
                "ubuntu-1");
        Result result1 = new Result();
        result1.name = imagePathInBaseRegistry1;
        result1.registry = MOCK_REGISTRY_PATH_HOSTNAME_AND_PORT;
        result1.official = true;
        result1.automated = true;
        result1.trusted = true;
        result1.starCount = 0;
        result1.description = "test";

        String imagePathInBaseRegistry2 = String.format("%s",
                "ubuntu-2");
        Result result2 = new Result();
        result2.name = imagePathInBaseRegistry2;
        result2.registry =  MOCK_REGISTRY_PATH_HOSTNAME_AND_PORT;
        result2.official = true;
        result2.automated = true;
        result2.trusted = true;
        result2.starCount = 0;
        result2.description = "test";

        String imagePathInRegistryWithNamespace3 = String.format("%s%s",
                MockRegistryPathConstants.REGISTRY_NAMESPACE_NAME,
                "/ubuntu-namespace-3");
        Result result3 = new Result();
        result3.name = imagePathInRegistryWithNamespace3;
        result3.registry = String.format("%s%s",MOCK_REGISTRY_PATH_HOSTNAME_AND_PORT,
                MockRegistryPathConstants.REGISTRY_NAMESPACE_PATH);
        result3.official = true;
        result3.automated = true;
        result3.trusted = true;
        result3.starCount = 0;
        result3.description = "test";

        String imagePathInRegistryWithNamespace4 = String.format("%s%s",
                MockRegistryPathConstants.REGISTRY_NAMESPACE_NAME,
                "/ubuntu-namespace-4");
        Result result4 = new Result();
        result4.name = imagePathInRegistryWithNamespace4;
        result4.registry = String.format("%s%s",MOCK_REGISTRY_PATH_HOSTNAME_AND_PORT,
                MockRegistryPathConstants.REGISTRY_NAMESPACE_PATH);
        result4.official = true;
        result4.automated = true;
        result4.trusted = true;
        result4.starCount = 0;
        result4.description = "test";

        String imagePathInRegistryWithNamespace5 = String.format("%s%s",
                MockRegistryPathConstants.REGISTRY_NAMESPACE_NAME,
                "/ubuntu-namespace-5");
        Result result5 = new Result();
        result5.name = imagePathInRegistryWithNamespace5;
        result5.registry = String.format("%s%s",MOCK_REGISTRY_PATH_HOSTNAME_AND_PORT,
                MockRegistryPathConstants.REGISTRY_NAMESPACE_PATH);
        result5.official = true;
        result5.automated = true;
        result5.trusted = true;
        result5.starCount = 0;
        result5.description = "test";

        response.results = new ArrayList<>();
        response.results.add(result1);
        response.results.add(result2);
        response.results.add(result3);
        response.results.add(result4);
        response.results.add(result5);

        patch.setBody(response);
        patch.complete();
    }
}
