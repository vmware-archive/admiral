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

package com.vmware.iaas.consumer.api;

import java.util.logging.Level;

import io.swagger.models.Info;

import com.vmware.iaas.consumer.api.service.ComputeControllerService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.RootNamespaceService;
import com.vmware.xenon.swagger.SwaggerDescriptorService;

public class Host extends ServiceHost {

    public static void main(String[] args) throws Throwable {
        Host host = new Host();
        host.initialize(args).start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            host.log(Level.WARNING, "API Host Stopping ...");
            host.stop();
            host.log(Level.WARNING, "API Host Stopped");
        }));
    }

    public ServiceHost start() throws Throwable {
        SwaggerDescriptorService swagger = new SwaggerDescriptorService();
        Info info = new Info();
        info.setDescription("IaaS Consumer API");
        info.setTermsOfService("API Terms of Service");
        info.setTitle("IaaS Consumer API");
        info.setVersion("0.9");
        swagger.setExcludeUtilities(false);
        swagger.setInfo(info);
        super.start();

        super.startDefaultCoreServicesSynchronously();

        super.startService(new RootNamespaceService()); // Start the root namespace service.

        this.startService(swagger);

        // Start API services.

        this.startService(Operation.createPost(UriUtils.buildUri(this, ComputeControllerService.class)),
                new ComputeControllerService());

        return this;
    }

}
