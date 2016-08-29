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

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigFactoryService;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService.ContainerNetworkConfigState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.test.MockSystemContainerConfig;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

public class ContainerServiceLinkProcessingTaskTest extends RequestBaseTest {
    private static final String TEST_DEP_NAME = "mydep";
    private static final String TEST_DEP_ALIAS = "myalias";
    private static final String TEST_DEP_CONTAINER_PORT = "80/udp";
    private static final int EXPECTED_EXTRA_HOSTS_COUNT = 1;

    private RequestBrokerState request;
    private ContainerState depContainerState;
    private MockContainerHostNetworkConfigService mockContainerHostNetworkConfigService;
    private CompositeDescription composite;

    @Override
    public void setUp() throws Throwable {
        super.setUp();

        // first request - create the dependency
        ContainerDescription depDesc = new ContainerDescription();
        depDesc.name = TEST_DEP_NAME;
        depDesc.image = "busybox";
        depDesc.portBindings = new PortBinding[] { PortBinding
                .fromDockerPortMapping(DockerPortMapping.fromString(TEST_DEP_CONTAINER_PORT)) };

        depDesc = doPost(depDesc, ContainerDescriptionService.FACTORY_LINK);

        composite.descriptionLinks.add(depDesc.documentSelfLink);
        composite = doPut(composite);

        String contextId = UUID.randomUUID().toString();
        RequestBrokerState depRequest = TestRequestStateFactory.createRequestState();
        depRequest.resourceDescriptionLink = depDesc.documentSelfLink;
        depRequest.tenantLinks = groupPolicyState.tenantLinks;
        depRequest.resourceCount = 1;
        depRequest.customProperties = new HashMap<>();
        depRequest.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        depRequest = startRequest(depRequest);
        waitForRequestToComplete(depRequest);

        depRequest = getDocument(RequestBrokerState.class, depRequest.documentSelfLink);

        List<String> depContainerStateLinks = findResourceLinks(ContainerState.class,
                depRequest.resourceLinks);

        assertEquals(depRequest.resourceCount, depContainerStateLinks.size());
        depContainerState = getDocument(ContainerState.class,
                depContainerStateLinks.get(0));
        assertNotNull(depContainerState);

        // second request - using the same context id, with a service link to the container from the
        // first request
        request = TestRequestStateFactory.createRequestState();
        request.resourceDescriptionLink = containerDesc.documentSelfLink;
        request.customProperties = new HashMap<>();
        request.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        request.resourceCount = 1;

        mockContainerHostNetworkConfigService = new MockContainerHostNetworkConfigService();
        String path = UriUtils.buildUriPath(ContainerHostNetworkConfigFactoryService.SELF_LINK,
                Service.getId(computeHost.documentSelfLink));
        host.startService(Operation.createPost(UriUtilsExtended.buildUri(host, path)),
                mockContainerHostNetworkConfigService);
    }

    @Override
    protected synchronized ContainerDescription createContainerDescription() throws Throwable {
        if (containerDesc == null) {
            ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
            desc.portBindings = null;
            desc.documentSelfLink = UUID.randomUUID().toString();
            desc.links = new String[] { TEST_DEP_NAME + ":" + TEST_DEP_ALIAS };
            desc.extraHosts = null;
            containerDesc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
            assertNotNull(containerDesc);

            composite = TestRequestStateFactory.createCompositeDescription();
            composite.descriptionLinks = new ArrayList<>();
            composite.descriptionLinks.add(containerDesc.documentSelfLink);
            composite = doPost(composite, CompositeDescriptionFactoryService.SELF_LINK);
            addForDeletion(composite);
        }
        return containerDesc;
    }

