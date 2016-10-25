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

/**
 * Constants for mock registry host and services
 */
public interface MockRegistryPathConstants {
    public static final String BASE_V1_PATH = "/mock/registry.v1";
    public static final String BASE_V2_PATH = "/mock/registry.v2";

    public static final String V1_PING_PATH = BASE_V1_PATH + "/v1/_ping";

    public static final String V1_SEARCH_PATH = BASE_V1_PATH + "/v1/search";
    public static final String V2_CATALOG_PATH = "/v2/_catalog";
    public static final String V2_SEARCH_PATH = BASE_V2_PATH + V2_CATALOG_PATH;

    public static final String DOCKER_HUB_BASE_PATH = "/mock/docker-hub";
    public static final String DOCKER_HUB_LIST_TAGS_PATH = DOCKER_HUB_BASE_PATH + "/v1/repositories/vmware/admiral/tags";
    public static final String V1_LIST_TAGS_PATH = BASE_V1_PATH + "/v1/repositories/vmware/admiral/tags";
    public static final String V2_LIST_TAGS_PATH = BASE_V2_PATH + "/v2/vmware/admiral/tags/list";
}
