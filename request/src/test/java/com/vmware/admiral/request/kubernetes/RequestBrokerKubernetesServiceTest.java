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

package com.vmware.admiral.request.kubernetes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;

import java.net.URI;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.host.HostInitKubernetesAdapterServiceConfig;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.RequestStatusService.RequestStatus;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState;
import com.vmware.admiral.request.composition.CompositionTaskFactoryService;
import com.vmware.admiral.request.composition.CompositionTaskService.CompositionTaskState;
import com.vmware.admiral.request.kubernetes.CompositeKubernetesProvisioningTaskService.KubernetesProvisioningTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.kubernetes.test.MockKubernetesApplicationAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class RequestBrokerKubernetesServiceTest extends RequestBaseTest {

    @Before
    public void setup() {
        HostInitKubernetesAdapterServiceConfig.startServices(host, true);
        MockKubernetesApplicationAdapterService.clear();
    }

    private static final String WP_K8S_TEMPLATE = "WordPress_with_MySQL_kubernetes.yaml";

    @Test
    public void testRequestLifeCycle() throws Throwable {
        doTestRequestLifeCycle(false);
    }

    @Test
    public void testRequestLifeCycleOnSpecificGroupPlacement() throws Throwable {
        doTestRequestLifeCycle(true);
    }

    private void doTestRequestLifeCycle(boolean sendGroupPlacementState) throws Throwable {
        host.log("########  Start of testRequestLifeCycle ######## ");
        // setup K8S Host:
        ResourcePoolState resourcePool = createResourcePool();
        createKubernetesHost(resourcePool);

        // setup Group Placement:
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);

        String template = CommonTestStateFactory.getFileContent(WP_K8S_TEMPLATE);
        CompositeDescription cd = createCompositeFromYaml(template);

        // 1. Request a container instance:
        RequestBrokerState request = TestRequestStateFactory.createRequestState();
        request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        request.resourceDescriptionLink = cd.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;

        if (sendGroupPlacementState) {
            // if this is set, the CompositeKubernetesProvisioningTaskService will
            // skip the reservation step and will use this group placement. This could
            // be done in order to test the compute host placement selection code that
            // would otherwise be skipped.
            request.groupResourcePlacementLink = groupPlacementState.documentSelfLink;
        }

        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // wait for request completed state:
        String requestSelfLink = request.documentSelfLink;
        request = waitForRequestToComplete(request);

        String provisioningSelfLink = UriUtils.buildUriPath(
                CompositeKubernetesProvisioningTaskService.FACTORY_LINK,
                extractId(requestSelfLink));

        // 2. Reservation stage:
        if (!sendGroupPlacementState) {
            String rsrvSelfLink = UriUtils.buildUriPath(ReservationTaskFactoryService.SELF_LINK,
                    extractId(requestSelfLink));
            ReservationTaskState rsrvTask = getDocument(ReservationTaskState.class, rsrvSelfLink);
            assertNotNull(rsrvTask);
            assertEquals(request.resourceDescriptionLink, rsrvTask.resourceDescriptionLink);
            assertEquals(provisioningSelfLink, rsrvTask.serviceTaskCallback.serviceSelfLink);
            assertEquals(request.tenantLinks, rsrvTask.tenantLinks);
        }

        // 3. Provisioning stage:
        String compositionTaskSelfLink = UriUtils.buildUriPath(
                CompositionTaskFactoryService.SELF_LINK,
                extractId(requestSelfLink));

        KubernetesProvisioningTaskState allocationTask = getDocument(
                KubernetesProvisioningTaskState.class, provisioningSelfLink);
        assertNotNull(allocationTask);
        assertEquals(request.resourceDescriptionLink, allocationTask.resourceDescriptionLink);
        assertEquals(compositionTaskSelfLink, allocationTask.serviceTaskCallback.serviceSelfLink);

        // 4. Composition stage:
        CompositionTaskState compositionTask = getDocument(
                CompositionTaskState.class, compositionTaskSelfLink);
        assertNotNull(compositionTask);
        assertEquals(request.resourceDescriptionLink, compositionTask.resourceDescriptionLink);
        assertEquals(requestSelfLink, compositionTask.serviceTaskCallback.serviceSelfLink);

        request = getDocument(RequestBrokerState.class, requestSelfLink);

        assertNotNull("ResourceLinks null for requestSelfLink: " + requestSelfLink,
                request.resourceLinks);
        assertEquals(1, request.resourceLinks.size());
        CompositeComponent state = getDocument(CompositeComponent.class,
                request.resourceLinks.iterator().next());
        assertNotNull(state);

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);
        assertEquals(Integer.valueOf(100), rs.progress);
    }

    protected ComputeState createKubernetesHost(ResourcePoolState resourcePoo)
            throws Throwable {

        ComputeDescription createDockerHostDescription = createDockerHostDescription();

        ComputeState compute = new ComputeState();
        compute.resourcePoolLink = resourcePool.documentSelfLink;
        compute.primaryMAC = UUID.randomUUID().toString();
        compute.powerState = PowerState.ON;
        compute.adapterManagementReference = URI.create("http://localhost:8081"); // not real reference
        compute.descriptionLink = createDockerHostDescription.documentSelfLink;

        compute.customProperties = new HashMap<>();
        compute.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.KUBERNETES.name());

        compute = getOrCreateDocument(compute, ComputeService.FACTORY_LINK);
        assertNotNull(compute);
        addForDeletion(compute);
        return compute;
    }

    private CompositeDescription createCompositeFromYaml(String yamlContent) throws Throwable {
        TestContext context = host.testCreate(1);
        AtomicReference<String> location = new AtomicReference<>();

        host.send(Operation.createPost(UriUtils.buildUri(host,
                CompositeDescriptionContentService.SELF_LINK))
                .setContentType(MEDIA_TYPE_APPLICATION_YAML)
                .forceRemote()
                .setBody(yamlContent)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        context.failIteration(e);
                    } else {
                        location.set(o.getResponseHeader(Operation.LOCATION_HEADER));
                        context.completeIteration();
                    }
                }));

        context.await();

        return getDocument(CompositeDescription.class, location.get());
    }

}
