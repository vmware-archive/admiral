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

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_BUILDARGS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_DOCKERFILE_DATA;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_DOCKERFILE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_FORCERM_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_INSPECT_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_NOCACHE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_TAG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_DATA_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_NAME_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_ALIAS_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_CERT_PROP_NAME;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;

public class DockerHostAdapterImageService extends AbstractDockerAdapterService {

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_IMAGE_HOST;

    private SystemImageRetrievalManager imageRetrievalManager;

    @Override
    public void handleStart(Operation startPost) {
        imageRetrievalManager = new SystemImageRetrievalManager(getHost());
        super.handleStart(startPost);
    }

    @Override
    public void handlePatch(Operation op) {
        DockerImageHostRequest request = op.getBody(DockerImageHostRequest.class);
        request.validate();

        logFine("Processing host docker image operation request %s",
                request.getRequestTrackingLog());
        getContainerHost(request, op, request.resourceReference,
                (computeState, commandInput) ->
                processOperation(request, op, computeState, commandInput));
    }

    private void processOperation(DockerImageHostRequest request, Operation op,
            ComputeService.ComputeState computeState, CommandInput commandInput) {
        switch (request.getOperationType()) {
        case BUILD:
            doBuildImage(request, computeState, commandInput);
            op.complete();
            break;
        case LOAD:
            doLoadImage(request, op, computeState, commandInput);
            op.complete();
            break;
        case DELETE:
            doDeleteImage(request, computeState, commandInput);
            op.complete();
            break;
        case INSPECT:
            doInspectImage(request, computeState, commandInput);
            op.complete();
            break;
        default:
            String errorMsg = "Unexpected image operation type: " + request.getOperationType();
            logWarning(errorMsg);
            op.fail(new IllegalArgumentException(errorMsg));

        }
    }

    private void doLoadImage(DockerImageHostRequest request, Operation op,
            ComputeService.ComputeState computeState,
            CommandInput commandInput) {

        String ref = request.customProperties.get(DOCKER_IMAGE_NAME_PROP_NAME);

        Operation.CompletionHandler imageCompletionHandler = (o, ex) -> {
            if (ex != null) {
                fail(request, o, ex);
            } else {
                handleExceptions(
                        request,
                        op,
                        () -> {
                            logInfo("Image loaded: %s on remote machine: %s", ref,
                                    computeState.documentSelfLink);
                            patchTaskStage(request, TaskState.TaskStage.FINISHED, null);
                        }
                );
            }
        };

        imageRetrievalManager.retrieveAgentImage(
                ref,
                request,
                (imageData) -> {
                    processLoadedImageData(computeState, commandInput, imageData, ref,
                            imageCompletionHandler);
                });
    }

    private void processLoadedImageData(ComputeService.ComputeState computeState,
            CommandInput commandInput, byte[] imageData, String fileName,
            Operation.CompletionHandler
            imageCompletionHandler) {
        if (imageData == null || imageData.length == 0) {
            String errMsg = String.format("No content loaded for file: %s ", fileName);
            this.logSevere(errMsg);
            imageCompletionHandler.handle(null, new LocalizableValidationException(errMsg, "adapter.load.image.empty", fileName, ""));
            return;
        }

        logInfo("Loaded content for file: %s . Now sending to host...", fileName);

        CommandInput loadCommandInput = new CommandInput(commandInput)
                .withProperty(DOCKER_IMAGE_DATA_PROP_NAME, imageData);
        getCommandExecutor().loadImage(loadCommandInput, imageCompletionHandler);
    }

    private void doInspectImage(AdapterRequest request, ComputeService.ComputeState computeState,
            CommandInput commandInput) {
        logInfo("Inspecting docker image on host: " + computeState
                .documentSelfLink);

        Map<String, String> customProperties = request.customProperties;

        commandInput
                .withProperty(DOCKER_BUILD_IMAGE_INSPECT_NAME_PROP_NAME,
                        customProperties.get(DOCKER_BUILD_IMAGE_INSPECT_NAME_PROP_NAME));

        getCommandExecutor()
                .inspectImage(
                        commandInput,
                        (o, ex) -> {
                            if (ex != null) {
                                logWarning(
                                        "Unable to inspect image %s on the remote host: %s",
                                        commandInput.getProperties().get(
                                                DOCKER_BUILD_IMAGE_INSPECT_NAME_PROP_NAME), ex);
                                fail(request, o, ex);
                            } else {
                                logInfo("Completed inspect image request on remote machine: %s ",
                                        computeState.documentSelfLink);
                                JsonElement rawResult = o.getBody(JsonElement.class);
                                ServiceTaskCallbackResponse callbackResponse = createSearchResponse(rawResult);
                                patchTaskStage(request, TaskState.TaskStage.FINISHED, null,
                                        callbackResponse);
                            }

                        });
    }

