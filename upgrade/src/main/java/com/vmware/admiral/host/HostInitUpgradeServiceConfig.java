/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.factoryService;
import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import com.vmware.admiral.upgrade.transformation.CompositeComponentsTransformationService;
import com.vmware.admiral.upgrade.transformation.CompositeDescriptionTransformationService;
import com.vmware.admiral.upgrade.transformation.ComputePlacementPoolRelationTransformationService;
import com.vmware.admiral.upgrade.transformation.ContainerNetworksTransformationService;
import com.vmware.admiral.upgrade.transformation.ContainerVolumesTransformationService;
import com.vmware.admiral.upgrade.transformation.ContainersTransformationService;
import com.vmware.admiral.upgrade.transformation.ProjectsTransformationBootstrapService;
import com.vmware.admiral.upgrade.transformation.ResourcePoolTransformationService;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.ServiceHost;

public class HostInitUpgradeServiceConfig extends HostInitServiceHelper {

    public static final Collection<ServiceMetadata> SERVICES_METADATA = Collections
            .unmodifiableList(Arrays.asList(
                    service(CompositeDescriptionTransformationService.class),
                    service(ContainersTransformationService.class),
                    service(ComputePlacementPoolRelationTransformationService.class),
                    service(ContainerVolumesTransformationService.class),
                    service(ContainerNetworksTransformationService.class),
                    service(CompositeComponentsTransformationService.class),
                    service(ResourcePoolTransformationService.class),
                    factoryService(ProjectsTransformationBootstrapService.class)));

    public static void startServices(ServiceHost host) {

        startServices(host,
                CompositeDescriptionTransformationService.class,
                ContainersTransformationService.class,
                ComputePlacementPoolRelationTransformationService.class,
                ContainerVolumesTransformationService.class,
                ContainerNetworksTransformationService.class,
                CompositeComponentsTransformationService.class,
                ResourcePoolTransformationService.class);

        startServiceFactories(host,
                ProjectsTransformationBootstrapService.class);
    }
}
