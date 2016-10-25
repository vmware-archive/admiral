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

import static com.vmware.admiral.adapter.registry.mock.MockRegistryPathConstants.V1_LIST_TAGS_PATH;

import java.util.HashMap;
import java.util.Map;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class MockV1RegistryListTagsService extends StatelessService {
    public static final String SELF_LINK = V1_LIST_TAGS_PATH;

    @Override
    public void handleGet(Operation get) {
        Map<String, String> response = new HashMap<>();
        response.put("7.1", "686477c1");
        response.put("7.2", "dce38fb5");
        response.put("7.3", "195eb90b");
        response.put("7.4", "25b0d242");

        get.setBody(response);
        get.complete();
    }
}
