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

package com.vmware.admiral.closures.drivers.docker;

import com.vmware.admiral.closures.drivers.ClosureDockerClient;
import com.vmware.admiral.closures.drivers.ClosureDockerClientFactory;
import com.vmware.xenon.common.ServiceHost;

/**
 * Factory for docker client.
 *
 */
public class ClosureDockerClientFactoryImpl implements ClosureDockerClientFactory {

    private final ServiceHost serviceHost;
    private ClosureDockerClient dockerClient;

    public ClosureDockerClientFactoryImpl(ServiceHost serviceHost) {
        this.serviceHost = serviceHost;
    }

    @Override
    public synchronized ClosureDockerClient getClient() {
        if (dockerClient == null) {
            dockerClient = new AdmiralDockerClient(serviceHost);
            return dockerClient;
        }

        return dockerClient;
    }

}






