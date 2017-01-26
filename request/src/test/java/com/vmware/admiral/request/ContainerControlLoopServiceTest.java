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

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.request.ContainerControlLoopService.ContainerControlLoopState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class ContainerControlLoopServiceTest extends RequestBaseTest {
    private static final int SINGLE_CONTAINERS_TO_BE_PROVISIONED = 3;

    private ContainerDescription containerDescription1;
    private ContainerDescription containerDescription2;

    @Before
    public void init() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
        waitForServiceAvailability(ContainerControlLoopService.CONTROL_LOOP_INFO_LINK);
    }

    @Test
    public void testControlLoopStateCreatedOnStartUp() throws Throwable {
        ContainerControlLoopState controlLoopState = getDocument(
                ContainerControlLoopState.class,
                ContainerControlLoopService.CONTROL_LOOP_INFO_LINK);

        assertNotNull(controlLoopState);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testPostAnotherStateWithoutSelfLinkShouldFail() throws Throwable {
        ContainerControlLoopState controlLoopState = getDocument(
                ContainerControlLoopState.class,
                ContainerControlLoopService.CONTROL_LOOP_INFO_LINK);
        controlLoopState.documentSelfLink = null;

        doPost(controlLoopState, ContainerControlLoopService.CONTROL_LOOP_INFO_LINK);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRedeploymentOfAContainerInCluster() throws Throwable {

        containerDescription1 = createContainerDescription(false);
        containerDescription1._cluster = 2;
        doPut(containerDescription1);

        // provision 2 containers in cluster
        ContainerState state = provisionContainer(containerDescription1.documentSelfLink);
        // change the power state of one of them
        state.powerState = PowerState.ERROR;
        doPut(state);

        Map<String, List<String>> containersPerContextId = new HashMap<>();

        retrieveContainerStates(containerDescription1.documentSelfLink).thenAccept(containerStates -> {
            List<String> containersFromDesc1 = containerStates.stream().map(cs -> cs.documentSelfLink).collect(Collectors.toList());
            assertEquals(2, containersFromDesc1.size());

            // clustered containers have same context_id
            containersPerContextId.put(containerStates.get(0).customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY), containersFromDesc1);
        });

        doOperation(new ContainerControlLoopState(), UriUtils.buildUri(host,
                ContainerControlLoopService.CONTROL_LOOP_INFO_LINK),
                false,
                Service.Action.PATCH);

        Map<String, List<String>> redeployedContainersPerContextId = new HashMap<>();

        AtomicBoolean containerFromDesc1Redeployed = new AtomicBoolean(false);

        waitFor(() -> {
            // get all containers from containerDescription1
            retrieveContainerStates(containerDescription1.documentSelfLink).thenAccept(containerStates -> {
                long healthyContainers = containerStates.stream().filter(cs -> PowerState.RUNNING.equals(cs.powerState)).count();
                host.log("Healthy containers from %s : %d", containerDescription1.documentSelfLink, healthyContainers);
                containerFromDesc1Redeployed.set(containerDescription1._cluster == healthyContainers && containerDescription1._cluster == containerStates.size());

                List<String> containersFromDesc1 = containerStates.stream().map(cs -> cs.documentSelfLink).collect(Collectors.toList());
                redeployedContainersPerContextId.put(containerStates.get(0).customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY), containersFromDesc1);
            });

            if (containerFromDesc1Redeployed.get()) {
                containersPerContextId.entrySet().stream().forEach(m -> {
                    String contextId = m.getKey();
                    List<String> redeployedContainers = redeployedContainersPerContextId.get(contextId);
                    host.log("Redeployed container: %s -> %s", StringUtils.join(m.getValue()), StringUtils.join(redeployedContainers));
                });
            }

            return containerFromDesc1Redeployed.get();
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void redeploymentOfSingleContainers() throws Throwable {
        containerDescription2 = createContainerDescription(false);

        // provision 3 single containers, 2 of them in ERROR state
        ContainerState state = null;
        for (int i = 0; i < SINGLE_CONTAINERS_TO_BE_PROVISIONED; i++) {
            state = provisionContainer(containerDescription2.documentSelfLink);

            if (i < SINGLE_CONTAINERS_TO_BE_PROVISIONED - 1) {
                state.powerState = PowerState.ERROR;
                doPut(state);
            }
        }

        Map<String, List<String>> containersPerContextId = new HashMap<>();

        retrieveContainerStates(containerDescription2.documentSelfLink).thenAccept(containerStates -> {
            containerStates.stream().forEach(cs -> {
                containersPerContextId.put(cs.customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY), Arrays.asList(cs.documentSelfLink));
            });
        });

        doOperation(new ContainerControlLoopState(), UriUtils.buildUri(host,
                ContainerControlLoopService.CONTROL_LOOP_INFO_LINK),
                false,
                Service.Action.PATCH);

        Map<String, List<String>> redeployedContainersPerContextId = new HashMap<>();
        AtomicBoolean containerFromDesc2Redeployed = new AtomicBoolean(false);

        waitFor(() -> {
            // get all containers from containerDescription2
            retrieveContainerStates(containerDescription2.documentSelfLink).thenAccept(containerStates -> {
                long healthyContainers = containerStates.stream().filter(cs -> PowerState.RUNNING.equals(cs.powerState)).count();
                host.log("Healthy containers from %s : %d", containerDescription2.documentSelfLink, healthyContainers);
                containerFromDesc2Redeployed.set(SINGLE_CONTAINERS_TO_BE_PROVISIONED == healthyContainers && SINGLE_CONTAINERS_TO_BE_PROVISIONED == containerStates.size());

                containerStates.stream().forEach(cs -> {
                    redeployedContainersPerContextId.put(cs.customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY), Arrays.asList(cs.documentSelfLink));
                });
            });

            if (containerFromDesc2Redeployed.get()) {
                containersPerContextId.entrySet().stream().forEach(m -> {
                    String contextId = m.getKey();
                    List<String> redeployedContainers = redeployedContainersPerContextId.get(contextId);
                    host.log("Redeployed container: %s -> %s", StringUtils.join(m.getValue()), StringUtils.join(redeployedContainers));
                });
            }

            return containerFromDesc2Redeployed.get();
        });
    }

    private DeferredResult<List<ContainerState>> retrieveContainerStates(String descriptionLink) {
        Builder builder = Builder.create()
                .addKindFieldClause(ContainerState.class)
                .addFieldClause(ContainerState.FIELD_NAME_DESCRIPTION_LINK, descriptionLink);

        QueryByPages<ContainerState> query = new QueryByPages<>(host, builder.build(),
                ContainerState.class, null);

        return query.collectDocuments(Collectors.toList());
    }
}
