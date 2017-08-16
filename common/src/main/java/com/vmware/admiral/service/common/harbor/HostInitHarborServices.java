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

package com.vmware.admiral.service.common.harbor;

import com.vmware.admiral.host.HostInitServiceHelper;
import com.vmware.admiral.service.common.harbor.mock.MockHarborApiProxyService;
import com.vmware.xenon.common.ServiceHost;

public class HostInitHarborServices {

    public static void startServices(ServiceHost host) {
        startServices(host, false);
    }

    public static void startServices(ServiceHost host, boolean mockHbrApiProxyService) {

        if (mockHbrApiProxyService) {
            HostInitServiceHelper.startService(host, MockHarborApiProxyService.class,
                    new MockHarborApiProxyService());
        } else {
            HostInitServiceHelper.startService(host, HarborApiProxyService.class,
                    new HarborApiProxyService());
        }

        HostInitServiceHelper.startService(host, HarborInitRegistryService.class,
                new HarborInitRegistryService());
    }

}