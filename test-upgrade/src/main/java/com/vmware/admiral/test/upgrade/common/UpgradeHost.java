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

package com.vmware.admiral.test.upgrade.common;

import java.util.logging.Level;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public abstract class UpgradeHost extends ServiceHost {

    protected UpgradeHost initializeHostAndServices(String[] args) throws Throwable {
        log(Level.INFO, "Initializing ...");
        initialize(args);
        log(Level.INFO, "Starting ...");
        start();
        log(Level.INFO, "**** Host starting ... ****");
        startServices();
        log(Level.INFO, "**** Host started. ****");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            this.log(Level.WARNING, "Host stopping ...");
            this.stop();
            this.log(Level.WARNING, "Host stopped.");
        }));
        return this;
    }

    @Override
    public ServiceHost start() throws Throwable {
        super.start();
        startDefaultCoreServicesSynchronously();
        return this;
    }

    protected abstract void startServices();

    /*
     * Helper methods
     */

    @SafeVarargs
    protected static void startServiceFactories(ServiceHost host,
            Class<? extends Service>... serviceClasses) {
        for (Class<? extends Service> serviceClass : serviceClasses) {
            Service serviceInstance;
            try {
                serviceInstance = serviceClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Failed to create factory for " + serviceClass, e);
            }
            host.startFactory(serviceInstance);
        }
    }

    @SafeVarargs
    protected static void startServices(ServiceHost host,
            Class<? extends Service>... serviceClasses) {
        for (Class<? extends Service> serviceClass : serviceClasses) {
            Service serviceInstance;
            try {
                serviceInstance = serviceClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Failed to create instance of " + serviceClass, e);
            }
            startService(host, serviceClass, serviceInstance);
        }
    }

    protected static void startService(ServiceHost host, Class<? extends Service> serviceClass,
            Service serviceInstance) {

        host.startService(
                Operation.createPost(UriUtils.buildUri(host, serviceClass))
                        .setCompletion((o, ex) -> {
                            if (ex != null) {
                                // shutdown the server when encountering an error
                                host.log(Level.SEVERE, "Failed to start service %s: %s",
                                        o.getUri(), Utils.toString(ex));
                                host.stop();
                                return;
                            }
                        }),
                serviceInstance);
    }
}
