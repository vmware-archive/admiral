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

package com.vmware.admiral.adapter.docker.service;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_DRIVER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_ID_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_OPTIONS_PROP_NAME;

import java.util.Map;
import java.util.logging.Level;

import org.apache.commons.lang3.NotImplementedException;

import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;

public class DockerNetworkAdapterService extends AbstractDockerAdapterService {

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_NETWORK;

    public static final String DOCKER_NETWORK_TYPE_DEFAULT = ContainerNetworkDescription.NETWORK_DRIVER_BRIDGE;

    private static final NetworkOperationType[] DIRECT_OPERATIONS = {};

    private static class RequestContext {
        public NetworkRequest request;
        public ContainerNetworkState networkState;
        public CommandInput commandInput;
        public DockerAdapterCommandExecutor executor;

        // Only for direct operations. See DIRECT_OPERATIONS list
        public Operation operation;
    }

    @Override
    public void handlePatch(Operation op) {
        RequestContext context = new RequestContext();
        context.request = op.getBody(NetworkRequest.class);
        context.request.validate();

        NetworkOperationType operationType = context.request.getOperationType();

        logInfo("Processing network operation request %s for resource %s %s",
                operationType, context.request.resourceReference,
                context.request.getRequestTrackingLog());

        if (isDirectOperationRequested(context)) {
            context.operation = op;
        } else {
            op.complete();
        }

        processNetworkRequest(context);
    }

    /**
     * Check whether the patch {@link Operation} id direct operation or not
     */
    private boolean isDirectOperationRequested(RequestContext context) {
        NetworkOperationType operationType = context.request.getOperationType();

        for (NetworkOperationType directOperation : DIRECT_OPERATIONS) {
            if (directOperation == operationType) {
                return true;
            }
        }

        return false;
    }

