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

import java.util.logging.Level;

import org.apache.commons.lang3.NotImplementedException;

import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class DockerNetworkAdapterService extends AbstractDockerAdapterService {

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_NETWORK;

    public static final String DOCKER_NETWORK_TYPE_DEFAULT = "overlay";

    private static final NetworkOperationType[] DIRECT_OPERATIONS = {
            NetworkOperationType.CREATE,
            NetworkOperationType.DELETE // TODO other operations
    };

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
                processInspectNetwork(context);
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
        CommandInput createCommandInput = context.commandInput.withPropertyIfNotNull(
                DockerAdapterCommandExecutor.DOCKER_NETWORK_NAME_PROP_NAME,
                context.networkState.name);
        if (context.networkState.driver != null && !context.networkState.driver.isEmpty()) {
            createCommandInput.withProperty(
                    DockerAdapterCommandExecutor.DOCKER_NETWORK_DRIVER_PROP_NAME,
                    context.networkState.driver);
        } else {
            createCommandInput.withProperty(
                    DockerAdapterCommandExecutor.DOCKER_NETWORK_DRIVER_PROP_NAME,
                    DOCKER_NETWORK_TYPE_DEFAULT);
        }

        // TODO do verification and stuff

        context.executor.createNetwork(createCommandInput, (op, ex) -> {
            if (ex != null) {
                context.operation.fail(ex);
            } else {
                if (op.hasBody()) {
                    context.operation.setBody(op.getBody(String.class));
                }
                context.operation.complete();
            }
        });
    }

    private void processDeleteNetwork(RequestContext context) {
        CommandInput deleteCommandInput = context.commandInput.withPropertyIfNotNull(
                DockerAdapterCommandExecutor.DOCKER_NETWORK_ID_PROP_NAME,
                context.networkState.name);

        // TODO do verification and stuff

        context.executor.removeNetwork(deleteCommandInput, (op, ex) -> {
            if (ex != null) {
                context.operation.fail(ex);
            } else {
                if (op.hasBody()) {
                    context.operation.setBody(op.getBody(String.class));
                }
                context.operation.complete();
            }
        });
    }

    private void processInspectNetwork(RequestContext context) {
        // TODO implement
        throw new NotImplementedException("inspecting networks is not implemented yet");
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
