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

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereAdapters;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;

public class HostInitPhotonModelServiceConfig {

    public static void startServices(ServiceHost host) throws Throwable {

        PhotonModelServices.startServices(host);
        PhotonModelTaskServices.startServices(host);

        // aws adapters:
        try {
            // start the aws instance service
            AWSAdapters.startServices(host);

        } catch (Throwable e) {
            host.log(Level.WARNING, "Exception staring AWS adapters: %s",
                    Utils.toString(e));
        }

        // azure adapters:
        try {
            // start the azure instance service
            AzureAdapters.startServices(host);

        } catch (Exception e) {
            host.log(Level.WARNING, "Exception staring Azure adapters: %s",
                    Utils.toString(e));
        }

        // vsphere adapters:
        try {
            // start the vsphere instance service
            VSphereAdapters.startServices(host);
        } catch (Throwable e) {
            host.log(Level.WARNING, "Exception staring vSphere adapters: %s",
                    Utils.toString(e));
        }
    }

}
