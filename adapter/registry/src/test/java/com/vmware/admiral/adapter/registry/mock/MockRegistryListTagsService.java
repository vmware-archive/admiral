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

import static com.vmware.admiral.adapter.registry.mock.MockRegistryPathConstants.DOCKER_HUB_LIST_TAGS_PATH;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class MockRegistryListTagsService extends StatelessService {
    public static final String SELF_LINK = DOCKER_HUB_LIST_TAGS_PATH;

    static class DockerHubImageTagsResponseItem {
        String layer;
        String name;

        DockerHubImageTagsResponseItem(String layer, String name) {
            this.layer = layer;
            this.name = name;
        }
    }

    @Override
    public void handleGet(Operation get) {
        DockerHubImageTagsResponseItem[] response = new DockerHubImageTagsResponseItem[] {
                new DockerHubImageTagsResponseItem("686477c1", "7.1"),
                new DockerHubImageTagsResponseItem("dce38fb5", "7.2"),
                new DockerHubImageTagsResponseItem("195eb90b", "7.3"),
                new DockerHubImageTagsResponseItem("25b0d242", "7.4")
        };

        get.setBody(response);
        get.complete();
    }
}
