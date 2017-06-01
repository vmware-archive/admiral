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
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_FROM_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_REPOSITORY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_TAG_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_ALIAS_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_CERT_PROP_NAME;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ImageOperationType;
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

        BaseRequestContext ctx = new BaseRequestContext();
        ctx.request = request;
        ctx.tenantLinks = request.tenantLinks;

        logFine("Processing host docker image operation request %s",
                request.getRequestTrackingLog());
        getContainerHost(request, op, request.resourceReference,
                (computeState, commandInput) -> processOperation(ctx, op, computeState,
                        commandInput, request.getOperationType()));
    }

    private void processOperation(BaseRequestContext ctx, Operation op,
            ComputeService.ComputeState computeState, CommandInput cmdInput, ImageOperationType
            operationType) {
        ctx.commandInput = cmdInput;
        switch (operationType) {
        case BUILD:
            doBuildImage(ctx, computeState);
            op.complete();
            break;
        case LOAD:
            doLoadImage(ctx, computeState);
            op.complete();
            break;
        case CREATE:
            ctx.imageName = ctx.request.customProperties.get(DOCKER_IMAGE_NAME_PROP_NAME);
            processAuthentication(ctx, () -> doCreateImage(ctx, computeState));
            op.complete();
            break;
        case TAG:
            doTagImage(ctx, computeState);
            op.complete();
            break;
        case DELETE:
            doDeleteImage(ctx, computeState);
            op.complete();
            break;
        case INSPECT:
            doInspectImage(ctx, computeState);
            op.complete();
            break;
        default:
            String errorMsg = "Unexpected image operation type: " + operationType;
            logWarning(errorMsg);
            op.fail(new IllegalArgumentException(errorMsg));

        }
    }

    private void doCreateImage(BaseRequestContext ctx, ComputeService.ComputeState computeState) {

        String fullImageName = ctx.request.customProperties.get(DOCKER_IMAGE_NAME_PROP_NAME);

        // use 'fromImage' - this will perform a docker pull
        ctx.commandInput.withProperty(DOCKER_IMAGE_FROM_PROP_NAME, fullImageName);

        logInfo("Pulling image: %s %s", fullImageName, ctx.request.getRequestTrackingLog());

        Operation.CompletionHandler imageCompletionHandler = (o, ex) -> {
            if (ex != null) {
                logWarning("Failure while pulling image [%s] on host [%s]",
                        fullImageName,
                        computeState.documentSelfLink);
                fail(ctx.request, o, ex);
            } else {
                handleExceptions(ctx.request, null, () -> {
                    logInfo("Image pulled: %s on remote machine: %s", fullImageName,
                            computeState.documentSelfLink);
                    patchTaskStage(ctx.request, TaskState.TaskStage.FINISHED, null);
                });
            }
        };

        getCommandExecutor().createImage(ctx.commandInput, imageCompletionHandler);
    }

    private void doTagImage(BaseRequestContext ctx, ComputeService.ComputeState computeState) {
        CommandInput tagCommandInput = ctx.commandInput;
        String fullImageName = ctx.request.customProperties.get(DOCKER_IMAGE_NAME_PROP_NAME);
        String imageRepo = ctx.request.customProperties.get(DOCKER_IMAGE_REPOSITORY_PROP_NAME);
        String imageTag = ctx.request.customProperties.get(DOCKER_IMAGE_TAG_PROP_NAME);

        tagCommandInput.withProperty(DOCKER_IMAGE_NAME_PROP_NAME, fullImageName);
        tagCommandInput.withProperty(DOCKER_IMAGE_REPOSITORY_PROP_NAME, imageRepo);
        tagCommandInput.withProperty(DOCKER_IMAGE_TAG_PROP_NAME, imageTag);

        logInfo("Tagging image: %s %s with repo: %s, tag: %s", fullImageName,
                ctx.request.getRequestTrackingLog(), imageRepo, imageTag);

        Operation.CompletionHandler imageCompletionHandler = (o, ex) -> {
            if (ex != null) {
                logWarning("Failure while tagging image [%s] on host [%s]",
                        fullImageName,
                        computeState.documentSelfLink);
                fail(ctx.request, o, ex);
            } else {
                handleExceptions(
                        ctx.request,
                        null,
                        () -> {
                            logInfo("Image tagged: %s on remote machine: %s", fullImageName,
                                    computeState.documentSelfLink);
                            patchTaskStage(ctx.request, TaskState.TaskStage.FINISHED, null);
                        });
            }
        };

        getCommandExecutor().tagImage(tagCommandInput, imageCompletionHandler);
    }

    private void doLoadImage(BaseRequestContext ctx, ComputeService.ComputeState computeState) {

        String ref = ctx.request.customProperties.get(DOCKER_IMAGE_NAME_PROP_NAME);

        Operation.CompletionHandler imageCompletionHandler = (o, ex) -> {
            if (ex != null) {
                logWarning("Failure while loading image [%s] on host [%s]",
                        ref,
                        computeState.documentSelfLink);
                fail(ctx.request, o, ex);
            } else {
                handleExceptions(
                        ctx.request,
                        null,
                        () -> {
                            logInfo("Image loaded: %s on remote machine: %s", ref,
                                    computeState.documentSelfLink);
                            patchTaskStage(ctx.request, TaskState.TaskStage.FINISHED, null);
                        });
            }
        };

        imageRetrievalManager.retrieveAgentImage(
                ref,
                ctx.request,
                (imageData) -> {
                    processLoadedImageData(ctx, imageData, ref, imageCompletionHandler);
                });
    }

    private void processLoadedImageData(BaseRequestContext ctx, byte[] imageData, String fileName,
            Operation.CompletionHandler imageCompletionHandler) {
        if (imageData == null || imageData.length == 0) {
            String errMsg = String.format("No content loaded for file: %s ", fileName);
            this.logSevere(errMsg);
            imageCompletionHandler.handle(null, new LocalizableValidationException(errMsg,
                    "adapter.load.image.empty", fileName, ""));
            return;
        }

        logInfo("Loaded content for file: %s . Now sending to host...", fileName);

        CommandInput loadCommandInput = new CommandInput(ctx.commandInput)
                .withProperty(DOCKER_IMAGE_DATA_PROP_NAME, imageData);
        getCommandExecutor().loadImage(loadCommandInput, imageCompletionHandler);
    }

    private void doInspectImage(BaseRequestContext ctx, ComputeService.ComputeState computeState) {
        logFine("Inspecting docker image on host: " + computeState.documentSelfLink);

        Map<String, String> customProperties = ctx.request.customProperties;

        CommandInput commandInput = ctx.commandInput;
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
                                                DOCKER_BUILD_IMAGE_INSPECT_NAME_PROP_NAME),
                                        ex);
                                fail(ctx.request, o, ex);
                            } else {
                                logFine("Completed inspect image request on remote machine: %s ",
                                        computeState.documentSelfLink);
                                JsonElement rawResult = o.getBody(JsonElement.class);
                                ServiceTaskCallbackResponse callbackResponse = createInspectResponse(
                                        ctx.request, rawResult);
                                patchTaskStage(ctx.request, TaskState.TaskStage.FINISHED, null,
                                        callbackResponse);
                            }

                        });
    }

    private ServiceTaskCallbackResponse createInspectResponse(AdapterRequest request, JsonElement
            rawResult) {
        ServiceTaskCallbackResponse finished = request.serviceTaskCallback.getFinishedResponse();
        ImageInspectResponse inspectImageResponse = new ImageInspectResponse();
        inspectImageResponse.imageDetails = rawResult;
        inspectImageResponse.taskInfo = finished.taskInfo;
        inspectImageResponse.taskSubStage = finished.taskSubStage;
        inspectImageResponse.customProperties = finished.customProperties;
        return inspectImageResponse;
    }

    private static class ImageInspectResponse extends ServiceTaskCallbackResponse {
        @SuppressWarnings("unused")
        public JsonElement imageDetails;
    }

    private void doDeleteImage(BaseRequestContext ctx, ComputeService.ComputeState computeState) {
        logInfo("Deleting docker image on host: " + computeState.documentSelfLink);

        CommandInput commandInput = ctx.commandInput;
        Map<String, String> customProperties = ctx.request.customProperties;
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
                        fail(ctx.request, operation, ex);
                    } else {
                        logInfo("Image deleted %s on remote machine: %s ", imageName,
                                computeState.documentSelfLink);
                    }

                });
    }

    private void doBuildImage(BaseRequestContext ctx, ComputeService.ComputeState computeState) {
        updateSslTrust(ctx.request, ctx.commandInput);

        Map<String, String> customProperties = ctx.request.customProperties;

        DockerImageHostRequest buildRequest = (DockerImageHostRequest) ctx.request;
        ctx.commandInput
                .withProperty(DOCKER_BUILD_IMAGE_DOCKERFILE_DATA, buildRequest.getDockerImageData())
                .withProperty(DOCKER_BUILD_IMAGE_DOCKERFILE_PROP_NAME,
                        customProperties.get(DOCKER_BUILD_IMAGE_DOCKERFILE_PROP_NAME))
                .withProperty(DOCKER_BUILD_IMAGE_FORCERM_PROP_NAME,
                        customProperties.get(DOCKER_BUILD_IMAGE_FORCERM_PROP_NAME))
                .withProperty(DOCKER_BUILD_IMAGE_NOCACHE_PROP_NAME,
                        customProperties.get(DOCKER_BUILD_IMAGE_NOCACHE_PROP_NAME))
                .withProperty(DOCKER_BUILD_IMAGE_TAG_PROP_NAME,
                        customProperties.get(DOCKER_BUILD_IMAGE_TAG_PROP_NAME));

        if (customProperties.get(DOCKER_BUILD_IMAGE_BUILDARGS_PROP_NAME) != null) {
            ctx.commandInput.withProperty(DOCKER_BUILD_IMAGE_BUILDARGS_PROP_NAME,
                    customProperties.get(DOCKER_BUILD_IMAGE_BUILDARGS_PROP_NAME));
        }

        getCommandExecutor().buildImage(
                ctx.commandInput,
                (operation, ex) -> {
                    String imageName = (String) ctx.commandInput.getProperties().get(
                            DOCKER_BUILD_IMAGE_TAG_PROP_NAME);
                    if (ex != null) {
                        logSevere("Unable to build image %s on the remote host! ", imageName);
                        fail(ctx.request, operation, ex);
                    } else {
                        logInfo("Image created: %s on remote machine: %s", imageName,
                                computeState.documentSelfLink);

                        patchTaskStage(ctx.request, TaskState.TaskStage.FINISHED, null);
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
