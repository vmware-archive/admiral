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

import com.vmware.admiral.adapter.etcd.service.EtcdEmulatorService;
import com.vmware.admiral.adapter.etcd.service.EtcdMembersService;
import com.vmware.admiral.adapter.etcd.service.KVStoreFactoryService;
import com.vmware.xenon.common.ServiceHost;

public class HostInitEtcdAdapterServiceConfig {

    public static void startServices(ServiceHost host, boolean startEtcdEmulator) {
        if (startEtcdEmulator) {
            HostInitServiceHelper.startServices(host, EtcdEmulatorService.class,
                    EtcdMembersService.class,
                    KVStoreFactoryService.class);
            host.log(Level.INFO, "etcd emulator services started.");
        }
    }
}