    private RequestBrokerState alocateAndCreate(RequestBrokerState request) throws Throwable {
        //create allocation request first
        request.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                Boolean.TRUE.toString());
        request = doPost(request, RequestBrokerFactoryService.SELF_LINK);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);

        // Request provisioning after allocation:
        RequestBrokerState provisioningRequest = new RequestBrokerState();
        provisioningRequest.resourceType = request.resourceType;
        provisioningRequest.resourceLinks = request.resourceLinks;
        provisioningRequest.resourceDescriptionLink = containerDesc.documentSelfLink;
        provisioningRequest.operation = ContainerOperationType.CREATE.id;
        provisioningRequest.customProperties = new HashMap<>();
        provisioningRequest.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY,
                request.customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY));
        provisioningRequest.resourceCount = 1;
        provisioningRequest = doPost(provisioningRequest, RequestBrokerFactoryService.SELF_LINK);
        waitForRequestToComplete(provisioningRequest);

        return getDocument(RequestBrokerState.class, provisioningRequest.documentSelfLink);
    }

    @Test
    public void testServiceLinkProcessingWithPostAllocaton() throws Throwable {
        // verify the resources are created as expected:
        request = alocateAndCreate(request);
        assertEquals(request.resourceCount, request.resourceLinks.size());
        List<String> containerStateLinks = findResourceLinks(ContainerState.class,
                request.resourceLinks);

        ContainerState containerState = getDocument(ContainerState.class,
                containerStateLinks.get(0));

        assertContainerWithExtraHostsAndInternalLinks(containerState);
    }

    @Test
    public void testServiceLinkProcessingAfterScaleUp() throws Throwable {
        request = alocateAndCreate(request);

        List<String> containerStateLinks = findResourceLinks(ContainerState.class,
                request.resourceLinks);

        ContainerState containerState = getDocument(ContainerState.class,
                containerStateLinks.get(0));

        String compositeContextId = containerState.customProperties
                .get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY);

        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = containerDesc.documentSelfLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        day2OperationClustering.resourceCount = 2;
        day2OperationClustering.customProperties = new HashMap<>();
        day2OperationClustering.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY,
                compositeContextId);

        day2OperationClustering = startRequest(day2OperationClustering);
        waitForRequestToComplete(day2OperationClustering);

        List<ContainerState> containerStates = getContainerStates(containerDesc.documentSelfLink);
        assertEquals(2, containerStates.size());

        for (ContainerState cs : containerStates) {
            assertContainerWithExtraHostsAndInternalLinks(cs);
        }
    }

    @Test
    public void testServiceLinkReconfigureAfterContainerNetworkChange() throws Throwable {
        request = alocateAndCreate(request);

        List<String> containerStateLinks = findResourceLinks(ContainerState.class,
                request.resourceLinks);
        ContainerState containerState = getDocument(ContainerState.class,
                containerStateLinks.get(0));

        ContainerNetworkConfigState containerNetworkConfigState = mockContainerHostNetworkConfigService
                .getConfig(containerState.documentSelfLink);
        String internalLink = containerNetworkConfigState.internalServiceNetworkLinks.iterator()
                .next();

        assertEquals(String.format("%s:%s:%s:%s", containerState.names.get(0),
                depContainerState.ports.get(0).containerPort, computeHost.address,
                depContainerState.ports.get(0).hostPort), internalLink);

        String newPort = String
                .valueOf(Integer.parseInt(depContainerState.ports.get(0).hostPort) + 1);

        assertNotEquals(String.format("%s:%s:%s:%s", containerState.names.get(0),
                depContainerState.ports.get(0).containerPort, computeHost.address,
                newPort),
                internalLink);

        ContainerState patchState = new ContainerState();
        patchState.ports = depContainerState.ports;
        patchState.ports.get(0).hostPort = newPort;

        URI uri = UriUtils.buildUri(host, depContainerState.documentSelfLink);
        doOperation(patchState, uri, false, Action.PATCH);

        waitFor(() -> {
            ContainerNetworkConfigState newContainerNetworkConfigState = mockContainerHostNetworkConfigService
                    .getConfig(containerState.documentSelfLink);

            String newInternalLink = newContainerNetworkConfigState.internalServiceNetworkLinks
                    .iterator().next();

            return newInternalLink.equals(String.format("%s:%s:%s:%s", containerState.names.get(0),
                    depContainerState.ports.get(0).containerPort, computeHost.address,
                    newPort));

        });
    }

    private void assertContainerWithExtraHostsAndInternalLinks(ContainerState containerState) {
        // check that an ExtraHost mapping has been added
        int previousExtraHosts = Optional.ofNullable(containerDesc.extraHosts)
                .orElse(new String[0]).length;

        assertEquals("number of extra hosts", previousExtraHosts + EXPECTED_EXTRA_HOSTS_COUNT,
                containerState.extraHosts.length);

        String[] split = containerState.extraHosts[0].split(":", 2);
        assertEquals("unexpected alias hostname in extra hosts", TEST_DEP_ALIAS, split[0]);
        assertEquals("unexpected address in extra hosts",
                MockSystemContainerConfig.NETWORK_ADDRESS,
                split[1]);

        ContainerNetworkConfigState containerNetworkConfigState = mockContainerHostNetworkConfigService
                .getConfig(containerState.documentSelfLink);

        assertNotNull(containerNetworkConfigState);
        assertEquals(1, containerNetworkConfigState.internalServiceNetworkLinks.size());
        assertEquals(String.format("%s:%s:%s:%s", containerState.names.get(0),
                depContainerState.ports.get(0).containerPort, computeHost.address,
                depContainerState.ports.get(0).hostPort),
                containerNetworkConfigState.internalServiceNetworkLinks.iterator().next());
    }

    private List<ContainerState> getContainerStates(String containerDescriptionLink)
            throws Throwable {
        ServiceDocumentQuery<ContainerState> query = new ServiceDocumentQuery<>(host,
                ContainerState.class);

        QueryTask queryTask = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_DESCRIPTION_LINK, containerDescriptionLink);
        queryTask.querySpec.options = EnumSet.of(QueryOption.EXPAND_CONTENT);

        host.testStart(1);
        List<ContainerState> containerStates = new ArrayList<>();
        query.query(queryTask, (r) -> {
            if (r.hasException()) {
                host.failIteration(r.getException());
            } else if (r.hasResult()) {
                containerStates.add(r.getResult());
            } else {
                host.completeIteration();
            }
        });
        host.testWait();

        return containerStates;
    }
}
