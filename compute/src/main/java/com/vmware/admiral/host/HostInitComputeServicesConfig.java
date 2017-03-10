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
import com.vmware.admiral.compute.ComputeSearchService;
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
import com.vmware.admiral.compute.container.HostContainerListDataCollection;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection;
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection;
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
import com.vmware.admiral.compute.endpoint.EndpointHealthCheckTaskService;
import com.vmware.admiral.compute.kubernetes.KubernetesEntityDataCollection;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService.DeploymentState;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionContentService;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.service.PodLogService;
import com.vmware.admiral.compute.kubernetes.service.PodService;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.compute.kubernetes.service.ReplicaSetService;
import com.vmware.admiral.compute.kubernetes.service.ReplicaSetService.ReplicaSetState;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService.ReplicationControllerState;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler.ServiceState;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.compute.profile.ComputeProfileService;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.ProfileMappingService;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.service.common.EventTopicRegistrationBootstrapService;
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
                RegistryHostConfigService.class,
                CompositeDescriptionFactoryService.class,
                CompositeDescriptionCloneService.class,
                CompositeDescriptionContentService.class, TemplateSearchService.class,
                CompositeComponentFactoryService.class, ContainerLogService.class,
                ContainerShellService.class, ShellContainerExecutorService.class,
                HostConfigCertificateDistributionService.class,
                RegistryConfigCertificateDistributionService.class,
                ComputeInitialBootService.class,
                ComputeSearchService.class,
                ElasticPlacementZoneConfigurationService.class,
                ProfileMappingService.class,
                KubernetesDescriptionContentService.class,
                PodLogService.class);

        startServiceFactories(host, CaSigningCertService.class,
                ContainerDescriptionService.class,
                GroupResourcePlacementService.class,
                KubernetesEntityDataCollection.class,
                HostContainerListDataCollection.class,
                HostNetworkListDataCollection.class,
                HostVolumeListDataCollection.class,
                ContainerHostDataCollectionService.class,
                ProfileService.class,
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
                PlacementCapacityUpdateTaskService.class,
                KubernetesDescriptionService.class,
                EndpointHealthCheckTaskService.class,
                PodService.class,
                DeploymentService.class,
                ReplicationControllerService.class,
                ServiceEntityHandler.class,
                ReplicaSetService.class,
                EventTopicRegistrationBootstrapService.class);




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
        CompositeComponentRegistry.registerComponent(ResourceType.NETWORK_TYPE.getName(),
                ContainerNetworkDescriptionService.FACTORY_LINK, ContainerNetworkDescription.class,
                ContainerNetworkService.FACTORY_LINK, ContainerNetworkState.class);
        CompositeComponentRegistry.registerComponent(ResourceType.COMPUTE_TYPE.getName(),
                ComputeDescriptionService.FACTORY_LINK,
                TemplateComputeDescription.class,
                ComputeService.FACTORY_LINK,
                ComputeState.class,
                com.vmware.admiral.compute.content.TemplateComputeState.class);

        CompositeComponentRegistry.registerComponent(ResourceType.VOLUME_TYPE.getName(),
                ContainerVolumeDescriptionService.FACTORY_LINK, ContainerVolumeDescription.class,
                ContainerVolumeService.FACTORY_LINK, ContainerVolumeState.class);

        CompositeComponentRegistry.registerComponent(ResourceType.CLOSURE_TYPE.getName(),
                ClosureDescriptionFactoryService.FACTORY_LINK, ClosureDescription.class,
                ClosureFactoryService.FACTORY_LINK, Closure.class);

        CompositeComponentRegistry.registerComponent(ResourceType.COMPUTE_NETWORK_TYPE.getName(),
                ComputeNetworkDescriptionService.FACTORY_LINK, ComputeNetworkDescription.class,
                ComputeNetworkService.FACTORY_LINK, ComputeNetwork.class);

        CompositeComponentRegistry.registerComponent(ResourceType.KUBERNETES_POD_TYPE.getName(),
                KubernetesDescriptionService.FACTORY_LINK, KubernetesDescription.class,
                PodService.FACTORY_LINK, PodState.class);

        CompositeComponentRegistry.registerComponent(ResourceType.KUBERNETES_DEPLOYMENT_TYPE.getName(),
                KubernetesDescriptionService.FACTORY_LINK, KubernetesDescription.class,
                DeploymentService.FACTORY_LINK, DeploymentState.class);

        CompositeComponentRegistry.registerComponent(ResourceType.KUBERNETES_SERVICE_TYPE.getName(),
                KubernetesDescriptionService.FACTORY_LINK, KubernetesDescription.class,
                ServiceEntityHandler.FACTORY_LINK, ServiceState.class);

        CompositeComponentRegistry.registerComponent(ResourceType.KUBERNETES_REPLICATION_CONTROLLER_TYPE.getName(),
                KubernetesDescriptionService.FACTORY_LINK, KubernetesDescription.class,
                ReplicationControllerService.FACTORY_LINK, ReplicationControllerState.class);

        CompositeComponentRegistry.registerComponent(ResourceType.KUBERNETES_REPLICA_SET_TYPE.getName(),
                KubernetesDescriptionService.FACTORY_LINK, KubernetesDescription.class,
                ReplicaSetService.FACTORY_LINK, ReplicaSetState.class);
    }
}
