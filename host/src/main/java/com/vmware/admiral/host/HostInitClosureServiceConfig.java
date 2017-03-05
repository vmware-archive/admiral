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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.vmware.admiral.closures.drivers.DriverRegistry;
import com.vmware.admiral.closures.drivers.DriverRegistryImpl;
import com.vmware.admiral.closures.drivers.docker.ClosureDockerClientFactoryImpl;
import com.vmware.admiral.closures.drivers.docker.DockerDriverBase;
import com.vmware.admiral.closures.services.adapter.AdmiralAdapterFactoryService;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescriptionFactoryService;
import com.vmware.admiral.closures.services.images.DockerImageFactoryService;
import com.vmware.admiral.service.test.MockClosureFactoryService;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class HostInitClosureServiceConfig extends HostInitServiceHelper {

    private static final DriverRegistry driverRegistry = new DriverRegistryImpl();

    public static void startServices(ServiceHost host, boolean startMockHostAdapterInstance) {
        List<FactoryService> factoryServices = initializeFactoryService(
                startMockHostAdapterInstance);

        registerExecutionDrivers(host);

        HostInitClosureServiceConfig.startFactoryServices(host, factoryServices);
    }

    private static List<FactoryService> initializeFactoryService(
            boolean startMockHostAdapterInstance) {
        List<FactoryService> factoryServices = new ArrayList<>(3);

        factoryServices.add(new ClosureDescriptionFactoryService());
        factoryServices.add(new DockerImageFactoryService(driverRegistry));
        if (startMockHostAdapterInstance) {
            factoryServices.add(new MockClosureFactoryService(driverRegistry));
        } else {
            factoryServices.add(new ClosureFactoryService(driverRegistry));
        }
        factoryServices.add(new AdmiralAdapterFactoryService());

        return factoryServices;
    }

    private static void registerExecutionDrivers(ServiceHost host) {
        Map<String, String> runtimes = driverRegistry.getSupportedRuntimes();
        runtimes.forEach((r, image) -> driverRegistry
                .register(r, new DockerDriverBase(host, driverRegistry, new
                        ClosureDockerClientFactoryImpl(host)) {
                    @Override
                    public String getDockerImage() {
                        return image;
                    }
                }));
    }

    private static void startFactoryServices(ServiceHost host,
            List<FactoryService> factoryServices) {
        for (Service factoryService : factoryServices) {
            host.startService(
                    Operation.createPost(UriUtils.buildFactoryUri(host, factoryService.getClass()))
                            .setCompletion((o, ex) -> {
                                if (ex != null) {
                                    // shutdown the server when encountering an error
                                    host.log(Level.SEVERE, "Failed to start service {}: {}",
                                            o.getUri(),
                                            Utils.toString(ex));
                                    host.stop();
                                }
                            }), factoryService);
        }
    }
}

