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

import com.vmware.admiral.adapter.common.VolumeOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;

public class DockerVolumeAdapterService extends AbstractDockerAdapterService {

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_VOLUME;

    public static final String DOCKER_VOLUME_DRIVER_TYPE_DEFAULT = "local";

    private static class RequestContext {
        public ContainerVolumeRequest request;
        public ContainerVolumeState volumeState;
        public CommandInput commandInput;
        public DockerAdapterCommandExecutor executor;

        // Only for direct operations. See DIRECT_OPERATIONS list
        public Operation operation;
    }

    @Override
    public void handlePatch(Operation op) {
        RequestContext context = new RequestContext();
        context.request = op.getBody(ContainerVolumeRequest.class);
        context.request.validate();

        VolumeOperationType operationType = context.request.getOperationType();

        logInfo("Processing volume operation request %s for resource %s %s", operationType,
                context.request.resourceReference, context.request.getRequestTrackingLog());

        op.complete();

        processVolumeRequest(context);
    }

    /**
     * Start processing the request. First fetches the {@link ContainerVolumeState}. Will result in
     * filling the {@link RequestContext#volumeState} property.
     */
    private void processVolumeRequest(RequestContext context) {
        Operation getVolumeState = Operation.createGet(context.request.getVolumeStateReference())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        handleExceptions(context.request, context.operation, () -> {
                            context.volumeState = o.getBody(ContainerVolumeState.class);
                            processVolumeState(context);
                        });
                    }
                });
        handleExceptions(context.request, context.operation, () -> {
            getHost().log(Level.FINE, "Fetching VolumeState: %s %s",
                    context.request.getRequestTrackingLog(),
                    context.request.getVolumeStateReference());
            sendRequest(getVolumeState);
        });
    }

    /**
     * Process the {@link ContainerVolumeState}. Will result in filling the
     * {@link RequestContext#commandInput} and {@link RequestContext#executor} properties.
     */
    private void processVolumeState(RequestContext context) {
        getContainerHost(context.request, context.operation,
                context.volumeState.originatingHostReference,
                (computeState, commandInput) -> {
                    context.commandInput = commandInput;
                    context.executor = getCommandExecutor();
                    handleExceptions(context.request, context.operation,
                            () -> processOperation(context));
                });
    }

    /**
     * Process the operation. This method should be called after {@link RequestContext#request},
     * {@link RequestContext#volumeState}, {@link RequestContext#commandInput} and
     * {@link RequestContext#executor} have been filled. For direct operations,
     * {@link RequestContext#operation} must also be filled
     *
     * @see #DIRECT_OPERATIONS
     */
    private void processOperation(RequestContext context) {
        try {
            switch (context.request.getOperationType()) {
            case CREATE:
                processCreateVolume(context);
                break;

            case DELETE:
                processDeleteVolume(context);
                break;

            case INSPECT:
                processInspectVolume(context);
                break;

            case LIST_VOLUMES:
                processListVolume(context);
                break;

            default:
                fail(context.request, new IllegalArgumentException("Unexpected request type: "
                        + context.request.getOperationType()
                        + context.request.getRequestTrackingLog()));
            }
        } catch (Throwable e) {
            fail(context.request, e);
        }
    }

    private void processCreateVolume(RequestContext context) {
        CommandInput createCommandInput = context.commandInput.withPropertyIfNotNull(
                DockerAdapterCommandExecutor.DOCKER_VOLUME_NAME_PROP_NAME,
                context.volumeState.name);
        if (context.volumeState.driver != null && !context.volumeState.driver.isEmpty()) {
            createCommandInput.withProperty(
                    DockerAdapterCommandExecutor.DOCKER_VOLUME_DRIVER_PROP_NAME,
                    context.volumeState.driver);
        } else {
            createCommandInput.withProperty(
                    DockerAdapterCommandExecutor.DOCKER_VOLUME_DRIVER_PROP_NAME,
                    DOCKER_VOLUME_DRIVER_TYPE_DEFAULT);
        }

        context.executor.createVolume(createCommandInput, (op, ex) -> {
            if (ex != null) {
                fail(context.request, ex);
            } else {
                patchTaskStage(context.request, TaskStage.FINISHED, null);
            }
        });
    }

    private void processDeleteVolume(RequestContext context) {
        CommandInput deleteCommandInput = context.commandInput.withPropertyIfNotNull(
                DockerAdapterCommandExecutor.DOCKER_VOLUME_NAME_PROP_NAME,
                context.volumeState.name);

        context.executor.removeVolume(deleteCommandInput, (op, ex) -> {
            if (ex != null) {
                fail(context.request, ex);
            } else {
                patchTaskStage(context.request, TaskStage.FINISHED, null);
            }
        });
    }

    private void processInspectVolume(RequestContext context) {
        CommandInput inspectCommandInput = context.commandInput.withPropertyIfNotNull(
                DockerAdapterCommandExecutor.DOCKER_VOLUME_NAME_PROP_NAME,
                context.volumeState.name);

        context.executor.inspectVolume(inspectCommandInput, (op, ex) -> {
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

    private void processListVolume(RequestContext context) {

        CommandInput createListVolumeCommandInput = new CommandInput(context.commandInput);

        context.executor.listVolumes(createListVolumeCommandInput, (op, ex) -> {
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

}
