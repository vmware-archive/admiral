/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.kubernetes;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService;
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
import com.vmware.xenon.common.ServiceDocument;
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

    @Test
    public void testRequestStateHasK8sInfo() throws Throwable {
        // setup K8S Host:
        ResourcePoolState resourcePool = createResourcePool();
        createKubernetesHost(resourcePool);

        // setup Group Placement:
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);

        String template = CommonTestStateFactory.getFileContent(WP_K8S_TEMPLATE);
        CompositeDescription cd = createCompositeFromYaml(template);
        cd.tenantLinks = groupPlacementState.tenantLinks;
        cd = doPut(cd);

        assertDocumentsCount(0, DeploymentService.DeploymentState.class);

        // request a container instance:
        RequestBrokerState request = TestRequestStateFactory.createRequestState();
        request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        request.resourceDescriptionLink = cd.documentSelfLink;
        request.groupResourcePlacementLink = groupPlacementState.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        request = startRequest(request);
        request = waitForRequestToComplete(request);

        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);

        assertNotNull(rs);
        assertNotNull(rs.resourceLinks);

        long numberOfDeployments = YamlMapper.splitYaml(template).stream()
                .filter(entity -> entity.contains(KubernetesUtil.DEPLOYMENT_TYPE))
                .count();
        assertEquals(1, MockKubernetesApplicationAdapterService.getProvisionedComponents().size());
        assertEquals(numberOfDeployments, MockKubernetesApplicationAdapterService.getCreatedDeploymentStates().size());
        assertEquals(numberOfDeployments, rs.resourceLinks.size());
        assertTrue(rs.resourceLinks.stream().allMatch(l -> l.contains(ManagementUriParts.KUBERNETES_DEPLOYMENTS)));

        assertDocumentsCount(numberOfDeployments, DeploymentService.DeploymentState.class);
        assertRightResourceLinks(rs.resourceLinks, DeploymentService.DeploymentState.class);
        assertDeploymentAreFromTheSameCompositeComponent();
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
        CompositeComponent state = getDocumentNoWait(CompositeComponent.class,
                request.resourceLinks.iterator().next());
        assertNull(state);

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);
        assertEquals(Integer.valueOf(100), rs.progress);
    }

    protected ComputeState createKubernetesHost(ResourcePoolState resourcePool)
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

    private void assertDocumentsCount(long expectedCount, Class<? extends ServiceDocument> documentType) throws Throwable {
        long foundCount = getDocumentLinksOfType(documentType).stream()
                .map(link -> {
                    try {
                        return getDocument(documentType, link);
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .count();

        assertEquals(expectedCount, foundCount);
    }

    private void assertDeploymentAreFromTheSameCompositeComponent() throws Throwable {
        List<DeploymentService.DeploymentState> deploymentStates = getDocumentLinksOfType(DeploymentService.DeploymentState.class).stream()
                .map(link -> {
                    try {
                        return getDocument(DeploymentService.DeploymentState.class, link);
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                        return null;
                    }
                }).collect(Collectors.toList());

        String compositeComponentLink = deploymentStates.get(0).compositeComponentLink;
        assertNotNull(compositeComponentLink);
        assertTrue(deploymentStates.stream().allMatch(state -> {
            assertNotNull(state.compositeComponentLink);
            return state.compositeComponentLink.equals(compositeComponentLink);
        }));
    }

    private void assertRightResourceLinks(Collection<String> links, Class<? extends ServiceDocument> type) throws Throwable {
        assertNotNull("The passed collection of links is null", links);
        Collection<String> extractedLinks = getDocumentLinksOfType(type);
        assertNotNull(String.format("Couldn't retrieve document links of type: %s", type.getName()), extractedLinks);
        assertEquals(links.size(), extractedLinks.size());
        assertTrue(links.containsAll(extractedLinks));
    }

}
