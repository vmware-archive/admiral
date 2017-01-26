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


import com.vmware.admiral.request.ClosureAllocationTaskService;
import com.vmware.admiral.request.ClosureProvisionTaskService;
import com.vmware.admiral.request.ClosureRemovalTaskFactoryService;
import com.vmware.admiral.request.ClusteringTaskService;
import com.vmware.admiral.request.ContainerAllocationTaskFactoryService;
import com.vmware.admiral.request.ContainerControlLoopService;
import com.vmware.admiral.request.ContainerHostRemovalTaskFactoryService;
import com.vmware.admiral.request.ContainerNetworkAllocationTaskService;
import com.vmware.admiral.request.ContainerNetworkProvisionTaskService;
import com.vmware.admiral.request.ContainerNetworkRemovalTaskService;
import com.vmware.admiral.request.ContainerOperationTaskFactoryService;
import com.vmware.admiral.request.ContainerPortsAllocationTaskService;
import com.vmware.admiral.request.ContainerRedeploymentTaskService;
import com.vmware.admiral.request.ContainerRemovalTaskFactoryService;
import com.vmware.admiral.request.ContainerVolumeAllocationTaskService;
import com.vmware.admiral.request.ContainerVolumeProvisionTaskService;
import com.vmware.admiral.request.ContainerVolumeRemovalTaskService;
import com.vmware.admiral.request.PlacementHostSelectionTaskService;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestStatusFactoryService;
import com.vmware.admiral.request.ReservationAllocationTaskService;
import com.vmware.admiral.request.ReservationRemovalTaskFactoryService;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.admiral.request.ResourceNamePrefixTaskService;
import com.vmware.admiral.request.ServiceDocumentDeleteTaskService;
import com.vmware.admiral.request.composition.CompositeComponentRemovalTaskService;
import com.vmware.admiral.request.composition.CompositionSubTaskFactoryService;
import com.vmware.admiral.request.composition.CompositionTaskFactoryService;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService;
import com.vmware.admiral.request.compute.ComputeNetworkAllocationTaskService;
import com.vmware.admiral.request.compute.ComputeNetworkProvisionTaskService;
import com.vmware.admiral.request.compute.ComputeNetworkRemovalTaskService;
import com.vmware.admiral.request.compute.ComputeOperationTaskService;
import com.vmware.admiral.request.compute.ComputePlacementSelectionTaskService;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService;
import com.vmware.admiral.request.compute.ComputeRemovalTaskService;
import com.vmware.admiral.request.compute.ComputeRemovalWatchService;
import com.vmware.admiral.request.compute.ComputeReservationTaskService;
import com.vmware.admiral.request.compute.ProvisionContainerHostsTaskService;
import com.vmware.admiral.request.notification.NotificationsService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class HostInitRequestServicesConfig extends HostInitServiceHelper {

    public static void startServices(ServiceHost host) {
        startServices(host,
                RequestBrokerFactoryService.class,
                ContainerAllocationTaskFactoryService.class,
                ReservationTaskFactoryService.class,
                ReservationRemovalTaskFactoryService.class,
                ContainerRemovalTaskFactoryService.class,
                ClosureRemovalTaskFactoryService.class,
                ContainerOperationTaskFactoryService.class,
                ContainerHostRemovalTaskFactoryService.class,
                CompositionSubTaskFactoryService.class,
                CompositionTaskFactoryService.class,
                RequestStatusFactoryService.class,
                NotificationsService.class,
                ComputeRemovalWatchService.class,
                RequestInitialBootService.class);

        startServiceFactories(host,
                ProvisionContainerHostsTaskService.class,
                ClosureAllocationTaskService.class,
                ClosureProvisionTaskService.class,
                ContainerRedeploymentTaskService.class,
                ContainerNetworkAllocationTaskService.class,
                ContainerNetworkProvisionTaskService.class,
                ComputeNetworkAllocationTaskService.class,
                ComputeNetworkProvisionTaskService.class,
                ComputeNetworkRemovalTaskService.class,
                ContainerNetworkRemovalTaskService.class,
                ContainerVolumeAllocationTaskService.class,
                ContainerVolumeProvisionTaskService.class,
                ContainerVolumeRemovalTaskService.class,
                ClusteringTaskService.class,
                ComputeAllocationTaskService.class,
                ComputeProvisionTaskService.class,
                ComputeReservationTaskService.class,
                ComputeRemovalTaskService.class,
                ComputeOperationTaskService.class,
                PlacementHostSelectionTaskService.class,
                ComputePlacementSelectionTaskService.class,
                ResourceNamePrefixTaskService.class,
                ReservationAllocationTaskService.class,
                CompositeComponentRemovalTaskService.class,
                ServiceDocumentDeleteTaskService.class,
                ContainerPortsAllocationTaskService.class,
                ContainerControlLoopService.class);

        // start initialization of system documents
        host.sendRequest(Operation.createPost(
                UriUtils.buildUri(host, RequestInitialBootService.class))
                .setReferer(host.getUri())
                .setBody(new ServiceDocument()));
    }
}