    private ServiceTaskCallbackResponse createSearchResponse(JsonElement rawResult) {
        ImageInspectResponse inspectImageResponse = new ImageInspectResponse();
        inspectImageResponse.imageDetails = rawResult;
        return inspectImageResponse;
    }

    private static class ImageInspectResponse extends ServiceTaskCallbackResponse {
        @SuppressWarnings("unused")
        public JsonElement imageDetails;
    }

    private void doDeleteImage(AdapterRequest request, ComputeService.ComputeState computeState,
            CommandInput commandInput) {
        logInfo("Deleting docker image on host: " + computeState.documentSelfLink);

        Map<String, String> customProperties = request.customProperties;
        commandInput
                .withProperty(DOCKER_BUILD_IMAGE_TAG_PROP_NAME,
                        customProperties.get(DOCKER_BUILD_IMAGE_TAG_PROP_NAME));

        getCommandExecutor().deleteImage(
                commandInput,
                (operation, ex) -> {
                    String imageName = (String) commandInput.getProperties().get(
                            DOCKER_BUILD_IMAGE_TAG_PROP_NAME);
                    if (ex != null) {
                        logWarning("Unable to delete image %s on the remote host: %s", imageName,
                                ex);
                        fail(request, operation, ex);
                    } else {
                        logInfo("Image deleted %s on remote machine: %s ", imageName,
                                computeState.documentSelfLink);
                    }

                });
    }

    private void doBuildImage(AdapterRequest request, ComputeService.ComputeState computeState,
            CommandInput commandInput) {
        updateSslTrust(request, commandInput);

        Map<String, String> customProperties = request.customProperties;

        DockerImageHostRequest buildRequest = (DockerImageHostRequest) request;
        commandInput
                .withProperty(DOCKER_BUILD_IMAGE_DOCKERFILE_DATA, buildRequest.getDockerImageData())
                .withProperty(DOCKER_BUILD_IMAGE_DOCKERFILE_PROP_NAME,
                        customProperties.get(DOCKER_BUILD_IMAGE_DOCKERFILE_PROP_NAME))
                .withProperty(DOCKER_BUILD_IMAGE_FORCERM_PROP_NAME, customProperties.get
                        (DOCKER_BUILD_IMAGE_FORCERM_PROP_NAME))
                .withProperty(DOCKER_BUILD_IMAGE_NOCACHE_PROP_NAME, customProperties.get
                        (DOCKER_BUILD_IMAGE_NOCACHE_PROP_NAME))
                .withProperty(DOCKER_BUILD_IMAGE_TAG_PROP_NAME,
                        customProperties.get(DOCKER_BUILD_IMAGE_TAG_PROP_NAME));

        if (customProperties.get(DOCKER_BUILD_IMAGE_BUILDARGS_PROP_NAME) != null) {
            commandInput.withProperty(DOCKER_BUILD_IMAGE_BUILDARGS_PROP_NAME, customProperties.get
                    (DOCKER_BUILD_IMAGE_BUILDARGS_PROP_NAME));
        }

        getCommandExecutor().buildImage(
                commandInput,
                (operation, ex) -> {
                    String imageName = (String) commandInput.getProperties().get(
                            DOCKER_BUILD_IMAGE_TAG_PROP_NAME);
                    if (ex != null) {
                        logSevere("Unable to build image %s on the remote host! ", imageName);
                        fail(request, operation, ex);
                    } else {
                        logInfo("Image created: %s on remote machine: %s", imageName,
                                computeState.documentSelfLink);

                        patchTaskStage(request, TaskState.TaskStage.FINISHED, null);
                    }

                });
    }

    private void updateSslTrust(AdapterRequest request, CommandInput commandInput) {
        if (request.customProperties == null) {
            request.customProperties = new HashMap<>();
        }
        commandInput.withProperty(SSL_TRUST_CERT_PROP_NAME,
                request.customProperties.get(SSL_TRUST_CERT_PROP_NAME));

        commandInput.withProperty(SSL_TRUST_ALIAS_PROP_NAME,
                request.customProperties.get(SSL_TRUST_ALIAS_PROP_NAME));
    }

}
