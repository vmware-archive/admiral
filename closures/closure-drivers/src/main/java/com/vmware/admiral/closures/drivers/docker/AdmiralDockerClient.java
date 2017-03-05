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

package com.vmware.admiral.closures.drivers.docker;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.common.ImageOperationType;
import com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor;
import com.vmware.admiral.adapter.docker.service.DockerHostAdapterImageService;
import com.vmware.admiral.adapter.docker.service.DockerImageHostRequest;
import com.vmware.admiral.closures.drivers.ClosureDockerClient;
import com.vmware.admiral.closures.drivers.ContainerConfiguration;
import com.vmware.admiral.closures.drivers.ImageConfiguration;
import com.vmware.admiral.closures.services.adapter.AdmiralAdapterFactoryService;
import com.vmware.admiral.closures.services.adapter.AdmiralAdapterService;
import com.vmware.admiral.closures.services.adapter.AdmiralAdapterService.AdmiralAdapterTaskState;
import com.vmware.admiral.closures.services.images.DockerImageFactoryService;
import com.vmware.admiral.closures.util.ClosureUtils;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Docker client using Admiral services.
 */
public class AdmiralDockerClient implements ClosureDockerClient {

    private final ServiceHost serviceHost;

    public AdmiralDockerClient(ServiceHost serviceHost) {
        this.serviceHost = serviceHost;
    }

    @Override
    public void createAndStartContainer(String closureLink, ImageConfiguration imageConfig,
            ContainerConfiguration configuration,
            Consumer<Throwable> errorHandler) {
        logInfo("Sending provisioning request of execution container...%s", closureLink);

        AdmiralAdapterService.AdmiralAdapterTaskState provisioningRequest = new AdmiralAdapterTaskState();
        provisioningRequest.imageConfig = imageConfig;
        provisioningRequest.configuration = configuration;

        provisioningRequest.serviceTaskCallback = ServiceTaskCallback.create(closureLink);

        URI uri = UriUtils.buildUri(getHost(), AdmiralAdapterFactoryService.FACTORY_LINK);
        getHost().sendRequest(OperationUtil.createForcedPost(uri)
                .setBody(provisioningRequest)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logError("Unable to send provisioning request: ", ex);
                        errorHandler.accept(ex);
                        return;
                    }

                    logInfo("Docker provisioning request sent. image: %s, host: %s", imageConfig,
                            configuration);
                    errorHandler.accept(null);
                }));
    }

    @Override
    public void removeContainer(String containerLink, Consumer<Throwable> errorHandler) {
        logInfo("Removing container state with self link: %s", containerLink);

        RequestBrokerState removeRequest = new RequestBrokerState();
        removeRequest.resourceType = ResourceType.CONTAINER_TYPE.getName();
        removeRequest.resourceLinks = new HashSet<>();
        removeRequest.resourceLinks.add(containerLink);
        removeRequest.operation = ContainerOperationType.DELETE.id;

        getHost().sendRequest(
                Operation.createPost(getHost(), RequestBrokerFactoryService.SELF_LINK)
                        .setBody(removeRequest)
                        .setReferer(getHost().getUri())
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                logError("Exception while submitting remove container request: ", e);
                                errorHandler.accept(e);
                                return;
                            }

                            // should be only one
                            logInfo("Container removed successfully: %s", containerLink);
                        }));

        logInfo("Removal request of execution container has been sent.");
    }

    @Override
    public void cleanImage(String imageName, String computeStateLink, Consumer<Throwable> errorHandler) {
        logInfo("Sending docker image clean request for image: %s on host: %s", imageName, computeStateLink);

        DockerImageHostRequest request = new DockerImageHostRequest();
        request.operationTypeId = ImageOperationType.DELETE.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = UriUtils.buildUri(getHost(), computeStateLink);
        request.customProperties = new HashMap<>();

        request.customProperties.putIfAbsent(DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_TAG_PROP_NAME, imageName);

        getHost().sendRequest(Operation
                .createPatch(getHost(), DockerHostAdapterImageService.SELF_LINK)
                .setBody(request)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logError("Unable to build image on docker host: ", ex);
                        errorHandler.accept(ex);
                        return;
                    }

                    logInfo("Docker clean image request sent. Image: %s, host: %s", imageName, computeStateLink);
                }));
    }

    @Override
    public void inspectImage(String imageName, String computeStateLink, Consumer<Throwable>
            errorHandler) {
        logInfo("Sending docker image inspect request for image: %s on host: %s", imageName,
                computeStateLink);

        DockerImageHostRequest request = new DockerImageHostRequest();
        request.operationTypeId = ImageOperationType.INSPECT.id;
        String completionServiceCallBack = createImageBuildRequestUri(imageName, computeStateLink);
        request.serviceTaskCallback = ServiceTaskCallback.create(completionServiceCallBack);
        request.resourceReference = UriUtils.buildUri(getHost(), computeStateLink);

        request.customProperties = new HashMap<>();
        request.customProperties
                .putIfAbsent(DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_INSPECT_NAME_PROP_NAME, imageName);

        getHost().sendRequest(Operation
                .createPatch(getHost(), DockerHostAdapterImageService.SELF_LINK)
                .setBody(request)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logError("Unable to inspect image on docker host: ", ex);
                        errorHandler.accept(ex);
                        return;
                    }

                    logInfo("Docker inspect image request sent. Image: %s, host: %s", imageName, computeStateLink);
                }));
    }

    private String createImageBuildRequestUri(String imageName, String computeStateLink) {
        String imageBuildRequestId = ClosureUtils.calculateHash(new String[] { imageName, "/", computeStateLink });

        return UriUtils.buildUriPath(DockerImageFactoryService.FACTORY_LINK, imageBuildRequestId);
    }

    private void logInfo(String message, Object... values) {
        Utils.log(getClass(), getClass().getSimpleName(), Level.INFO, message, values);
    }

    private void logError(String message, Object... values) {
        Utils.log(getClass(), getClass().getSimpleName(), Level.SEVERE, message, values);
    }

    private ServiceHost getHost() {
        return serviceHost;
    }

}
