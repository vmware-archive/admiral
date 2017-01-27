/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.cd;

import static org.junit.Assert.assertNotNull;

import org.junit.Before;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeBackgroundServicesConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitContinuousDeliveryServicesConfig;
import com.vmware.admiral.host.HostInitDockerAdapterServiceConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.host.HostInitRequestServicesConfig;
import com.vmware.admiral.request.ClusteringTaskService;
import com.vmware.admiral.request.ContainerAllocationTaskFactoryService;
import com.vmware.admiral.request.ContainerHostRemovalTaskFactoryService;
import com.vmware.admiral.request.ContainerRemovalTaskFactoryService;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.ReservationRemovalTaskFactoryService;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.admiral.request.composition.CompositionSubTaskFactoryService;
import com.vmware.admiral.request.composition.CompositionTaskFactoryService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.AuthCredentialsService;

public abstract class ContinuousDeliveryBaseTest extends BaseTestCase {

    @Before
    public void setUp() throws Throwable {
        startServices(host);
        MockDockerAdapterService.resetContainers();
    }

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    protected void startServices(ServiceHost serviceHost) throws Throwable {
        // speed up the test (default is 500ms):
        setFinalStatic(QueryUtil.class
                .getDeclaredField("QUERY_RETRY_INTERVAL_MILLIS"), 20L);

        HostInitTestDcpServicesConfig.startServices(serviceHost);
        HostInitPhotonModelServiceConfig.startServices(serviceHost);
        HostInitCommonServiceConfig.startServices(serviceHost);
        HostInitComputeServicesConfig.startServices(serviceHost, false);
        HostInitComputeBackgroundServicesConfig.startServices(serviceHost);
        HostInitRequestServicesConfig.startServices(serviceHost);
        HostInitDockerAdapterServiceConfig.startServices(serviceHost, true);
        HostInitContinuousDeliveryServicesConfig.startServices(serviceHost);

        // request tasks:
        waitForServiceAvailability(RequestBrokerFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainerAllocationTaskFactoryService.SELF_LINK);
        waitForServiceAvailability(ReservationTaskFactoryService.SELF_LINK);
        waitForServiceAvailability(ReservationRemovalTaskFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainerRemovalTaskFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainerHostRemovalTaskFactoryService.SELF_LINK);
        waitForServiceAvailability(CompositionSubTaskFactoryService.SELF_LINK);
        waitForServiceAvailability(CompositionTaskFactoryService.SELF_LINK);
        waitForServiceAvailability(ClusteringTaskService.FACTORY_LINK);

        // admiral states:
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
        waitForServiceAvailability(RegistryService.FACTORY_LINK);

        // Request Allocation (enatai) services:
        waitForServiceAvailability(AuthCredentialsService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);

        // continuous delivery services:
        waitForServiceAvailability(SelfProvisioningTaskService.FACTORY_LINK);

        // Default services:
        waitForServiceAvailability(ResourceNamePrefixService.DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK);
    }

    protected void waitForContainerPowerState(final PowerState expectedPowerState,
            String containerLink) throws Throwable {
        assertNotNull(containerLink);
        waitFor(() -> {
            ContainerState container = getDocument(ContainerState.class, containerLink);
            assertNotNull(container);
            if (container.powerState != expectedPowerState) {
                host.log(
                        "Container PowerState is: %s. Expected powerState: %s. Retrying for container: %s...",
                        container.powerState, expectedPowerState, container.documentSelfLink);
                return false;
            }
            return true;
        });
    }

}
