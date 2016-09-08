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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigFactoryService;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService.ContainerNetworkConfigState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ExposedServiceDescriptionService.ExposedServiceDescriptionFactoryService;
import com.vmware.admiral.compute.container.ExposedServiceDescriptionService.ExposedServiceDescriptionState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.ServiceAddressConfig;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

public class ContainerExposeServiceProcessingTaskTest extends RequestBaseTest {
    private RequestBrokerState request;
    private MockContainerHostNetworkConfigService mockContainerHostNetworkConfigService;
    private ServiceAddressConfig serviceAddressWithFormat;
    private ServiceAddressConfig serviceAddressWithoutFormat;
    private CompositeDescription composite;
    private static final int CLUSTER_SIZE = 3;

    private static final String SERVICE_ADDRESS_ALIAS_FORMAT = "public-web-service-%s.com";
    private static final String SERVICE_ADDRESS_ALIAS_FORMAT_REGEXP = "public-web-service-mcm.*\\.com";

    private static final String SERVICE_ADDRESS_ALIAS_NOFORMAT = "public-web-service.com";
    private static final String SERVICE_ADDRESS_ALIAS_NOFORMAT_REGEXP = "public-web-service\\.com-mcm.*";

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();

        createCompositeDesc(containerDesc);

        // second request - using the same context id, with a service link to the container from the
        // first request
        request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), composite.documentSelfLink);
        request.customProperties = new HashMap<>();

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
            desc.documentSelfLink = UUID.randomUUID().toString();
            desc._cluster = CLUSTER_SIZE;

            desc.portBindings = Arrays.stream(new String[] {
                    "127.0.0.1::80" })
                    .map((s) -> PortBinding.fromDockerPortMapping(DockerPortMapping.fromString(s)))
                    .collect(Collectors.toList())
                    .toArray(new PortBinding[0]);

            serviceAddressWithFormat = new ServiceAddressConfig();
            serviceAddressWithFormat.address = SERVICE_ADDRESS_ALIAS_FORMAT;
            serviceAddressWithFormat.port = "80";

            serviceAddressWithoutFormat = new ServiceAddressConfig();
            serviceAddressWithoutFormat.address = SERVICE_ADDRESS_ALIAS_NOFORMAT;
            serviceAddressWithoutFormat.port = "80";

            desc.exposeService = new ServiceAddressConfig[] { serviceAddressWithFormat,
                    serviceAddressWithoutFormat };
            containerDesc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
            assertNotNull(containerDesc);
        }
        return containerDesc;
    }

    private void createCompositeDesc(ContainerDescription containerDescription) throws Throwable {
        composite = TestRequestStateFactory.createCompositeDescription();
        composite.descriptionLinks = new ArrayList<>();
        composite.descriptionLinks.add(containerDescription.documentSelfLink);
        composite = doPost(composite, CompositeDescriptionFactoryService.SELF_LINK);
        addForDeletion(composite);
    }

    @Test
    public void testExposeServiceProcessingWithPostAllocaton() throws Throwable {

        request = startRequest(request);
        waitForRequestToComplete(request);
        request = getDocument(RequestBrokerState.class, request.documentSelfLink);

        CompositeComponent cc = getDocument(CompositeComponent.class, request.resourceLinks.get(0));

        List<String> containerStateLinks = findResourceLinks(ContainerState.class,
                cc.componentLinks);

        assertEquals(CLUSTER_SIZE, containerStateLinks.size());
        assertEquals(CLUSTER_SIZE,
                mockContainerHostNetworkConfigService.getConfigs().size());

        List<ContainerState> containerStates = getContainerStates(containerStateLinks);

        Set<String> hostPorts = new HashSet<>();

        for (ContainerState containerState : containerStates) {
            for (PortBinding pb : containerState.ports) {
                if (pb.containerPort.equals(serviceAddressWithFormat.port)) {
                    hostPorts.add(pb.hostPort);
                }
            }
        }

        for (ContainerState containerState : containerStates) {
            ContainerNetworkConfigState containerNetworkConfigState = mockContainerHostNetworkConfigService
                    .getConfig(containerState.documentSelfLink);
            assertNotNull(containerNetworkConfigState);

            Iterator<String> publicServiceNetworkLinksIterator = containerNetworkConfigState.publicServiceNetworkLinks
                    .iterator();

            Set<String> matchRegexs = new HashSet<>();
            matchRegexs.add(SERVICE_ADDRESS_ALIAS_FORMAT_REGEXP);
            matchRegexs.add(SERVICE_ADDRESS_ALIAS_NOFORMAT_REGEXP);

            while (publicServiceNetworkLinksIterator.hasNext()) {
                assertPublicNetworkLink(publicServiceNetworkLinksIterator.next(), hostPorts,
                        new HashSet<>(matchRegexs));
            }
        }
    }

    @Test
    public void testExposeServiceProcessingAfterScaleUp() throws Throwable {
        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);

        List<ContainerState> containerStates = getContainerStates(containerDesc.documentSelfLink);
        assertEquals(CLUSTER_SIZE, containerStates.size());

        ExposedServiceDescriptionState exposedServiceState = getExposedServiceState(containerStates);

        for (ContainerState containerState : containerStates) {
            assertEquals(exposedServiceState.documentSelfLink, containerState.exposedServiceLink);
        }

        String compositeContextId = containerStates.get(0).customProperties
                .get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY);

        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = containerDesc.documentSelfLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        day2OperationClustering.resourceCount = CLUSTER_SIZE + 1;
        day2OperationClustering.customProperties = new HashMap<>();
        day2OperationClustering.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY,
                compositeContextId);

        day2OperationClustering = startRequest(day2OperationClustering);
        waitForRequestToComplete(day2OperationClustering);

        containerStates = getContainerStates(containerDesc.documentSelfLink);
        assertEquals(CLUSTER_SIZE + 1, containerStates.size());

        for (ContainerState containerState : containerStates) {
            assertEquals(exposedServiceState.documentSelfLink, containerState.exposedServiceLink);
        }
    }

    @Test
    public void testExposeServiceProcessingAfterScaleDown() throws Throwable {
        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);

        List<ContainerState> containerStates = getContainerStates(containerDesc.documentSelfLink);
        assertEquals(CLUSTER_SIZE, containerStates.size());

        ExposedServiceDescriptionState exposedServiceState = getExposedServiceState(containerStates);

        for (ContainerState containerState : containerStates) {
            assertEquals(exposedServiceState.documentSelfLink, containerState.exposedServiceLink);
        }

        String compositeContextId = containerStates.get(0).customProperties
                .get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY);

        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = containerDesc.documentSelfLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        day2OperationClustering.resourceCount = CLUSTER_SIZE - 1;
        day2OperationClustering.customProperties = new HashMap<>();
        day2OperationClustering.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY,
                compositeContextId);

        day2OperationClustering = startRequest(day2OperationClustering);
        waitForRequestToComplete(day2OperationClustering);

        List<ContainerState> newContainerStates = getContainerStates(containerDesc.documentSelfLink);
        assertEquals(CLUSTER_SIZE - 1, newContainerStates.size());

        for (ContainerState containerState : newContainerStates) {
            assertEquals(exposedServiceState.documentSelfLink, containerState.exposedServiceLink);
        }

        List<String> containerStatesLinks = containerStates.stream().map((c) -> c.documentSelfLink)
                .collect(Collectors.toList());
        List<String> newContainerStatesLinks = newContainerStates.stream()
                .map((c) -> c.documentSelfLink).collect(Collectors.toList());

        containerStatesLinks.removeAll(newContainerStatesLinks);

        // The removed container
        assertEquals(1, containerStatesLinks.size());

        assertNull(mockContainerHostNetworkConfigService.getConfig(containerStatesLinks.get(0)));
    }

    @Test
    public void testExposeServiceReconfigureAfterContainerNetworkChange() throws Throwable {
        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);

        CompositeComponent cc = getDocument(CompositeComponent.class, request.resourceLinks.get(0));

        List<String> containerStateLinks = findResourceLinks(ContainerState.class,
                cc.componentLinks);
        List<ContainerState> containerStates = getContainerStates(containerStateLinks);

        ContainerState containerStateToChange = containerStates.get(0);

        String newPort = String
                .valueOf(Integer.parseInt(containerStateToChange.ports.get(0).hostPort) + 1);

        containerStateToChange.ports.get(0).hostPort = newPort;

        ContainerState patchState = new ContainerState();
        patchState.ports = containerStateToChange.ports;
        patchState.ports.get(0).hostPort = newPort;

        Set<String> hostPorts = new HashSet<>();

        for (ContainerState containerState : containerStates) {
            for (PortBinding pb : containerState.ports) {
                if (pb.containerPort.equals(serviceAddressWithFormat.port)) {
                    hostPorts.add(pb.hostPort);
                }
            }
        }

        URI uri = UriUtils.buildUri(host, containerStateToChange.documentSelfLink);
        doOperation(patchState, uri, false, Action.PATCH);

        waitFor(() -> {
            for (ContainerState containerState : containerStates) {
                ContainerNetworkConfigState containerNetworkConfigState = mockContainerHostNetworkConfigService
                        .getConfig(containerState.documentSelfLink);
                assertNotNull(containerNetworkConfigState);

                Iterator<String> publicServiceNetworkLinksIterator = containerNetworkConfigState.publicServiceNetworkLinks
                        .iterator();

                Set<String> matchRegexs = new HashSet<>();
                matchRegexs.add(SERVICE_ADDRESS_ALIAS_FORMAT_REGEXP);
                matchRegexs.add(SERVICE_ADDRESS_ALIAS_NOFORMAT_REGEXP);

                while (publicServiceNetworkLinksIterator.hasNext()) {
                    String publicServiceLink = publicServiceNetworkLinksIterator.next();
                    if (!matchPublicNetworkLink(publicServiceLink,
                            hostPorts, new HashSet<>(matchRegexs))) {
                        return false;
                    }
                }
            }

            return true;
        });
    }

    /**
     * Mimics a request from vRA's blueprint. It will make 1 allocation request for a cluster
     * and then parallel provision requests for every container of the cluster
     */
    @Test
    public void testExposeServiceContainerAllocationWithParallelProvisioningRequest()
            throws Throwable {
        host.log(">>>>>>Start: testContainerAllocationWithFollowingProvisioningRequest <<<<< ");
        doOperation(containerDesc, UriUtils.buildUri(host, containerDesc.documentSelfLink),
                false, Action.PUT);

        request.customProperties = new HashMap<>();
        request.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                Boolean.TRUE.toString());

        request = startRequest(request);
        request = waitForRequestToComplete(request);

        CompositeComponent cc = getDocument(CompositeComponent.class, request.resourceLinks.get(0));

        assertEquals(CLUSTER_SIZE, cc.componentLinks.size());

        String contextId = request.customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY);

        // Request provisioning after allocation:
        List<RequestBrokerState> provisioningRequests = new ArrayList<>();

        for (String resourceLink : cc.componentLinks) {
            RequestBrokerState provisioningRequest = new RequestBrokerState();
            provisioningRequest.resourceType = ResourceType.CONTAINER_TYPE.getName();
            provisioningRequest.resourceLinks = Collections.singletonList(resourceLink);
            provisioningRequest.resourceDescriptionLink = containerDesc.documentSelfLink;
            provisioningRequest.operation = ContainerOperationType.CREATE.id;
            provisioningRequest.customProperties = new HashMap<>();
            provisioningRequest.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY,
                    contextId);
            provisioningRequest.resourceCount = 1;

            provisioningRequest = doPost(provisioningRequest, RequestBrokerFactoryService.SELF_LINK);
            provisioningRequests.add(provisioningRequest);
        }

        for (RequestBrokerState provisioningRequest : provisioningRequests) {
            waitForRequestToComplete(provisioningRequest);
        }

        String exposedServiceSelfLink = UriUtilsExtended.buildUriPath(
                ExposedServiceDescriptionFactoryService.SELF_LINK,
                Service.getId(containerDesc.documentSelfLink) + '_' + contextId);

        for (RequestBrokerState provisioningRequest : provisioningRequests) {
            waitForRequestToComplete(provisioningRequest);
            ContainerState containerState = getDocument(ContainerState.class,
                    provisioningRequest.resourceLinks.get(0));

            assertEquals(exposedServiceSelfLink, containerState.exposedServiceLink);
        }

        ExposedServiceDescriptionState exposedService = getDocument(
                ExposedServiceDescriptionState.class, exposedServiceSelfLink);

        assertNotNull(exposedService);
    }

    private List<ContainerState> getContainerStates(List<String> containerStateLinks)
            throws Throwable {
        ServiceDocumentQuery<ContainerState> query = new ServiceDocumentQuery<>(host,
                ContainerState.class);

        QueryTask queryTask = QueryUtil.buildPropertyQuery(ContainerState.class);
        QueryUtil.addExpandOption(queryTask);
        QueryUtil.addListValueClause(queryTask, ContainerState.FIELD_NAME_SELF_LINK,
                containerStateLinks);

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

    private ExposedServiceDescriptionState getExposedServiceState(
            List<ContainerState> containerStates)
            throws Throwable {
        return getDocument(ExposedServiceDescriptionState.class,
                containerStates.get(0).exposedServiceLink);
    }

    private void assertPublicNetworkLink(String publicServiceNetworkLink,
            Collection<String> hostPorts,
            Set<String> matchRegexs) {
        String[] publicServiceNetworkLinkParts = publicServiceNetworkLink.split(":");
        // Check that service alias is generated with unique suffix

        Iterator<String> iterator = matchRegexs.iterator();
        boolean matched = false;
        while (iterator.hasNext()) {
            if (publicServiceNetworkLinkParts[1].matches(iterator.next())) {
                iterator.remove();
                matched = true;
                break;
            }
        }

        assertTrue(matched);

        assertEquals(UriUtils.HTTP_SCHEME,
                publicServiceNetworkLinkParts[0]);
        assertEquals(UriUtilsExtended.extractHost(computeHost.address),
                publicServiceNetworkLinkParts[2]);
        assertTrue(hostPorts.contains(publicServiceNetworkLinkParts[3]));
    }

    private boolean matchPublicNetworkLink(String publicServiceNetworkLink,
            Collection<String> hostPorts,
            Set<String> matchRegexs) {
        String[] publicServiceNetworkLinkParts = publicServiceNetworkLink.split(":");
        // Check that service alias is generated with unique suffix

        Iterator<String> iterator = matchRegexs.iterator();
        boolean matched = false;
        while (iterator.hasNext()) {
            if (publicServiceNetworkLinkParts[1].matches(iterator.next())) {
                iterator.remove();
                matched = true;
                break;
            }
        }

        return matched &&
                UriUtils.HTTP_SCHEME.equals(publicServiceNetworkLinkParts[0]) &&
                UriUtilsExtended.extractHost(computeHost.address).equals(
                        publicServiceNetworkLinkParts[2]) &&
                hostPorts.contains(publicServiceNetworkLinkParts[3]);
    }

}
