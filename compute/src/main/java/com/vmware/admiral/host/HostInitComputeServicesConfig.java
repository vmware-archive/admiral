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

import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescriptionFactoryService;
import com.vmware.admiral.compute.ConfigureHostOverSshTaskService;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneService;
import com.vmware.admiral.compute.EpzComputeEnumerationTaskService;
import com.vmware.admiral.compute.HostConfigCertificateDistributionService;
import com.vmware.admiral.compute.PlacementCapacityUpdateTaskService;
import com.vmware.admiral.compute.RegistryConfigCertificateDistributionService;
import com.vmware.admiral.compute.RegistryHostConfigService;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeDescriptionCloneService;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerLogService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerShellService;
import com.vmware.admiral.compute.container.DeploymentPolicyService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionFactoryService;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.HostNetworkListDataCollectionFactoryService;
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection.HostVolumeListDataCollectionFactoryService;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.admiral.compute.container.TemplateSearchService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.compute.content.TemplateComputeDescription;
import com.vmware.admiral.compute.endpoint.EndpointAdapterService;
import com.vmware.admiral.compute.env.ComputeProfileService;
import com.vmware.admiral.compute.env.EnvironmentMappingService;
import com.vmware.admiral.compute.env.EnvironmentService;
import com.vmware.admiral.compute.env.NetworkProfileService;
import com.vmware.admiral.compute.env.StorageProfileService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.service.test.MockConfigureHostOverSshTaskServiceWithoutValidate;
import com.vmware.admiral.service.test.MockContainerHostService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class HostInitComputeServicesConfig extends HostInitServiceHelper {

    public static void startServices(ServiceHost host, boolean startMockContainerHostService) {

        startServices(host,
                ContainerFactoryService.class,
                EndpointAdapterService.class,
                HostContainerListDataCollectionFactoryService.class,
                HostNetworkListDataCollectionFactoryService.class,
                HostVolumeListDataCollectionFactoryService.class,
                RegistryHostConfigService.class,
                CompositeDescriptionFactoryService.class,
                CompositeDescriptionCloneService.class,
                CompositeDescriptionContentService.class, TemplateSearchService.class,
                CompositeComponentFactoryService.class, ContainerLogService.class,
                ContainerShellService.class, ShellContainerExecutorService.class,
                HostConfigCertificateDistributionService.class,
                RegistryConfigCertificateDistributionService.class,
                ComputeInitialBootService.class,
                ElasticPlacementZoneConfigurationService.class,
                EnvironmentMappingService.class);

        startServiceFactories(host, CaSigningCertService.class,
                ContainerDescriptionService.class,
                GroupResourcePlacementService.class,
                ContainerHostDataCollectionService.class,
                EnvironmentService.class,
                ComputeProfileService.class,
                StorageProfileService.class,
                NetworkProfileService.class,
                DeploymentPolicyService.class,
                HostPortProfileService.class,
                ContainerNetworkService.class,
                ContainerNetworkDescriptionService.class,
                ComputeNetworkDescriptionService.class,
                ComputeNetworkService.class,
                ContainerVolumeDescriptionService.class,
                ContainerVolumeService.class,
                ContainerVolumeDescriptionService.class,
                ElasticPlacementZoneService.class,
                EpzComputeEnumerationTaskService.class,
                PlacementCapacityUpdateTaskService.class);

        if (startMockContainerHostService) {
            startServices(host, MockContainerHostService.class);
            startServiceFactories(host, MockConfigureHostOverSshTaskServiceWithoutValidate.class);
        } else {
            startServices(host, ContainerHostService.class);
            startServiceFactories(host, ConfigureHostOverSshTaskService.class);
        }

        initCompositeComponentRegistry();

        // start initialization of system documents
        host.sendRequest(Operation.createPost(
                UriUtils.buildUri(host, ComputeInitialBootService.class))
                .setReferer(host.getUri())
                .setBody(new ServiceDocument()));

    }

    public static void initCompositeComponentRegistry() {
        // register a well-know Components
        CompositeComponentRegistry.registerComponent(ResourceType.CONTAINER_TYPE.getName(),
                ContainerDescriptionService.FACTORY_LINK,
                ContainerDescription.class, ContainerFactoryService.SELF_LINK,
                ContainerState.class);
        CompositeComponentRegistry.registerComponent(ResourceType.CONTAINER_NETWORK_TYPE.getName(),
                ContainerNetworkDescriptionService.FACTORY_LINK, ContainerNetworkDescription.class,
                ContainerNetworkService.FACTORY_LINK, ContainerNetworkState.class);
        CompositeComponentRegistry.registerComponent(ResourceType.COMPUTE_TYPE.getName(),
                ComputeDescriptionService.FACTORY_LINK,
                TemplateComputeDescription.class,
                ComputeService.FACTORY_LINK,
                ComputeState.class,
                com.vmware.admiral.compute.content.TemplateComputeState.class);

        CompositeComponentRegistry.registerComponent(ResourceType.CONTAINER_VOLUME_TYPE.getName(),
                ContainerVolumeDescriptionService.FACTORY_LINK, ContainerVolumeDescription.class,
                ContainerVolumeService.FACTORY_LINK, ContainerVolumeState.class);

        CompositeComponentRegistry.registerComponent(ResourceType.CLOSURE_TYPE.getName(),
                ClosureDescriptionFactoryService.FACTORY_LINK, ClosureDescription.class,
                ClosureFactoryService.FACTORY_LINK, Closure.class);

        CompositeComponentRegistry.registerComponent(ResourceType.COMPUTE_NETWORK_TYPE.getName(),
                ComputeNetworkDescriptionService.FACTORY_LINK, ComputeNetworkDescription.class,
                ComputeNetworkService.FACTORY_LINK, ComputeNetwork.class);
    }
}
