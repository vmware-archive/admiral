/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.iaas.consumer.api.service;

import java.util.concurrent.TimeUnit;

import org.junit.Before;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.security.PhotonModelSecurityServices;
import com.vmware.xenon.services.common.RootNamespaceService;

public class BaseServiceTest extends BaseTestCase {
    //protected Host host;

    @Before
    public void setupBaseServiceTest() throws Throwable {
        //this.host = new Host();
        //this.host.initialize(new String[] {}).start();

        this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(10));
        //this.host.setTimeoutSeconds(this.requestTimeout);

        this.host.startService(new RootNamespaceService()); // Start the root namespace service.
        PhotonModelServices.startServices(this.host);
        PhotonModelSecurityServices.startServices(this.host);
        //PhotonModelTaskServices.startServices(this.host);
        //PhotonModelAdaptersRegistryAdapters.startServices(this.host);

        waitForServiceAvailability(PhotonModelServices.LINKS);
        waitForServiceAvailability(PhotonModelSecurityServices.LINKS);
        //this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
        //this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);

    }
}