    /**
     * Start processing the request. First fetches the {@link ContainerNetworkState}. Will result in
     * filling the {@link RequestContext#networkState} property.
     */
    private void processNetworkRequest(RequestContext context) {
        Operation getNetworkState = Operation
                .createGet(context.request.getNetworkStateReference())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        handleExceptions(context.request, context.operation, () -> {
                            context.networkState = o
                                    .getBody(ContainerNetworkState.class);
                            processNetworkState(context);
                        });
                    }
                });
        handleExceptions(context.request, context.operation, () -> {
            getHost().log(Level.FINE, "Fetching NetworkState: %s %s",
                    context.request.getRequestTrackingLog(),
                    context.request.getNetworkStateReference());
            sendRequest(getNetworkState);
        });
    }

    /**
     * Process the {@link ContainerNetworkState}. Will result in filling the
     * {@link RequestContext#commandInput} and {@link RequestContext#executor} properties.
     */
    private void processNetworkState(RequestContext context) {
        getContainerHost(
                context.request,
                context.operation,
                UriUtils.buildUri(getHost(), context.networkState.originatingHostLink),
                (computeState, commandInput) -> {
                    context.commandInput = commandInput;
                    context.executor = getCommandExecutor(computeState);
                    handleExceptions(context.request, context.operation,
                            () -> processOperation(context));
                });
    }

    /**
     * Process the operation. This method should be called after {@link RequestContext#request},
     * {@link RequestContext#networkState}, {@link RequestContext#commandInput} and
     * {@link RequestContext#executor} have been filled. For direct operations,
     * {@link RequestContext#operation} must also be filled
     *
     * @see #DIRECT_OPERATIONS
     */
    private void processOperation(RequestContext context) {
        try {
            switch (context.request.getOperationType()) {
            case CREATE:
                processCreateNetwork(context);
                break;

            case DELETE:
                processDeleteNetwork(context);
                break;

            case INSPECT:
                inspectAndUpdateNetwork(context);
                break;

            case LIST_NETWORKS:
                processListNetworks(context);
                break;

            case CONNECT:
                processConnectNetwork(context);
                break;

            case DISCONNECT:
                processDisconnectNetwork(context);
                break;

            default:
                fail(context.request, new IllegalArgumentException(
                        "Unexpected request type: " + context.request.getOperationType()
                                + context.request.getRequestTrackingLog()));
            }
        } catch (Throwable e) {
            fail(context.request, e);
        }
    }

    private void processCreateNetwork(RequestContext context) {
        AssertUtil.assertNotNull(context.networkState, "networkState");
        AssertUtil.assertNotEmpty(context.networkState.name, "networkState.name");

        CommandInput createCommandInput = context.commandInput.withPropertyIfNotNull(
                DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME,
                context.networkState.name);
        if (context.networkState.driver != null && !context.networkState.driver.isEmpty()) {
            createCommandInput.withProperty(
                    DOCKER_CONTAINER_NETWORK_DRIVER_PROP_NAME,
                    context.networkState.driver);
        } else {
            createCommandInput.withProperty(
                    DOCKER_CONTAINER_NETWORK_DRIVER_PROP_NAME,
                    DOCKER_NETWORK_TYPE_DEFAULT);
        }

        if (context.networkState.options != null && !context.networkState.options.isEmpty()) {
            createCommandInput.withProperty(
                    DOCKER_CONTAINER_NETWORK_OPTIONS_PROP_NAME,
                    context.networkState.options);
        }

        if (context.networkState.ipam != null) {
            createCommandInput.withProperty(DOCKER_CONTAINER_NETWORK_IPAM_PROP_NAME,
                    DockerAdapterUtils.ipamToMap(context.networkState.ipam));
        }

        context.executor.createNetwork(createCommandInput, (op, ex) -> {
            if (ex != null) {
                fail(context.request, ex);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = op.getBody(Map.class);

                context.networkState.id = (String) body
                        .get(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME);
                inspectAndUpdateNetwork(context);
                // transition to TaskStage.FINISHED is done later, after the network state gets
                // updated
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void inspectAndUpdateNetwork(RequestContext context) {

        String networkId;
        if (Boolean.TRUE.equals(context.networkState.external)) {
            // Actual network name in Docker!
            networkId = context.networkState.name;
        } else {
            networkId = context.networkState.id;
        }

        if (networkId == null) {
            fail(context.request, new IllegalStateException("network id is required "
                    + context.request.getRequestTrackingLog()));
            return;
        }

        CommandInput inspectCommandInput = new CommandInput(context.commandInput).withProperty(
                DOCKER_CONTAINER_NETWORK_ID_PROP_NAME, networkId);

        getHost().log(Level.FINE, "Executing inspect network: %s %s",
                context.networkState.documentSelfLink, context.request.getRequestTrackingLog());

        context.executor.inspectNetwork(
                inspectCommandInput,
                // commandInput,
                (o, ex) -> {
                    if (ex != null) {
                        fail(context.request, o, ex);
                    } else {
                        handleExceptions(
                                context.request,
                                context.operation,
                                () -> {
                                    Map<String, Object> properties = o.getBody(Map.class);

                                    patchNetworkState(context.request, context.networkState,
                                            properties, context);
                                });
                    }
                });
    }

    private void patchNetworkState(NetworkRequest request,
            ContainerNetworkState networkState, Map<String, Object> properties,
            RequestContext context) {

        ContainerNetworkState newNetworkState = new ContainerNetworkState();
        newNetworkState.documentSelfLink = networkState.documentSelfLink;
        newNetworkState.documentExpirationTimeMicros = -1; // make sure the expiration is reset.
        newNetworkState.adapterManagementReference = networkState.adapterManagementReference;

        ContainerNetworkStateMapper.propertiesToContainerNetworkState(newNetworkState, properties);

        getHost().log(Level.FINE, "Patching ContainerNetworkState: %s %s",
                networkState.documentSelfLink, request.getRequestTrackingLog());
        sendRequest(Operation
                .createPatch(request.getNetworkStateReference())
                .setBody(newNetworkState)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, o, ex);
                    } else {
                        patchTaskStage(request, TaskStage.FINISHED, ex);
                    }
                }));
    }

    private void processDeleteNetwork(RequestContext context) {
        AssertUtil.assertNotNull(context.networkState, "networkState");
        AssertUtil.assertNotEmpty(context.networkState.id, "networkState.id");

        CommandInput deleteCommandInput = context.commandInput.withPropertyIfNotNull(
                DOCKER_CONTAINER_NETWORK_ID_PROP_NAME, context.networkState.id);

        // TODO do verification and stuff

        context.executor.removeNetwork(deleteCommandInput, (op, ex) -> {
            if (ex != null) {
                fail(context.request, ex);
            } else {
                patchTaskStage(context.request, TaskStage.FINISHED, null);
            }
        });
    }

    private void processListNetworks(RequestContext context) {
        // TODO implement
        throw new NotImplementedException("listing networks is not implemented yet");
    }

    private void processConnectNetwork(RequestContext context) {
        // TODO implement
        throw new NotImplementedException(
                "connecting containers to networks is not implemented yet");
    }

    private void processDisconnectNetwork(RequestContext context) {
        // TODO implement
        throw new NotImplementedException(
                "disconnecting containers from networks is not implemented yet");
    }

}
