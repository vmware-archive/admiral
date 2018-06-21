/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model;

import com.vmware.photon.controller.model.monitoring.InMemoryResourceMetricService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.FirewallService;
import com.vmware.photon.controller.model.resources.IPAddressService;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService;
import com.vmware.photon.controller.model.resources.LoadBalancerService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.ResourceDescriptionService;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.RouterService;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.SubnetRangeService;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.xenon.common.ServiceHost;

/**
 * Helper class that starts all the photon model provisioning services
 */
public class PhotonModelServices {

    public static final String[] LINKS = {
            ComputeDescriptionService.FACTORY_LINK,
            ComputeService.FACTORY_LINK,
            ResourcePoolService.FACTORY_LINK,
            ResourceDescriptionService.FACTORY_LINK,
            DiskService.FACTORY_LINK,
            SnapshotService.FACTORY_LINK,
            NetworkInterfaceService.FACTORY_LINK,
            NetworkInterfaceDescriptionService.FACTORY_LINK,
            ResourceGroupService.FACTORY_LINK,
            NetworkService.FACTORY_LINK,
            SecurityGroupService.FACTORY_LINK,
            FirewallService.FACTORY_LINK,
            StorageDescriptionService.FACTORY_LINK,
            InMemoryResourceMetricService.FACTORY_LINK,
            EndpointService.FACTORY_LINK,
            ImageService.FACTORY_LINK,
            TagService.FACTORY_LINK,
            LoadBalancerDescriptionService.FACTORY_LINK,
            LoadBalancerService.FACTORY_LINK,
            RouterService.FACTORY_LINK };

    public static void startServices(ServiceHost host) throws Throwable {
        host.startFactory(new ComputeDescriptionService());
        host.startFactory(new ComputeService());
        host.startFactory(new ResourcePoolService());
        host.startFactory(new ResourceDescriptionService());
        host.startFactory(new DiskService());
        host.startFactory(new SnapshotService());
        host.startFactory(new NetworkInterfaceService());
        host.startFactory(new NetworkInterfaceDescriptionService());
        host.startFactory(new SubnetService());
        host.startFactory(new SubnetRangeService());
        host.startFactory(new IPAddressService());
        host.startFactory(new ResourceGroupService());
        host.startFactory(new NetworkService());
        host.startFactory(new FirewallService());
        host.startFactory(new SecurityGroupService());
        host.startFactory(new StorageDescriptionService());
        host.startFactory(new EndpointService());
        host.startFactory(new ImageService());
        host.startFactory(new InMemoryResourceMetricService());
        host.startFactory(TagService.class, TagFactoryService::new);
        host.startFactory(new LoadBalancerDescriptionService());
        host.startFactory(new LoadBalancerService());
        host.startFactory(new RouterService());
    }
}
