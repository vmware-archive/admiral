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

import java.util.logging.Level;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Helper for starting services
 */
public abstract class HostInitServiceHelper {

    @SafeVarargs
    public static void startServiceFactories(ServiceHost host,
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
    public static void startServices(ServiceHost host,
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

    public static void startService(ServiceHost host, Class<? extends Service> serviceClass,
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
