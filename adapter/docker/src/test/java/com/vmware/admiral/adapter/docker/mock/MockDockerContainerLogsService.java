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

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 * Mock for servicing log requests for a container instance
 */
public class MockDockerContainerLogsService extends StatelessService {

    @Override
    public void handleGet(Operation get) {
        String logs = "mock log message";
        get.setContentType("application/octet-stream");
        get.setBody(logs.getBytes());
        get.complete();
    }

}
