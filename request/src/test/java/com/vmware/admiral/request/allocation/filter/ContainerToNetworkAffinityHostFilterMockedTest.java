/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.allocation.filter;

import java.net.URI;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class ContainerToNetworkAffinityHostFilterMockedTest {

    private VerificationHost host;
    private PlacementHostSelectionTaskState placementHostSelectionTaskState;

    @Before
    public void setup() {
        placementHostSelectionTaskState = new PlacementHostSelectionTaskState();
        placementHostSelectionTaskState.contextId = UUID.randomUUID().toString();

        host = Mockito.mock(VerificationHost.class);
        Mockito.when(host.getUri()).thenReturn(URI.create("http://mocked.verification.host:1234"));

        Answer<Void> realMethodAnswer = invocation -> {
            invocation.callRealMethod();
            return null;
        };
        Mockito.doAnswer(realMethodAnswer).when(host).completeIteration();
        Mockito.doAnswer(realMethodAnswer).when(host).failIteration(Mockito.any(Throwable.class));
    }


    @Test
    public void testUpdateUserDefinedNetworksWithSelectedHostCompletesWhenCompositeComponentIsNotFound() {
        AtomicBoolean requestFailed = new AtomicBoolean(false);

        Mockito.doAnswer(invocation -> {
            Operation op = invocation.<Operation>getArgument(0);
            requestFailed.set(true);
            op.fail(Operation.STATUS_CODE_NOT_FOUND, new Exception("Simulated not found exception"),
                    null);
            return null;
        }).when(host).sendRequest(Mockito.any(Operation.class));


        ContainerToNetworkAffinityHostFilter filter = new ContainerToNetworkAffinityHostFilter(
                host, new ContainerDescription());

        Runnable completion = () -> {
            if (requestFailed.get()) {
                host.completeIteration();
            } else {
                host.failIteration(new IllegalStateException(
                        "Request was expected to fail with a not found exception"));
            }
        };

        host.testStart(1);
        filter.updateUserDefinedNetworksWithSelectedHost(placementHostSelectionTaskState, null,
                completion);
        host.testWait();

        String compositeLink = UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK,
                placementHostSelectionTaskState.contextId);

        Mockito.verify(host, Mockito.times(1)).sendRequest(Mockito.any(Operation.class));
        Mockito.verify(host, Mockito.times(1)).sendRequest(Mockito.<Operation>argThat(
                op -> op.getUri().toString().endsWith(compositeLink)));
    }

    @Test
    public void testUpdateUserDefinedNetworksWithSelectedHostCompletesWhenGetCompositeComponentFails() {
        AtomicBoolean requestFailed = new AtomicBoolean(false);

        Mockito.doAnswer(invocation -> {
            Operation op = invocation.<Operation>getArgument(0);
            requestFailed.set(true);
            op.fail(Operation.STATUS_CODE_INTERNAL_ERROR, new Exception("Simulated internal error"),
                    null);
            return null;
        }).when(host).sendRequest(Mockito.any(Operation.class));

        ContainerToNetworkAffinityHostFilter filter = new ContainerToNetworkAffinityHostFilter(
                host, new ContainerDescription());

        Runnable completion = () -> {
            if (requestFailed.get()) {
                host.completeIteration();
            } else {
                host.failIteration(new IllegalStateException(
                        "Request was expected to fail with an internal error"));
            }
        };

        host.testStart(1);
        filter.updateUserDefinedNetworksWithSelectedHost(placementHostSelectionTaskState, null,
                completion);
        host.testWait();

        String compositeLink = UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK,
                placementHostSelectionTaskState.contextId);

        Mockito.verify(host, Mockito.times(1)).sendRequest(Mockito.any(Operation.class));
        Mockito.verify(host, Mockito.times(1)).sendRequest(Mockito.<Operation>argThat(
                op -> op.getUri().toString().endsWith(compositeLink)));
    }

    @Test
    public void testUpdateUserDefinedNetworksWithSelectedHostCompletesWhenGetCompositeDescriptionFails() {
        AtomicBoolean requestFailed = new AtomicBoolean(false);

        CompositeDescription description = new CompositeDescription();
        description.documentSelfLink = UriUtils.buildUriPath(
                CompositeDescriptionFactoryService.SELF_LINK,
                placementHostSelectionTaskState.contextId);
        description.descriptionLinks = Collections.emptyList();
        CompositeComponent component = new CompositeComponent();
        component.documentSelfLink = UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK,
                placementHostSelectionTaskState.contextId);
        component.compositeDescriptionLink = description.documentSelfLink;

        Mockito.doAnswer(invocation -> {
            Operation op = invocation.<Operation>getArgument(0);
            String uri = op.getUri().toString();
            if (uri.endsWith(component.documentSelfLink)) {
                op.setBody(component).complete();
            } else {
                requestFailed.set(true);
                op.fail(Operation.STATUS_CODE_INTERNAL_ERROR,
                        new Exception("Simulated internal error"), null);
            }

            return null;
        }).when(host).sendRequest(Mockito.any(Operation.class));

        ContainerToNetworkAffinityHostFilter filter = new ContainerToNetworkAffinityHostFilter(
                host, new ContainerDescription());

        Runnable completion = () -> {
            if (requestFailed.get()) {
                host.completeIteration();
            } else {
                host.failIteration(new IllegalStateException(
                        "Request was expected to fail one time"));
            }
        };

        host.testStart(1);
        filter.updateUserDefinedNetworksWithSelectedHost(placementHostSelectionTaskState, null,
                completion);
        host.testWait();

        Mockito.verify(host, Mockito.times(2)).sendRequest(Mockito.any(Operation.class));
        Mockito.verify(host, Mockito.times(1)).sendRequest(Mockito.<Operation>argThat(
                op -> op.getUri().toString().endsWith(component.documentSelfLink)));
        Mockito.verify(host, Mockito.times(1)).sendRequest(Mockito.<Operation>argThat(
                op -> op.getUri().toString().endsWith(description.documentSelfLink)));
    }

    @Test
    public void testUpdateUserDefinedNetworksWithSelectedHostCompletesWhenCompositeDescriptionHasNoNetworks() {
        AtomicBoolean requestFailed = new AtomicBoolean(false);

        CompositeDescription description = new CompositeDescription();
        description.documentSelfLink = UriUtils.buildUriPath(
                CompositeDescriptionFactoryService.SELF_LINK,
                placementHostSelectionTaskState.contextId);
        description.descriptionLinks = Collections.emptyList();
        CompositeComponent component = new CompositeComponent();
        component.documentSelfLink = UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK,
                placementHostSelectionTaskState.contextId);
        component.compositeDescriptionLink = description.documentSelfLink;

        Mockito.doAnswer(invocation -> {
            Operation op = invocation.<Operation>getArgument(0);
            String uri = op.getUri().toString();
            if (uri.endsWith(component.documentSelfLink)) {
                op.setBody(component).complete();
            } else if (uri.endsWith(description.documentSelfLink)) {
                op.setBody(description).complete();
            } else {
                requestFailed.set(true);
                op.fail(Operation.STATUS_CODE_INTERNAL_ERROR,
                        new Exception("Simulated internal error"), null);
            }

            return null;
        }).when(host).sendRequest(Mockito.any(Operation.class));

        ContainerToNetworkAffinityHostFilter filter = new ContainerToNetworkAffinityHostFilter(
                host, new ContainerDescription());

        Runnable completion = () -> {
            if (!requestFailed.get()) {
                host.completeIteration();
            } else {
                host.failIteration(new IllegalStateException(
                        "Request was expected not to fail"));
            }
        };

        host.testStart(1);
        filter.updateUserDefinedNetworksWithSelectedHost(placementHostSelectionTaskState, null,
                completion);
        host.testWait();

        Mockito.verify(host, Mockito.times(2)).sendRequest(Mockito.any(Operation.class));
        Mockito.verify(host, Mockito.times(1)).sendRequest(Mockito.<Operation>argThat(
                op -> op.getUri().toString().endsWith(component.documentSelfLink)));
        Mockito.verify(host, Mockito.times(1)).sendRequest(Mockito.<Operation>argThat(
                op -> op.getUri().toString().endsWith(description.documentSelfLink)));
    }

    @Test
    public void testFindNetworkDescriptionsByLinksCompletesOnFailedQuery() {
        AtomicBoolean requestFailed = new AtomicBoolean(false);

        Mockito.doAnswer(invocation -> {
            Operation op = invocation.<Operation>getArgument(0);
            requestFailed.set(true);
            op.fail(Operation.STATUS_CODE_INTERNAL_ERROR,
                    new Exception("Simulated internal error"), null);
            return null;
        }).when(host).sendRequest(Mockito.any(Operation.class));

        ContainerDescription containerDescription = new ContainerDescription();
        containerDescription.networks = Collections.singletonMap("some-network", null);

        ContainerToNetworkAffinityHostFilter filter = new ContainerToNetworkAffinityHostFilter(
                host, containerDescription);

        Runnable completion = () -> {
            if (requestFailed.get()) {
                host.completeIteration();
            } else {
                host.failIteration(new IllegalStateException(
                        "Request was expected to fail once"));
            }
        };

        host.testStart(1);
        filter.findNetworkDescriptionsByLinks(placementHostSelectionTaskState, null, null,
                completion);
        host.testWait();

        Mockito.verify(host, Mockito.times(1)).sendRequest(Mockito.any(Operation.class));
        Mockito.verify(host, Mockito.times(1)).sendRequest(Mockito.<Operation>argThat(
                op -> op.getUri().toString().endsWith(ServiceUriPaths.CORE_QUERY_TASKS)));
    }
}
