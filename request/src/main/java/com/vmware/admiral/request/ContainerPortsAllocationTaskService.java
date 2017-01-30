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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.netty.util.internal.StringUtil;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.ContainerService;
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;

/**
 * Task to allocate container ports
 */
public class ContainerPortsAllocationTaskService
        extends
        AbstractTaskStatefulService<ContainerPortsAllocationTaskService.ContainerPortsAllocationTaskState,
                ContainerPortsAllocationTaskService.ContainerPortsAllocationTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_CONTAINER_PORTS_ALLOCATION_TASKS;
    public static final String DISPLAY_NAME = "Container Ports Allocation";
    public static final String CONTAINER_PORT_ALLOCATION_ENABLED = "dcp.management.container.port.allocation.enabled";

    // cached container state
    private volatile Set<ContainerService.ContainerState> containerStates;
    // cached host port profile
    private volatile Set<HostPortProfileService.HostPortProfileState> hostPortProfileStates;

    public static class ContainerPortsAllocationTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerPortsAllocationTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            CONTEXT_PREPARED,
            ALLOCATING_PORTS,
            PORTS_ALLOCATED,
            ERROR,
            COMPLETED;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(ALLOCATING_PORTS));
        }

        /** {@link com.vmware.admiral.compute.container.ContainerService.ContainerState} link. */
        @ServiceDocument.PropertyOptions(usage = { SINGLE_ASSIGNMENT,
                REQUIRED }, indexing = STORE_ONLY)
        public Set<String> containerStateLinks;
    }

    public ContainerPortsAllocationTaskService() {
        super(ContainerPortsAllocationTaskState.class,
                ContainerPortsAllocationTaskState.SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = ContainerPortsAllocationTaskState.SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(
            ContainerPortsAllocationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            prepareContext(state, null);
            break;
        case CONTEXT_PREPARED:
            allocatePorts(state, null);
            break;
        case ALLOCATING_PORTS:
            break;
        case PORTS_ALLOCATED:
            updateContainerPorts(state, null);
            break;
        case COMPLETED:
            complete();
            break;
        case ERROR:
            completeWithError();
            break;
        default:
            break;
        }
    }

    private void prepareContext(ContainerPortsAllocationTaskState state,
            Set<ContainerService.ContainerState> containerStates) {
        // make sure port allocation enabled
        String portAllocationEnabled = System.getProperty(CONTAINER_PORT_ALLOCATION_ENABLED,
                Boolean.TRUE.toString());
        if (!Boolean.parseBoolean(portAllocationEnabled)) {
            proceedTo(ContainerPortsAllocationTaskState.SubStage.COMPLETED);
            return;
        }

        if (containerStates == null) {
            getContainerState(state, (contStates) -> {
                prepareContext(state, contStates);
            });
            return;
        }

        if (hostPortProfileStates == null) {
            getHostPortProfile(containerStates, (profiles) -> {
                if (profiles.isEmpty()) {
                    proceedTo(ContainerPortsAllocationTaskState.SubStage.COMPLETED);
                    return;
                }
                proceedTo(ContainerPortsAllocationTaskState.SubStage.CONTEXT_PREPARED);
            });
        }
    }

    private void allocatePorts(ContainerPortsAllocationTaskState state,
            ServiceTaskCallback taskCallback) {
        if (taskCallback == null) {
            createCounterSubTaskCallback(state, state.containerStateLinks.size(), false,
                    ContainerPortsAllocationTaskState.SubStage.PORTS_ALLOCATED,
                    (serviceTask) -> allocatePorts(state, serviceTask));
            return;
        }

        for (ContainerService.ContainerState containerState : containerStates) {
            HostPortProfileService.HostPortProfileState profile = hostPortProfileStates
                    .stream()
                    .filter(p -> p.hostLink.equals(containerState.parentLink))
                    .findFirst()
                    .orElse(null);
            if (profile == null) {
                completeSubTasksCounter(taskCallback, null);
                continue;
            }
            // create port allocation request based on container PortBindings
            HostPortProfileService.HostPortProfileReservationRequest hostPortProfileRequest =
                    createHostPortProfileRequest(containerState);
            if (hostPortProfileRequest == null) {
                completeSubTasksCounter(taskCallback, null);
                continue;
            }

            // allocate ports
            sendRequest(Operation
                    .createPatch(getHost(), profile.documentSelfLink)
                    .setBody(hostPortProfileRequest)
                    .setCompletion(
                            (op, ex) -> {
                                if (ex != null) {
                                    completeSubTasksCounter(taskCallback, ex);
                                    return;
                                }
                                // update cached host port profile state
                                HostPortProfileService.HostPortProfileState result =
                                        op.getBody(
                                                HostPortProfileService.HostPortProfileState.class);
                                profile.reservedPorts.putAll(result.reservedPorts);
                                completeSubTasksCounter(taskCallback, null);
                            }));
            proceedTo(ContainerPortsAllocationTaskState.SubStage.ALLOCATING_PORTS);
        }
    }

    private HostPortProfileService.HostPortProfileReservationRequest createHostPortProfileRequest(
            ContainerService.ContainerState containerState) {
        if (containerState.ports == null || containerState.ports.isEmpty()) {
            return null;
        }

        // get port bindings with specified host_port
        Set<Long> requestedPorts = containerState.ports
                .stream()
                .filter(p -> !StringUtil.isNullOrEmpty(p.hostPort)
                        && Integer.parseInt(p.hostPort) > 0)
                .map(k -> (long) Integer.parseInt(k.hostPort))
                .collect(Collectors.toSet());
        // get port bindings that do not specify host_port
        long additionalPortCount = containerState.ports
                .stream()
                .filter((p) -> StringUtil.isNullOrEmpty(p.hostPort)
                        || Integer.parseInt(p.hostPort) == 0)
                .count();

        // return null if there a no ports to allocate
        if (requestedPorts.size() == 0 && additionalPortCount == 0) {
            return null;
        }

        HostPortProfileService.HostPortProfileReservationRequest request =
                new HostPortProfileService.HostPortProfileReservationRequest();
        request.containerLink = containerState.documentSelfLink;
        request.additionalHostPortCount = additionalPortCount;
        request.specificHostPorts = requestedPorts;
        request.mode = HostPortProfileService.HostPortProfileReservationRequestMode.ALLOCATE;
        return request;
    }

    private void updateContainerPorts(ContainerPortsAllocationTaskState state,
            ServiceTaskCallback taskCallback) {
        if (taskCallback == null) {
            createCounterSubTaskCallback(state, state.containerStateLinks.size(), false,
                    ContainerPortsAllocationTaskState.SubStage.COMPLETED,
                    (serviceTask) -> updateContainerPorts(state, serviceTask));
            return;
        }

        for (ContainerService.ContainerState containerState : containerStates) {
            HostPortProfileService.HostPortProfileState profile = hostPortProfileStates
                    .stream()
                    .filter(p -> p.hostLink.equals(containerState.parentLink))
                    .findFirst()
                    .orElse(null);
            if (profile == null || containerState.ports == null) {
                completeSubTasksCounter(taskCallback, null);
                continue;
            }

            // get all ports reserved for the container
            Set<Long> allocatedPorts = HostPortProfileService.getAllocatedPorts(
                    profile, containerState.documentSelfLink);
            // remove explicitly defined host_ports from reservedPorts
            allocatedPorts
                    .removeIf(p -> containerState.ports
                            .stream()
                            .anyMatch(c -> !StringUtil.isNullOrEmpty(c.hostPort)
                                    && p.intValue() == (Integer.parseInt(c.hostPort))));
            // assign allocated ports to container port bindings
            Iterator<Long> hostPortStatesIterator = allocatedPorts.iterator();
            containerState.ports
                    .stream()
                    .filter(p -> StringUtil.isNullOrEmpty(p.hostPort)
                            || Integer.parseInt(p.hostPort) == 0)
                    .forEach(p -> {
                        if (hostPortStatesIterator.hasNext()) {
                            p.hostPort = hostPortStatesIterator.next().toString();
                        } else {
                            completeSubTasksCounter(taskCallback,
                                    new IllegalStateException("Not enough ports allocated"));
                        }
                    });

            // update container state
            sendRequest(Operation
                    .createPatch(getHost(), containerState.documentSelfLink)
                    .setBody(containerState)
                    .setCompletion(
                            (o, e) -> {
                                if (e != null) {
                                    completeSubTasksCounter(taskCallback, e);
                                    return;
                                }
                                ContainerService.ContainerState body = o
                                        .getBody(ContainerService.ContainerState.class);
                                logInfo("Updated ContainerState: %s ", body.documentSelfLink);
                                completeSubTasksCounter(taskCallback, null);
                            }));

        }
    }

    private void getHostPortProfile(Set<ContainerService.ContainerState> containerStates,
            Consumer<Set<HostPortProfileService.HostPortProfileState>> callbackFunction) {
        hostPortProfileStates = new HashSet<>(containerStates.size());
        List<Operation> operationList = new ArrayList<>(containerStates.size());
        for (ContainerService.ContainerState containerState : containerStates) {
            // get HostPortProfile for container host
            String hostPortProfileLink = HostPortProfileService
                    .getHostPortProfileLink(containerState.parentLink);
            operationList.add(Operation.createGet(this, hostPortProfileLink)
                    .setCompletion((o, e) -> {
                        if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND ||
                                e instanceof CancellationException) {
                            logWarning("Cannot find host port profile [%s]", hostPortProfileLink);
                            return;
                        }
                        if (e != null) {
                            failTask("Failed retrieving HostPortProfileState: "
                                    + hostPortProfileLink, e);
                            return;
                        }
                        hostPortProfileStates.add(o.getBody(
                                HostPortProfileService.HostPortProfileState.class));
                    }));
        }

        OperationJoin.create(operationList)
                .setCompletion((o, es) -> {
                    callbackFunction.accept(hostPortProfileStates);
                }).sendWith(this);
    }

    private void getContainerState(ContainerPortsAllocationTaskState state,
            Consumer<Set<ContainerService.ContainerState>> callbackFunction) {
        containerStates = new HashSet<>(state.containerStateLinks.size());
        List<Operation> operationList = new ArrayList<>(state.containerStateLinks.size());

        for (String containerStateLink : state.containerStateLinks) {
            operationList.add(Operation.createGet(this, containerStateLink)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            return;
                        }

                        ContainerService.ContainerState contState = o
                                .getBody(ContainerService.ContainerState.class);
                        containerStates.add(contState);
                    }));
        }

        OperationJoin.create(operationList)
                .setCompletion((o, es) -> {
                    if (es != null && !es.isEmpty()) {
                        logWarning(Utils.toString(es));
                        failTask("Failed retrieving ContainerState: ",
                                es.values().iterator().next());
                        return;
                    }
                    callbackFunction.accept(containerStates);
                }).sendWith(this);
    }
}
