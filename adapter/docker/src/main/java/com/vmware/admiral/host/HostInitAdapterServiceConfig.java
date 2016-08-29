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

package com.vmware.admiral.host;

import java.net.URI;
import java.util.logging.Level;

import com.vmware.admiral.adapter.docker.service.DockerAdapterService;
import com.vmware.admiral.adapter.docker.service.DockerHostAdapterService;
import com.vmware.admiral.adapter.docker.service.DockerNetworkAdapterService;
import com.vmware.admiral.adapter.docker.service.DockerOperationTypesService;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class HostInitAdapterServiceConfig {
    public static final String FIELD_NAME_START_MOCK_HOST_ADAPTER_INSTANCE = "startMockHostAdapterInstance";

    public static void startServices(ServiceHost host, boolean startMockHostAdapterInstance) {
        if (startMockHostAdapterInstance) {
            DeploymentProfileConfig.getInstance().setTest(true);
            host.startService(Operation.createPost(UriUtils.buildUri(host,
                    MockDockerAdapterService.class)),
                    new MockDockerAdapterService());

            host.startService(Operation.createPost(UriUtils.buildUri(host,
                    MockDockerHostAdapterService.class)), new MockDockerHostAdapterService());
        } else {
            URI instanceReference = null;
            String remoteAdapterReference = System
                    .getProperty("dcp.management.container.docker.instance.service.reference");
            if (remoteAdapterReference != null && !remoteAdapterReference.isEmpty()) {
                instanceReference = URI.create(remoteAdapterReference);
            } else {
                instanceReference = UriUtils.buildUri(host, DockerAdapterService.class);
                host.startService(Operation.createPost(instanceReference),
                        new DockerAdapterService());
                host.startService(Operation.createPost(UriUtils.buildUri(host,
                        DockerOperationTypesService.class)), new DockerOperationTypesService());
            }

            host.startService(
                    Operation.createPost(UriUtils.buildUri(host, DockerHostAdapterService.class)),
                    new DockerHostAdapterService());
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host, DockerNetworkAdapterService.class)),
                    new DockerNetworkAdapterService());

            host.log(Level.INFO, "Docker instance reference: %s", instanceReference);
        }
    }
}
