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

package com.vmware.admiral.adapter.docker.mock;

/**
 * Constants for mock docker host and services
 */
public interface MockDockerPathConstants {
    public static final String BASE_PATH = "/mock/docker";
    public static final String API_VERSION = "1.24";
    public static final String BASE_VERSIONED_PATH = BASE_PATH + "/v" + API_VERSION;

    public static final String CREATE = "/create";
    public static final String JSON = "/json";
    public static final String START = "/start";
    public static final String STOP = "/stop";
    public static final String LOGS = "/logs";
    public static final String CONNECT = "/connect";

    // since '/stats' is reserved in DCP, use '/stats_mock' instead
    public static final String STATS = "/stats_mock";

    public static final String CONTAINERS = "/containers";
    public static final String IMAGES = "/images";
    public static final String NETWORKS = "/networks";
    public static final String VOLUMES = "/volumes";

    // host:
    public static final String INFO = "/info";
    public static final String VERSION = "/version";
    public static final String _PING = "/_ping";

}
