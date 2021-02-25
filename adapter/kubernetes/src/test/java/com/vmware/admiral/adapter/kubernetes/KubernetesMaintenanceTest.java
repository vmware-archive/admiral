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

package com.vmware.admiral.adapter.kubernetes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.KubernetesOperationType;
import com.vmware.admiral.adapter.kubernetes.service.KubernetesAdapterService;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.kubernetes.entities.common.ObjectMeta;
import com.vmware.admiral.compute.kubernetes.entities.pods.Container;
import com.vmware.admiral.compute.kubernetes.entities.pods.Pod;
import com.vmware.admiral.compute.kubernetes.entities.pods.PodSpec;
import com.vmware.admiral.compute.kubernetes.service.PodFactoryService;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.host.ComputeInitialBootService;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitKubernetesAdapterServiceConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.service.test.MockKubernetesAdapterService;

public class KubernetesMaintenanceTest extends ComputeBaseTest {

    @Before
    @Override
    public void beforeForComputeBase() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(false);
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitTestDcpServicesConfig.startServices(host);
        HostInitCommonServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, true);
        HostInitKubernetesAdapterServiceConfig.startServices(host, true);
        waitForServiceAvailability(ComputeInitialBootService.SELF_LINK);
        waitForInitialBootServiceToBeSelfStopped(ComputeInitialBootService.SELF_LINK);
        waitForServiceAvailability(PodFactoryService.SELF_LINK);
        waitForServiceAvailability(KubernetesAdapterService.SELF_LINK);
    }

    @Test
    public void testAdapterRequestOnPeriodicMaintenance() throws Throwable {
        PodState podState = new PodState();
        podState.pod = new Pod();
        podState.pod.spec = new PodSpec();
        podState.pod.spec.containers = new ArrayList<>();
        Container container1 = new Container();
        container1.name = "container1";
        container1.image = "test-image";
        podState.pod.spec.containers.add(container1);
        podState.pod.metadata = new ObjectMeta();
        podState.pod.metadata.selfLink = "/api/v1/namespaces/default/pods/test-pod";
        podState.pod.metadata.name = "test-pod";
        podState = doPost(podState, PodFactoryService.SELF_LINK);

        waitFor(() -> MockKubernetesAdapterService.requestOnInspect != null);

        assertNotNull(MockKubernetesAdapterService.requestOnInspect);
        assertEquals(KubernetesOperationType.INSPECT.id, MockKubernetesAdapterService
                .requestOnInspect.operationTypeId);
        assertEquals(podState.documentSelfLink, MockKubernetesAdapterService.requestOnInspect
                .resourceReference.getPath());
        MockKubernetesAdapterService.requestOnInspect = null;
    }

}
