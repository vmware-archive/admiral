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

package com.vmware.photon.controller.model.tasks;

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.factoryService;

import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsAggregationTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsAggregationTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.TaskFactoryService;

/**
 * Helper class that starts all the photon model Task services
 */
public class PhotonModelTaskServices {

    public static final ServiceMetadata[] SERVICES_METADATA = {
            factoryService(ResourceAllocationTaskService.class),
            factoryService(ResourceEnumerationTaskService.class),
            factoryService(ImageEnumerationTaskService.class),
            factoryService(ScheduledTaskService.class),
            factoryService(ResourceRemovalTaskService.class),
            factoryService(ProvisionComputeTaskService.class),
            factoryService(ProvisionNetworkTaskService.class),
            factoryService(ProvisionSubnetTaskService.class),
            factoryService(ProvisionLoadBalancerTaskService.class),
            factoryService(SnapshotTaskService.class),
            factoryService(ProvisionSecurityGroupTaskService.class),
            factoryService(StatsCollectionTaskService.class),
            factoryService(SingleResourceStatsCollectionTaskService.class),
            factoryService(StatsAggregationTaskService.class),
            factoryService(EndpointAllocationTaskService.class),
            factoryService(SingleResourceStatsAggregationTaskService.class),
            factoryService(SubTaskService.class)
    };

    public static final String[] LINKS = {
            SshCommandTaskService.FACTORY_LINK,
            ResourceAllocationTaskService.FACTORY_LINK,
            ResourceEnumerationTaskService.FACTORY_LINK,
            ImageEnumerationTaskService.FACTORY_LINK,
            ScheduledTaskService.FACTORY_LINK,
            ResourceRemovalTaskService.FACTORY_LINK,
            ProvisionComputeTaskService.FACTORY_LINK,
            ProvisionNetworkTaskService.FACTORY_LINK,
            ProvisionSubnetTaskService.FACTORY_LINK,
            ProvisionLoadBalancerTaskService.FACTORY_LINK,
            SnapshotTaskService.FACTORY_LINK,
            ProvisionSecurityGroupTaskService.FACTORY_LINK,
            StatsCollectionTaskService.FACTORY_LINK,
            SingleResourceStatsCollectionTaskService.FACTORY_LINK,
            StatsAggregationTaskService.FACTORY_LINK,
            EndpointAllocationTaskService.FACTORY_LINK,
            SingleResourceStatsAggregationTaskService.FACTORY_LINK,
            SubTaskService.FACTORY_LINK
    };

    public static void startServices(ServiceHost host) throws Throwable {

        host.startService(Operation.createPost(host,
                SshCommandTaskService.FACTORY_LINK),
                SshCommandTaskService.createFactory());
        host.startFactory(ResourceAllocationTaskService.class,
                () -> TaskFactoryService.create(ResourceAllocationTaskService.class));
        host.startFactory(ResourceEnumerationTaskService.class,
                () -> ResourceEnumerationTaskService.createFactory());
        host.startFactory(ImageEnumerationTaskService.class,
                () -> ImageEnumerationTaskService.createFactory());
        host.startFactory(ScheduledTaskService.class,
                () -> TaskFactoryService.create(ScheduledTaskService.class));
        host.startFactory(ResourceRemovalTaskService.class,
                () -> TaskFactoryService.create(ResourceRemovalTaskService.class));
        host.startFactory(ProvisionComputeTaskService.class,
                () -> TaskFactoryService.create(ProvisionComputeTaskService.class));
        host.startFactory(ProvisionNetworkTaskService.class,
                () -> TaskFactoryService.create(ProvisionNetworkTaskService.class));
        host.startFactory(ProvisionSubnetTaskService.class,
                () -> TaskFactoryService.create(ProvisionSubnetTaskService.class));
        host.startFactory(ProvisionLoadBalancerTaskService.class,
                () -> TaskFactoryService.create(ProvisionLoadBalancerTaskService.class));
        host.startFactory(SnapshotTaskService.class,
                () -> TaskFactoryService.create(SnapshotTaskService.class));
        host.startFactory(ProvisionSecurityGroupTaskService.class,
                () -> TaskFactoryService.create(ProvisionSecurityGroupTaskService.class));
        host.startFactory(EndpointAllocationTaskService.class,
                () -> TaskFactoryService.create(EndpointAllocationTaskService.class));
        host.startFactory(EndpointRemovalTaskService.class,
                () -> TaskFactoryService.create(EndpointRemovalTaskService.class));
        host.startFactory(SingleResourceStatsAggregationTaskService.class,
                () -> SingleResourceStatsAggregationTaskService.createFactory());
        host.startFactory(StatsAggregationTaskService.class,
                () -> StatsAggregationTaskService.createFactory());
        host.startFactory(SingleResourceStatsCollectionTaskService.class,
                () -> SingleResourceStatsCollectionTaskService.createFactory());
        host.startFactory(StatsCollectionTaskService.class,
                () -> StatsCollectionTaskService.createFactory());
        host.startFactory(SubTaskService.class,
                () -> TaskFactoryService.create(SubTaskService.class));
    }
}
