/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.service;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;

import com.vmware.admiral.adapter.docker.service.DockerAdapterService;
import com.vmware.admiral.adapter.docker.service.DockerHostAdapterImageService;
import com.vmware.admiral.adapter.docker.service.DockerHostAdapterService;
import com.vmware.admiral.adapter.docker.service.DockerNetworkAdapterService;
import com.vmware.admiral.adapter.docker.service.DockerOperationTypesService;
import com.vmware.admiral.adapter.docker.service.DockerVolumeAdapterService;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.service.test.MockComputeHostInstanceAdapter;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.admiral.service.test.MockDockerContainerToHostService;
import com.vmware.admiral.service.test.MockDockerHostAdapterImageService;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.admiral.service.test.MockDockerNetworkAdapterService;
import com.vmware.admiral.service.test.MockDockerNetworkToHostService;
import com.vmware.admiral.service.test.MockDockerVolumeAdapterService;
import com.vmware.admiral.service.test.MockDockerVolumeToHostService;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class HostInitDockerAdapterServiceConfig {
    public static final String FIELD_NAME_START_MOCK_HOST_ADAPTER_INSTANCE = "startMockHostAdapterInstance";

    public static final Collection<ServiceMetadata> SERVICES_METADATA = Collections
            .unmodifiableList(Arrays.asList(
                    service(DockerAdapterService.class),
                    service(DockerOperationTypesService.class),
                    service(DockerHostAdapterService.class),
                    service(DockerNetworkAdapterService.class),
                    service(DockerVolumeAdapterService.class),
                    service(DockerHostAdapterImageService.class)));

    public static void startServices(ServiceHost host, boolean startMockHostAdapterInstance) {
        if (startMockHostAdapterInstance) {
            DeploymentProfileConfig.getInstance().setTest(true);
            host.startService(Operation.createPost(UriUtils.buildUri(host,
                    MockDockerAdapterService.class)), new MockDockerAdapterService());
            host.startFactory(new MockDockerContainerToHostService());

            host.startService(Operation.createPost(UriUtils.buildUri(host,
                    MockDockerHostAdapterService.class)), new MockDockerHostAdapterService());

            host.startService(Operation.createPost(UriUtils.buildUri(host,
                    MockComputeHostInstanceAdapter.class)), new MockComputeHostInstanceAdapter());

            host.startService(Operation.createPost(UriUtils.buildUri(host,
                    MockDockerNetworkAdapterService.class)), new MockDockerNetworkAdapterService());
            host.startFactory(new MockDockerNetworkToHostService());

            host.startService(Operation.createPost(UriUtils.buildUri(host,
                    MockDockerVolumeAdapterService.class)), new MockDockerVolumeAdapterService());
            host.startFactory(new MockDockerVolumeToHostService());
            host.startService(
                    Operation.createPost(
                            UriUtils.buildUri(host, MockDockerHostAdapterImageService.class)),
                    new MockDockerHostAdapterImageService());
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
                    Operation
                            .createPost(UriUtils.buildUri(host, DockerNetworkAdapterService.class)),
                    new DockerNetworkAdapterService());
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host, DockerVolumeAdapterService.class)),
                    new DockerVolumeAdapterService());
            host.startService(
                    Operation.createPost(
                            UriUtils.buildUri(host, DockerHostAdapterImageService.class)),
                    new DockerHostAdapterImageService());
            host.log(Level.INFO, "Docker instance reference: %s", instanceReference);
        }
    }
}
