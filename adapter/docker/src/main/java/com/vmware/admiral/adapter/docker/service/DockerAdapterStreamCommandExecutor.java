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

package com.vmware.admiral.adapter.docker.service;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public interface DockerAdapterStreamCommandExecutor {
    String EVENT_TYPE_CONTAINER = "container";
    String EVENT_TYPE_CONTAINER_START = "start";
    String EVENT_TYPE_CONTAINER_DIE = "die";

    URLConnection openConnection(CommandInput input, URL url) throws NoSuchAlgorithmException, KeyManagementException, IOException;

    void closeConnection(URLConnection con) throws IOException;
}
