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

import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class HostInitRegistryAdapterServiceConfig {
    public static volatile URI registryAdapterReference;

    public static void startServices(ServiceHost host) {
        String remoteAdapterReference = System
                .getProperty("dcp.management.container.registry.adapter.service.reference");

        if (remoteAdapterReference != null && !remoteAdapterReference.isEmpty()) {
            registryAdapterReference = URI.create(remoteAdapterReference);

        } else {
            registryAdapterReference = UriUtils.buildUri(host, RegistryAdapterService.class);
            host.startService(Operation.createPost(registryAdapterReference),
                    new RegistryAdapterService());
        }
    }
}
