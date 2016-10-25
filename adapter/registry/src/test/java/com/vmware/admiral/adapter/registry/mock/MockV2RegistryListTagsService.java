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

import static com.vmware.admiral.adapter.registry.mock.MockRegistryPathConstants.V2_LIST_TAGS_PATH;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class MockV2RegistryListTagsService extends StatelessService {
    public static final String SELF_LINK = V2_LIST_TAGS_PATH;

    static class V2ImageTagsResponse {
        String name;
        String[] tags;
    }

    @Override
    public void handleGet(Operation get) {
        V2ImageTagsResponse response = new V2ImageTagsResponse();
        response.name = "vmware/admiral";
        response.tags = new String[] { "7.1", "7.2", "7.3", "7.4" };

        get.setBody(response);
        get.complete();
    }
}
