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

import org.junit.Test;

import com.vmware.iaas.consumer.api.common.Constants.CustomPropertyKeys;
import com.vmware.iaas.consumer.api.model.BlockDevice;
import com.vmware.iaas.consumer.api.model.Image;
import com.vmware.iaas.consumer.api.model.LoadBalancer;
import com.vmware.iaas.consumer.api.model.Machine;
import com.vmware.iaas.consumer.api.model.MachineDisk;
import com.vmware.iaas.consumer.api.model.Network;
import com.vmware.iaas.consumer.api.model.NetworkInterface;
import com.vmware.iaas.consumer.api.model.specifications.BlockDeviceSpecification;
import com.vmware.iaas.consumer.api.model.specifications.DiskAttachmentSpecification;
import com.vmware.iaas.consumer.api.model.specifications.DiskAttachmentSpecification.BootConfig;
import com.vmware.iaas.consumer.api.model.specifications.LoadBalancerSpecification;
import com.vmware.iaas.consumer.api.model.specifications.MachineSpecification;
import com.vmware.iaas.consumer.api.model.specifications.NetConnectivitySpecification;
import com.vmware.iaas.consumer.api.model.specifications.NetworkSpecification;
import com.vmware.iaas.consumer.api.model.specifications.NetworkSpecification.IpAssignment;
import com.vmware.iaas.consumer.api.model.specifications.NetworkSpecification.NetworkType;

//TODO: remove this useless test class
public class ModelTest extends BaseServiceTest {
    @Test
    public void testCanInstanciateResourceModels() throws Throwable {
        BlockDevice dev = new BlockDevice();
        Image img = new Image();
        LoadBalancer lb = new LoadBalancer();
        Machine mach = new Machine();
        MachineDisk machDisk = new MachineDisk();
        Network net = new Network();
        NetworkInterface ni = new NetworkInterface();

        String apiContextKey = CustomPropertyKeys.API_CONTEXT_ID;
    }

    @Test
    public void testCanInstanciateSpecModels() throws Throwable {
        BlockDeviceSpecification dev = new BlockDeviceSpecification();
        LoadBalancerSpecification lb = new LoadBalancerSpecification();
        MachineSpecification mach = new MachineSpecification();
        DiskAttachmentSpecification da = new DiskAttachmentSpecification();
        da.bootConfig = new BootConfig();
        NetConnectivitySpecification net = new NetConnectivitySpecification();
        NetworkSpecification ni = new NetworkSpecification();
        ni.assignment = IpAssignment.DYNAMIC;
        ni.networkType = NetworkType.ISOLATED;
    }
}
