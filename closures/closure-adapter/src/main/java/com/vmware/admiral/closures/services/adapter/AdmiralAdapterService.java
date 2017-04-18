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

package com.vmware.admiral.closures.services.adapter;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_BUILDARGS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_DOCKERFILE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_FORCERM_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_NOCACHE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_TAG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_CREATE_USE_LOCAL_IMAGE_WITH_PRIORITY;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_REPOSITORY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_TAG_PROP_NAME;
import static com.vmware.admiral.closures.util.ClosureUtils.loadDockerImageData;
import static com.vmware.admiral.common.ManagementUriParts.CLOSURES_CONTAINER_DESC;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.admiral.common.util.ServiceDocumentQuery.error;
import static com.vmware.admiral.common.util.ServiceDocumentQuery.result;
import static com.vmware.admiral.common.util.ServiceDocumentQuery.resultLink;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.vmware.admiral.adapter.common.ImageOperationType;
import com.vmware.admiral.adapter.docker.service.DockerImageHostRequest;
import com.vmware.admiral.closures.drivers.ContainerConfiguration;
import com.vmware.admiral.closures.drivers.DriverConstants;
import com.vmware.admiral.closures.drivers.ImageConfiguration;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.closures.services.images.DockerImage;
import com.vmware.admiral.closures.services.images.DockerImageFactoryService;
import com.vmware.admiral.closures.util.ClosureProps;
import com.vmware.admiral.closures.util.ClosureUtils;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.container.Ulimit;
import com.vmware.admiral.request.ContainerAllocationTaskFactoryService;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.admiral.request.ReservationTaskService;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ConfigurationService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class AdmiralAdapterService extends
        AbstractTaskStatefulService<AdmiralAdapterService.AdmiralAdapterTaskState,
                AdmiralAdapterService.AdmiralAdapterTaskState.SubStage> {

    private static final String DISPLAY_NAME = "Closure Container Provisioning";

    private static final String BUILD_IMAGE_RETRIES_COUNT_PARAM_NAME = "build.closure.image.retries.count";

    private final Random randomIntegers = new Random();

    private volatile Integer retriesCount;

    private ContainerDescription cachedContainerDescription;

    public static class AdmiralAdapterTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<AdmiralAdapterTaskState.SubStage> {

        private static final String FIELD_NAME_CONTAINER_IMAGE = "imageConfig";
        private static final String FIELD_NAME_CONFIGURATION = "configuration";

        public enum SubStage {
            CREATED,
            RESERVING,
            RESOURCE_RESERVED,
            PLACEMENT_SELECTED,
            COMPUTE_STATE_SELECTED,
            BASE_IMAGE_PULLING,
            BASE_IMAGE_PULLED,
            COMPLETED,
            ERROR;

            static final Set<AdmiralAdapterTaskState.SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(RESERVING, BASE_IMAGE_PULLING));
        }

        /** Image configuration to use */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public ImageConfiguration imageConfig;

        /** Container configuration to be applied. */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public ContainerConfiguration configuration;

        /** Resource pool to use on provisioning */
        @PropertyOptions(usage = { SERVICE_USE, SINGLE_ASSIGNMENT, LINK,
                AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String placementZoneLink;

        /** Resource policy to use on provisioning */
        @PropertyOptions(usage = { SERVICE_USE, SINGLE_ASSIGNMENT, LINK,
                AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String groupResourcePlacementLink;

        /** Compute state to use on provisioning */
        @PropertyOptions(usage = { SERVICE_USE, SINGLE_ASSIGNMENT, LINK,
                AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String selectedComputeLink;

        /** (Internal) Set by task after the ComputeState is found to host the containers */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public List<HostSelectionFilter.HostSelection> hostSelections;
    }

    public AdmiralAdapterService() {
        super(AdmiralAdapterTaskState.class, AdmiralAdapterTaskState.SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = AdmiralAdapterTaskState.SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(AdmiralAdapterTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            proceedWithContainerDescription(state, null);
            break;
        case RESERVING:
            break;
        case RESOURCE_RESERVED:
            proceedWithGroupPlacement(state);
            break;
        case PLACEMENT_SELECTED:
            handlePlacementSelected(state);
            break;
        case COMPUTE_STATE_SELECTED:
            handleComputeSelected(state);
            break;
        case BASE_IMAGE_PULLING:
            break;
        case BASE_IMAGE_PULLED:
            fetchContainerDescription(state,
                    (containerDesc) -> this.tagBaseImage(state, containerDesc));
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

    private void createReservationTasks(AdmiralAdapterTaskState state, ContainerDescription
            containerDesc) {

        if (containerDesc == null) {
            fetchContainerDescription(state, (cd) -> createReservationTasks(state, cd));
            return;
        }

        ReservationTaskService.ReservationTaskState rsrvTask = new ReservationTaskService.ReservationTaskState();
        rsrvTask.documentSelfLink = getSelfId();
        rsrvTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskState.TaskStage.STARTED,
                AdmiralAdapterTaskState.SubStage.RESOURCE_RESERVED,
                TaskState.TaskStage.STARTED,
                AdmiralAdapterTaskState.SubStage.ERROR);

        rsrvTask.resourceCount = 1;
        rsrvTask.tenantLinks = state.tenantLinks;
        rsrvTask.resourceType = ResourceType.CONTAINER_TYPE.getName();
        rsrvTask.resourceDescriptionLink = containerDesc.documentSelfLink;
        rsrvTask.customProperties = mergeCustomProperties(
                state.customProperties, containerDesc.customProperties);
        rsrvTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ReservationTaskFactoryService.SELF_LINK)
                .setBody(rsrvTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failed to create closure reservation task"
                                + containerDesc.documentSelfLink, e);
                        return;
                    }
                    proceedTo(AdmiralAdapterTaskState.SubStage.RESERVING);
                }));
    }

    private void handlePlacementSelected(AdmiralAdapterTaskState state) {
        if (cachedContainerDescription == null) {
            fetchContainerDescription(state,
                    (contDesc) -> selectComputeStates(state, contDesc));
            return;
        }

        selectComputeStates(state, cachedContainerDescription);
    }

    private void selectComputeStates(AdmiralAdapterTaskState state, ContainerDescription contDesc) {
        proceedWithSelection(contDesc, state,
                (computeResult) -> {
                    if (computeResult.hasException()) {
                        failTask("Unable to fetch compute states: ", computeResult.getException());
                        return;
                    }

                    proceedTo(AdmiralAdapterTaskState.SubStage.COMPUTE_STATE_SELECTED, (s) -> {
                        s.selectedComputeLink = computeResult.getDocumentSelfLink();
                    });

                });
    }

    private void handleComputeSelected(AdmiralAdapterTaskState state) {
        if (cachedContainerDescription == null) {
            fetchContainerDescription(state,
                    (contDesc) -> proceedWithBaseDockerImage(contDesc, state, 0));
            return;
        }

        proceedWithBaseDockerImage(cachedContainerDescription, state, 0);
    }

    @Override
    protected void validateStateOnStart(AdmiralAdapterTaskState state)
            throws IllegalArgumentException {
        assertNotNull(state.imageConfig, AdmiralAdapterTaskState.FIELD_NAME_CONTAINER_IMAGE);
        assertNotNull(state.configuration, AdmiralAdapterTaskState.FIELD_NAME_CONFIGURATION);
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private void seedPeers(Collection<String> availableHosts, List<String> computeStateLinks,
            String selectedComputeLink, Consumer<String> consumer) {
        //        Collection<String> availableStates = new ArrayList<>(hostLinks);
        List<String> selectedForSeed = availableHosts.parallelStream().filter(
                (s) -> !s.equalsIgnoreCase(selectedComputeLink) && !computeStateLinks.contains(s)
        ).collect(Collectors.toList());

        selectedForSeed.parallelStream().forEach((s) -> consumer.accept(s));
    }

    private void proceedWithBaseDockerImage(ContainerDescription containerDesc,
            AdmiralAdapterTaskState state, int retriesCount) {
        logInfo("Checking docker build image request for image: %s host: %s", containerDesc.image,
                state.selectedComputeLink);
        String baseImageName = ClosureUtils.createBaseImageName(containerDesc.image);
        String dockerBuildImageLink = createImageBuildRequestUri(
                createBaseImageDockerName(state.imageConfig),
                state.selectedComputeLink);
        getHost().sendRequest(Operation.createGet(getHost(), dockerBuildImageLink)
                .setReferer(getHost().getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        if (op.getStatusCode() == 404) {
                            logWarning("No base image found! image: %s compute state: %s",
                                    baseImageName,
                                    state.selectedComputeLink);
                            // proceed with images creation
                            proceedWithBaseImageCreation(containerDesc, state, 0);
                        } else {
                            logWarning("Unable to fetch base image requests:", ex);
                            failTask("Unable to fetch base image requests", ex);
                        }
                    } else {
                        logInfo("Docker base image request already created.");
                        DockerImage imageRequest = op.getBody(DockerImage.class);
                        handleBaseDockerBuildImageRequest(containerDesc, state, imageRequest,
                                retriesCount);
                    }
                }));
    }

    private void proceedWithDockerImage(ContainerDescription containerDesc,
            AdmiralAdapterTaskState state,
            int retriesCount) {
        logInfo("Checking docker build image request for image: %s host: %s, retries: %s",
                containerDesc.image, state.selectedComputeLink, retriesCount);
        String dockerBuildImageLink = createImageBuildRequestUri(containerDesc.image,
                state.selectedComputeLink);
        getHost().sendRequest(Operation.createGet(getHost(), dockerBuildImageLink)
                .setReferer(getHost().getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        if (op.getStatusCode() == 404) {
                            logWarning("No build image found! image: %s compute state: %s",
                                    containerDesc.image, state.selectedComputeLink);
                            // proceed with images creation
                            proceedWithDockerImageCreation(containerDesc, state, 0);
                        } else {
                            logWarning("Unable to fetch docker image requests:", ex);
                            failTask("Unable to fetch docker image requests", ex);
                        }
                    } else {
                        logInfo("Docker build image request already created.");
                        DockerImage imageRequest = op.getBody(DockerImage.class);
                        handleDockerBuildImageRequest(containerDesc, state, imageRequest,
                                retriesCount);
                    }
                }));
    }

    private void ensurePropertyExists(Consumer<Integer> callback) {
        if (retriesCount != null) {
            callback.accept(retriesCount);
        } else {
            String maxRetriesCountConfigPropPath = UriUtils.buildUriPath(
                    ConfigurationService.ConfigurationFactoryService.SELF_LINK,
                    BUILD_IMAGE_RETRIES_COUNT_PARAM_NAME);
            sendRequest(Operation.createGet(this, maxRetriesCountConfigPropPath)
                    .setCompletion((o, ex) -> {
                        // in case of exception the default retry count will be 3
                        retriesCount = 3;
                        if (ex == null) {
                            retriesCount = Integer.valueOf(
                                    o.getBody(ConfigurationService.ConfigurationState.class).value);
                        }
                        callback.accept(retriesCount);
                    }));
        }
    }

    private void proceedWithBaseImageCreation(ContainerDescription containerDesc,
            AdmiralAdapterTaskState state,
            int retriesCount) {
        URI uri = UriUtils.buildUri(getHost(), DockerImageFactoryService.FACTORY_LINK);

        DockerImage buildImage = new DockerImage();
        buildImage.name = createBaseImageDockerName(state.imageConfig);
        buildImage.computeStateLink = state.selectedComputeLink;
        buildImage.taskInfo = TaskState.create();
        buildImage.documentSelfLink = createImageBuildRequestUri(buildImage.name,
                state.selectedComputeLink);

        logInfo("Creating docker image request: %s ", uri);
        getHost().sendRequest(OperationUtil.createForcedPost(uri)
                .setBody(buildImage)
                .setReferer(getHost().getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Exception while submitting docker image request: ", e);
                        failTask("Unable to submit docker image request", e);
                        return;
                    }

                    logInfo("Docker image request has been created successfully.");
                    // 2. Proceed with image operation request
                    handleBaseImage(containerDesc, state, buildImage, state.selectedComputeLink,
                            () -> proceedWithBaseDockerImage(containerDesc, state, retriesCount));
                }));
    }

    private void handleBaseImage(ContainerDescription containerDesc,
            AdmiralAdapterTaskState state, DockerImage buildImage, String targetComputeLink,
            Runnable delegate) {
        if (state.imageConfig.registry != null && !state.imageConfig.registry
                .isEmpty()) {
            String baseImageName = createRegistryBaseImageName(state.imageConfig.registry,
                    buildImage.name);
            createBaseImage(baseImageName, targetComputeLink);
        } else {
            String baseImageName = ClosureUtils.createBaseImageName(containerDesc.image);
            loadBaseImage(baseImageName, state.imageConfig, targetComputeLink);

            // 3. Poll for completion
            getHost().schedule(delegate::run, 5, TimeUnit.SECONDS);
        }
    }

    private void tagBaseImage(AdmiralAdapterTaskState state, ContainerDescription containerDesc) {
        ImageConfiguration imageConfig = state.imageConfig;
        String computeStateLink = state.selectedComputeLink;
        DockerImageHostRequest request = new DockerImageHostRequest();
        request.operationTypeId = ImageOperationType.TAG.id;
        String baseDockerName = createBaseImageDockerName(imageConfig);
        String completionServiceCallBack = createImageBuildRequestUri(baseDockerName,
                computeStateLink);
        request.serviceTaskCallback = ServiceTaskCallback.create(completionServiceCallBack);
        request.resourceReference = UriUtils.buildUri(getHost(), computeStateLink);

        logInfo("Executing TAG operation %s on remote docker host: %s ",
                request.operationTypeId,
                request.resourceReference);

        request.customProperties = new HashMap<>();
        String fullImageName = createRegistryBaseImageName(imageConfig.registry, baseDockerName);
        request.customProperties.putIfAbsent(DOCKER_IMAGE_NAME_PROP_NAME, fullImageName);
        request.customProperties
                .putIfAbsent(DOCKER_IMAGE_REPOSITORY_PROP_NAME, imageConfig.baseImageName);
        request.customProperties
                .putIfAbsent(DOCKER_IMAGE_TAG_PROP_NAME, imageConfig.baseImageVersion);

        getHost().sendRequest(Operation
                .createPatch(getHost(), ManagementUriParts.ADAPTER_DOCKER_IMAGE_HOST)
                .setBody(request)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        String errMsg = String.format("Tag Image operation %s failed on docker "
                                + "host: ", request.operationTypeId);
                        logSevere(errMsg, ex);
                        failTask(errMsg + computeStateLink, ex);
                        return;
                    }

                    logInfo("Tag Image operation %s request sent. Image: %s, host: %s",
                            request.operationTypeId,
                            baseDockerName,
                            computeStateLink);
                }));

        // 3. Poll for completion
        proceedWithBaseDockerImage(containerDesc, state, 0);
    }

    private void createBaseImage(String baseImageName, String computeStateLink) {
        DockerImageHostRequest request = new DockerImageHostRequest();
        request.operationTypeId = ImageOperationType.CREATE.id;

        request.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskState.TaskStage.STARTED, AdmiralAdapterTaskState.SubStage.BASE_IMAGE_PULLED,
                TaskState.TaskStage.STARTED,
                AdmiralAdapterTaskState.SubStage.ERROR);
        request.resourceReference = UriUtils.buildUri(getHost(), computeStateLink);

        logInfo("Executing CREATE operation %s to remote docker host: %s ", request
                        .operationTypeId,
                request.resourceReference);

        request.customProperties = new HashMap<>();
        request.customProperties.putIfAbsent(DOCKER_IMAGE_NAME_PROP_NAME, baseImageName);

        getHost().sendRequest(Operation
                .createPatch(getHost(), ManagementUriParts.ADAPTER_DOCKER_IMAGE_HOST)
                .setBody(request)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        String errMsg = String.format("Create Image operation %s failed on docker "
                                + "host: ", request.operationTypeId);
                        logSevere(errMsg, ex);
                        failTask(errMsg + computeStateLink, ex);
                        return;
                    }

                    logInfo("Create Image operation %s request sent. Image: %s, host: %s",
                            request.operationTypeId,
                            baseImageName,
                            computeStateLink);
                    proceedTo(AdmiralAdapterTaskState.SubStage.BASE_IMAGE_PULLING);
                }));
    }

    private void loadBaseImage(String baseImageName, ImageConfiguration imageConfig, String
            computeStateLink) {
        DockerImageHostRequest request = new DockerImageHostRequest();
        request.operationTypeId = ImageOperationType.LOAD.id;
        String baseDockerName = createBaseImageDockerName(imageConfig);
        String completionServiceCallBack = createImageBuildRequestUri(baseDockerName,
                computeStateLink);
        request.serviceTaskCallback = ServiceTaskCallback.create(completionServiceCallBack);
        request.resourceReference = UriUtils.buildUri(getHost(), computeStateLink);

        logInfo("Loading image to remote docker host: %s ", computeStateLink);

        request.customProperties = new HashMap<>();
        request.customProperties.putIfAbsent(DOCKER_IMAGE_NAME_PROP_NAME, baseImageName);

        getHost().sendRequest(Operation
                .createPatch(getHost(), ManagementUriParts.ADAPTER_DOCKER_IMAGE_HOST)
                .setBody(request)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        String errMsg = String.format("Load image operation %s failed on docker "
                                + "host: ", request.operationTypeId);
                        logSevere(errMsg, ex);
                        failTask(errMsg + computeStateLink, ex);
                        return;
                    }

                    logInfo("Docker load image request sent. Image: %s, host: %s", baseImageName,
                            computeStateLink);
                }));
    }

    private String createBaseImageDockerName(ImageConfiguration imageConfig) {
        return String.format("%s:%s", imageConfig.baseImageName, imageConfig.baseImageVersion);
    }

    private String createRegistryBaseImageName(String registry, String fullImageName) {
        return String.format("%s/%s", registry, fullImageName);
    }

    private void seedWithBaseDockerImage(ContainerDescription containerDesc,
            String computeStateLink, AdmiralAdapterTaskState state) {
        String baseImageName = ClosureUtils.createBaseImageName(containerDesc.image);
        logInfo("Checking docker build image request for base image: %s host: %s", baseImageName,
                computeStateLink);
        String dockerBuildImageLink = createImageBuildRequestUri(createBaseImageDockerName
                (state.imageConfig), computeStateLink);
        getHost().sendRequest(Operation.createGet(getHost(), dockerBuildImageLink)
                .setReferer(getHost().getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        if (op.getStatusCode() == 404) {
                            logWarning("No base image found! image: %s compute state: %s",
                                    containerDesc.image,
                                    computeStateLink);
                            // proceed with images creation
                            seedWithBaseDockerImageCreation(containerDesc, computeStateLink, state);
                        } else {
                            logWarning("Unable to fetch docker base image request:", ex);
                        }
                    } else {
                        logInfo("Docker build base image request already created.");
                        DockerImage imageRequest = op.getBody(DockerImage.class);
                        if (TaskState.isFailed(imageRequest.taskInfo)
                                || TaskState.isCancelled(imageRequest.taskInfo)) {
                            logWarning("Failed to seed docker base image: %s", imageRequest);
                        } else if (TaskState.isFinished(imageRequest.taskInfo)) {
                            seedWithDockerImage(containerDesc, computeStateLink, state);
                            touchDockerImage(dockerBuildImageLink, imageRequest);
                        } else {
                            getHost().schedule(
                                    () -> seedWithBaseDockerImage(containerDesc, computeStateLink,
                                            state),
                                    5, TimeUnit.SECONDS);
                        }
                    }
                }));
    }

    private void seedWithDockerImage(ContainerDescription containerDesc, String computeStateLink,
            AdmiralAdapterTaskState state) {
        logInfo("Checking docker build image request for image: %s host: %s", containerDesc.image,
                computeStateLink);
        String dockerBuildImageLink = createImageBuildRequestUri(containerDesc.image,
                computeStateLink);
        getHost().sendRequest(Operation.createGet(getHost(), dockerBuildImageLink)
                .setReferer(getHost().getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        if (op.getStatusCode() == 404) {
                            logWarning("No build image found! image: %s compute state: %s",
                                    containerDesc.image,
                                    computeStateLink);
                            // proceed with images creation
                            seedWithDockerImageCreation(containerDesc, computeStateLink, state);
                        } else {
                            logWarning("Unable to fetch docker image requests:", ex);
                        }
                    } else {
                        logInfo("Docker build image request already created.");
                        DockerImage imageRequest = op.getBody(DockerImage.class);

                        touchDockerImage(dockerBuildImageLink, imageRequest);
                    }
                }));
    }

    private void seedWithBaseDockerImageCreation(ContainerDescription containerDesc,
            String computeStateLink, AdmiralAdapterTaskState state) {
        URI uri = UriUtils.buildUri(getHost(), DockerImageFactoryService.FACTORY_LINK);

        DockerImage buildImage = new DockerImage();
        buildImage.name = createBaseImageDockerName(state.imageConfig);
        buildImage.computeStateLink = computeStateLink;
        buildImage.taskInfo = TaskState.create();
        buildImage.documentSelfLink = createImageBuildRequestUri(buildImage.name, computeStateLink);

        logInfo("Creating docker build base image request: %s", uri);
        getHost().sendRequest(OperationUtil.createForcedPost(uri)
                .setBody(buildImage)
                .setReferer(getHost().getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Exception while submitting docker build image request: ", e);
                        failTask("Unable to submit docker build image request", e);
                        return;
                    }

                    logInfo("Docker build image request has been created successfully.");

                    // 2. Proceed with image operation request
                    handleBaseImage(containerDesc, state, buildImage, computeStateLink,
                            () -> seedWithBaseDockerImage(containerDesc, computeStateLink, state));
                }));
    }

    private void handleBaseDockerBuildImageRequest(ContainerDescription containerDesc,
            AdmiralAdapterTaskState state,
            DockerImage imageRequest, int retriesCount) {
        if (TaskState.isFinished(imageRequest.taskInfo)) {
            // Image has been already built, proceed with container allocation
            logInfo("Base Image has already been built. Creating child image...");
            proceedWithDockerImage(containerDesc, state, 0);
            touchDockerImage(imageRequest.documentSelfLink, imageRequest);
        } else if (TaskState.isFailed(imageRequest.taskInfo)) {
            AtomicInteger retryCount = new AtomicInteger(retriesCount);
            ensurePropertyExists((retryCountProperty) -> {
                if (retryCount.getAndIncrement() < retryCountProperty) {
                    // try to load base image again
                    proceedWithBaseImageCreation(containerDesc, state, retryCount.get());
                } else {
                    // Failed to build docker image
                    logWarning("Failed to build base image %s on host: %s Reason: %s",
                            containerDesc.image,
                            state.selectedComputeLink, imageRequest.taskInfo.failure);
                    String errorMessage = getErrorMsg(imageRequest);
                    failTask("Failed to build base image " + containerDesc.image + " on host: "
                                    + state.selectedComputeLink
                                    + " Reason: " + imageRequest.taskInfo.failure,
                            new Exception(errorMessage));
                }
            });
        } else {
            // Base image is still not ready. Wait until build process completes.
            logInfo("Base image is still building: %s", containerDesc.image);
            getHost()
                    .schedule(() -> proceedWithBaseDockerImage(containerDesc, state, 0), 5,
                            TimeUnit.SECONDS);
        }
    }

    private String getErrorMsg(DockerImage imageRequest) {
        return imageRequest.taskInfo.failure != null ? imageRequest.taskInfo.failure.message :
                "General error";
    }

    private void handleDockerBuildImageRequest(ContainerDescription containerDesc,
            AdmiralAdapterTaskState state,
            DockerImage imageRequest, int retriesCount) {
        if (TaskState.isFinished(imageRequest.taskInfo)) {
            // Image has been already built, proceed with container allocation
            logInfo("Image: %s is ready. Allocating docker container...", containerDesc.image);
            proceedWithProvidedPolicy(containerDesc, state);
            String dockerBuildImageLink = createImageBuildRequestUri(containerDesc.image,
                    state.selectedComputeLink);
            touchDockerImage(dockerBuildImageLink, imageRequest);
        } else if (TaskState.isFailed(imageRequest.taskInfo)) {
            AtomicInteger retryCount = new AtomicInteger(retriesCount);
            ensurePropertyExists((retryCountProperty) -> {
                if (retryCount.getAndIncrement() < retryCountProperty) {
                    // try to load base image again
                    proceedWithDockerImageCreation(containerDesc, state, retryCount.get());
                } else {
                    // Failed to build docker image
                    logWarning("Failed to build image {} on host: %s Reason: %s",
                            containerDesc.image,
                            state.selectedComputeLink, imageRequest.taskInfo.failure);
                    String errorMessage = getErrorMsg(imageRequest);
                    failTask("Failed to build image " + containerDesc.image + " on host: "
                                    + state.selectedComputeLink
                                    + " Reason: " + imageRequest.taskInfo.failure,
                            new Exception(errorMessage));
                }
            });
        } else {
            // Image is still not ready. Wait until build process completes.
            logInfo("Image is still building: %s", containerDesc.image);
            getHost()
                    .schedule(() -> proceedWithDockerImage(containerDesc, state, retriesCount), 3,
                            TimeUnit.SECONDS);
        }
    }

    private void proceedWithProvidedPolicy(ContainerDescription containerDesc,
            AdmiralAdapterTaskState state) {
        // Create allocation closure
        ContainerAllocationTaskState allocationTask = prepareContainerAllocationTask(containerDesc,
                1, state.groupResourcePlacementLink);

        HostSelectionFilter.HostSelection hostSelection = new HostSelectionFilter.HostSelection();
        hostSelection.resourceCount = 1;
        hostSelection.hostLink = state.selectedComputeLink;
        hostSelection.resourcePoolLinks = new ArrayList<>(Arrays.asList(state.placementZoneLink));
        allocationTask.hostSelections = new ArrayList<>(Collections.singletonList(hostSelection));

        // Allocate container
        try {
            logInfo("Initiating container provisioning for closure: %s", containerDesc.env[0]);
            startAllocationTask(allocationTask);

            updateClosureContainerDescription(containerDesc);

            proceedTo(AdmiralAdapterTaskState.SubStage.COMPLETED);
        } catch (Throwable ex) {
            logWarning("Unable to initiate provisioning closure: " + containerDesc.env[0], ex);
            throw new RuntimeException(ex);
        }
    }

    private void updateClosureContainerDescription(
            ContainerDescription containerDesc) {
        containerDesc.documentExpirationTimeMicros = Utils
                .fromNowMicrosUtc(TimeUnit.SECONDS.toMicros
                        (ClosureProps.CLOSURE_CONTAINER_DESCRIPTION_EXPIRATION_SECONDS));
        sendRequest(Operation
                .createPatch(getHost(), containerDesc.documentSelfLink)
                .setBody(containerDesc)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logSevere(
                                "Unable to set expiration time on closure container description %s"
                                        + containerDesc.documentSelfLink, ex);
                    }
                }));
    }

    private void startAllocationTask(ContainerAllocationTaskState allocationTask) {
        URI uri = UriUtils.buildUri(getHost(), ContainerAllocationTaskFactoryService.SELF_LINK);
        getHost().sendRequest(OperationUtil.createForcedPost(uri)
                .setBody(allocationTask)
                .setReferer(getHost().getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Exception while submitting allocation closure: ", e);
                        return;
                    }

                    logInfo("Allocation closure submitted successfully");
                }));

    }

    private ContainerAllocationTaskState prepareContainerAllocationTask(
            ContainerDescription containerDesc,
            long resourceCount, String placemenStateLink) {
        String taskLink = buildTaskLink(containerDesc);

        ContainerAllocationTaskState allocationTask = new ContainerAllocationTaskState();
        allocationTask.resourceDescriptionLink = containerDesc.documentSelfLink;
        allocationTask.groupResourcePlacementLink = placemenStateLink;
        allocationTask.resourceType = ResourceType.CONTAINER_TYPE.getName();
        allocationTask.resourceCount = resourceCount;
        allocationTask.tenantLinks = containerDesc.tenantLinks;
        allocationTask.serviceTaskCallback = createServiceCallBack(taskLink);
        allocationTask.customProperties = new HashMap<>();
        return allocationTask;
    }

    private ServiceTaskCallback createServiceCallBack(String taskLink) {
        return ServiceTaskCallback.create(taskLink);
    }

    private String buildTaskLink(ContainerDescription containerDesc) {
        String taskId = containerDesc.name.substring(0, containerDesc.name.indexOf("_"));
        return ClosureFactoryService.FACTORY_LINK + "/" + taskId;
    }

    private void seedWithDockerImageCreation(ContainerDescription containerDesc,
            String computeStateLink,
            AdmiralAdapterTaskState state) {
        URI uri = UriUtils.buildUri(getHost(), DockerImageFactoryService.FACTORY_LINK);

        DockerImage buildImage = new DockerImage();
        buildImage.name = containerDesc.image;
        buildImage.computeStateLink = computeStateLink;
        buildImage.taskInfo = TaskState.create();
        buildImage.documentSelfLink = createImageBuildRequestUri(containerDesc.image,
                computeStateLink);

        logInfo("Creating docker build image request: %s", uri);
        getHost().sendRequest(OperationUtil.createForcedPost(uri)
                .setBody(buildImage)
                .setReferer(getHost().getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Exception while submitting docker build image request: ", e);
                        failTask("Unable to submit docker build image request", e);
                        return;
                    }

                    logInfo("Docker build image request has been created successfully.");
                    // 2. Send image build request
                    buildDockerImage(containerDesc, computeStateLink, state);
                    // 3. Poll for completion
                    getHost().schedule(
                            () -> seedWithDockerImage(containerDesc, computeStateLink, state),
                            3, TimeUnit.SECONDS);
                }));

    }

    private void proceedWithDockerImageCreation(ContainerDescription containerDesc,
            AdmiralAdapterTaskState state,
            int retriesCount) {

        URI uri = UriUtils.buildUri(getHost(), DockerImageFactoryService.FACTORY_LINK);

        DockerImage buildImage = new DockerImage();
        buildImage.name = containerDesc.image;
        buildImage.computeStateLink = state.selectedComputeLink;
        buildImage.taskInfo = TaskState.create();
        buildImage.documentSelfLink = createImageBuildRequestUri(containerDesc.image,
                state.selectedComputeLink);

        logInfo("Creating docker build image request: %s, retries: %s", uri, retriesCount);
        getHost().sendRequest(OperationUtil.createForcedPost(uri)
                .setBody(buildImage)
                .setReferer(getHost().getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Exception while submitting docker build image request: ", e);
                        failTask("Unable to submit docker build image request", e);
                        return;
                    }

                    logInfo("Docker build image request has been created successfully.");
                    // 2. Send image build request
                    buildDockerImage(containerDesc, state.selectedComputeLink, state);
                    // 3. Poll for completion
                    getHost().schedule(
                            () -> proceedWithDockerImage(containerDesc, state, retriesCount), 3,
                            TimeUnit.SECONDS);
                }));

    }

    private void buildDockerImage(ContainerDescription containerDesc, String computeStateLink,
            AdmiralAdapterTaskState state) {
        logInfo("Sending docker image build request of execution container...");

        DockerImageHostRequest request = new DockerImageHostRequest();
        request.operationTypeId = ImageOperationType.BUILD.id;
        String completionServiceCallBack = createImageBuildRequestUri(containerDesc.image,
                computeStateLink);
        request.serviceTaskCallback = ServiceTaskCallback.create(completionServiceCallBack);
        request.resourceReference = UriUtils.buildUri(getHost(), computeStateLink);

        logInfo("Build image on REMOTE DOCKER HOST: %s ", request.resourceReference);

        request.customProperties = new HashMap<>();
        request.customProperties.putIfAbsent(DOCKER_BUILD_IMAGE_TAG_PROP_NAME, containerDesc.image);
        request.customProperties.putIfAbsent(DOCKER_BUILD_IMAGE_DOCKERFILE_PROP_NAME, "Dockerfile");
        request.customProperties.putIfAbsent(DOCKER_BUILD_IMAGE_FORCERM_PROP_NAME, "true");
        request.customProperties.putIfAbsent(DOCKER_BUILD_IMAGE_NOCACHE_PROP_NAME, "true");

        boolean setTaskUri = mustSetTaskUri(state);
        JsonElement buildArgsObj = prepareBuildArgs(containerDesc, setTaskUri);

        request.customProperties
                .putIfAbsent(DOCKER_BUILD_IMAGE_BUILDARGS_PROP_NAME, buildArgsObj.toString());

        request.setDockerImageData(loadDockerImageData(containerDesc.image, DriverConstants
                .DOCKER_IMAGE_DATA_FOLDER_NAME, getClass()));

        Operation op = Operation
                .createPatch(getHost(), ManagementUriParts.ADAPTER_DOCKER_IMAGE_HOST)
                .setBody(request)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere("Unable to build image on docker host: ", ex);
                        failTask("Unable to build image on docker host: " + computeStateLink, ex);
                        return;
                    }

                    logInfo("Docker build image request sent. Image: %s, host: %s",
                            containerDesc.image,
                            computeStateLink);
                });

        prepareRequest(op, true);
        getHost().sendRequest(op);
    }

    private boolean mustSetTaskUri(AdmiralAdapterTaskState state) {
        if (!ClosureUtils.isEmpty(state.configuration.dependencies)) {
            return true;
        }

        return !ClosureUtils.isEmpty(state.configuration.sourceURL);
    }

    protected void prepareRequest(Operation op, boolean longRunningRequest) {
        op.forceRemote();
        if (op.getExpirationMicrosUtc() == 0L) {
            long timeout;
            if (longRunningRequest) {
                timeout = TimeUnit.SECONDS
                        .toMicros(ClosureProps.DOCKER_IMAGE_REQUEST_TIMEOUT_SECONDS);
            } else {
                timeout = ServiceHost.ServiceHostState.DEFAULT_OPERATION_TIMEOUT_MICROS;
            }

            op.setExpiration(ServiceUtils.getExpirationTimeFromNowInMicros(timeout));
        }
    }

    private JsonElement prepareBuildArgs(ContainerDescription containerDesc, boolean setTaskUri) {
        JsonObject buildArgsObj = new JsonObject();
        for (String env : containerDesc.env) {
            int sepIndex = env.indexOf("=");
            if (sepIndex > 0) {
                String key = env.split("=")[0].trim();
                if ((setTaskUri && ClosureProps.ENV_PROP_TASK_URI.equalsIgnoreCase(key))
                        || ClosureProps.ENV_TRUST_CERTS.equalsIgnoreCase(key)) {
                    String value = env.substring(sepIndex + 1).trim();
                    buildArgsObj.addProperty(key, value);
                }
            }
        }

        return buildArgsObj;
    }

    private String createImageBuildRequestUri(String imageName, String computeStateLink) {
        String imageBuildRequestId = ClosureUtils
                .calculateHash(new String[] { imageName, "/", computeStateLink });

        return UriUtils.buildUriPath(DockerImageFactoryService.FACTORY_LINK, imageBuildRequestId);
    }

    private void touchDockerImage(String dockerBuildImageLink, DockerImage imageRequest) {
        logInfo("Updating docker build image request: {}", dockerBuildImageLink);
        getHost().sendRequest(Operation.createPatch(getHost(), dockerBuildImageLink)
                .setBody(imageRequest)
                .setReferer(getHost().getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Exception while updating docker build image request: ", e);
                        return;
                    }

                    logInfo("Docker build image request has been updated successfully: %s",
                            dockerBuildImageLink);
                }));
    }

    private void proceedWithSelection(ContainerDescription containerDesc, AdmiralAdapterTaskState
            state, Consumer<ServiceDocumentQuery.ServiceDocumentQueryElementResult<ServiceDocument>>
            completionHandler) {

        QueryTask q = QueryUtil.buildQuery(DockerImage.class, false);
        QueryTask.Query imageClause = new QueryTask.Query()
                .setTermPropertyName("name")
                .setTermMatchValue(containerDesc.image);
        q.querySpec.query.addBooleanClause(imageClause);

        QueryUtil.addExpandOption(q);

        final List<String> computeStateLinks = new ArrayList<>();
        ServiceDocumentQuery<DockerImage> query = new ServiceDocumentQuery<>(
                getHost(), DockerImage.class);
        query.query(q, (r) -> {
            if (r.hasException()) {
                completionHandler.accept(error(r.getException()));
            } else if (r.hasResult()) {
                DockerImage dockerImage = r.getResult();
                if (TaskState.isFinished(dockerImage.taskInfo)) {
                    computeStateLinks.add(dockerImage.computeStateLink);
                }
            } else {
                Set<String> selectedHosts = state.hostSelections.stream().map((item) -> item
                        .hostLink)
                        .collect(Collectors.toSet());

                String selectedComputeLink = selectComputeLink(selectedHosts, computeStateLinks);
                if (ClosureUtils.isEmpty(selectedComputeLink)) {
                    logWarning("No available hosts configured! Aborting deployment...");
                    completionHandler
                            .accept(error(new Exception("No available hosts configured!")));
                }

                logInfo("Selected compute to provision: %s ", selectedComputeLink);
                completionHandler.accept(resultLink(selectedComputeLink, 1));

                seedPeers(selectedHosts, computeStateLinks, selectedComputeLink,
                        (computeLink) -> seedWithBaseDockerImage(containerDesc, computeLink,
                                state));
            }
        });
    }

    private String selectComputeLink(Set<String> hostLinks, List<String>
            cachedComputeStateLinks) {
        if (cachedComputeStateLinks.isEmpty()) {
            // Image not found anywhere cached
            return selectFromComputeStates(hostLinks);
        }
        List<String> liveComputeStatesLinks = filterOutdatedComputes(hostLinks,
                cachedComputeStateLinks);
        if (liveComputeStatesLinks.isEmpty()) {
            // Image not found anywhere cached on live compute states
            return selectFromComputeStates(hostLinks);
        }

        // Select random from the list of live compute states with cached images
        return (String) nextValue(liveComputeStatesLinks);
    }

    private String selectFromComputeStates(Collection<String> computeStates) {
        String selectedCompute = (String) nextValue(computeStates);
        if (selectedCompute != null) {
            return selectedCompute;
        }
        return null;
    }

    private List<String> filterOutdatedComputes(Collection<String> hostLinks,
            List<String> computeStateLinks) {

        return hostLinks.stream().filter(c -> computeStateLinks.contains(c))
                .collect(Collectors.toList());

    }

    private Object nextValue(Collection<?> values) {
        List<Object> items = new ArrayList<>(values);
        if (items.size() == 0) {
            return null;
        } else if (items.size() == 1) {
            return items.get(0);
        }

        int randomIndex = (randomIntegers.nextInt(1000000) + 1) % items.size();
        return items.get(randomIndex);
    }

    private Collection<ComputeService.ComputeState> convertToComputeStates(
            Collection<Object> values) {
        List<ComputeService.ComputeState> computeStates = new LinkedList<>();
        for (Object val : values) {
            computeStates.add(Utils.fromJson(val, ComputeService.ComputeState.class));
        }

        return computeStates;
    }

    private void proceedWithContainerDescription(AdmiralAdapterTaskState state, ContainerDescription
            containerDesc) {
        if (containerDesc == null) {
            fetchContainerDescription(state,
                    (contDesc) -> this.proceedWithContainerDescription(state, contDesc));
            return;
        }

        createReservationTasks(state, containerDesc);
    }

    private void proceedWithGroupPlacement(AdmiralAdapterTaskState state) {
        fetchGroupPlacement(state, (result) -> {
                    if (result.hasException()) {
                        failTask("No available placement group!", result.getException());
                        return;
                    }
                    GroupResourcePlacementState placement = result.getResult();
                    if (placement != null) {
                        String placementZoneLink = placement.resourcePoolLink;
                        proceedTo(AdmiralAdapterTaskState.SubStage.PLACEMENT_SELECTED, (s) -> {
                            s.placementZoneLink = placementZoneLink;
                            s.groupResourcePlacementLink = placement.documentSelfLink;
                        });

                    } else {
                        failTask("No available placement available!", null);
                        return;
                    }
                }
        );
    }

    private void fetchGroupPlacement(AdmiralAdapterTaskState state,
            Consumer<ServiceDocumentQuery.ServiceDocumentQueryElementResult<GroupResourcePlacementState>> callbackFunction) {
        logInfo("Fetching group placement: %s", state.groupResourcePlacementLink);
        try {
            getHost().sendRequest(Operation.createGet(getHost(), state.groupResourcePlacementLink)
                    .setReferer(getHost().getUri())
                    .setCompletion((op, ex) -> {
                        if (ex != null) {
                            callbackFunction.accept(error(ex));
                        } else {
                            GroupResourcePlacementState placement = op.getBody
                                    (GroupResourcePlacementState.class);
                            callbackFunction.accept(result(placement, 1));
                        }
                    }));

        } catch (Throwable ex) {
            logSevere("Unable to fetch available group placement!", ex);
        }
    }

    private void fetchContainerDescription(AdmiralAdapterTaskState state,
            Consumer<ContainerDescription> callbackFunction) {
        String checksum = ClosureUtils.calculateHash(state.configuration.envVars);

        String containerDescriptionLink = CLOSURES_CONTAINER_DESC + "-" + checksum;
        URI containerDescriptionURI = UriUtils.buildUri(getHost(), containerDescriptionLink);
        logInfo("Getting container desc: %s", containerDescriptionURI);

        try {
            ServiceDocumentQuery<ContainerDescription> query = new ServiceDocumentQuery<>(getHost(),
                    ContainerDescription.class);
            query.queryDocument(
                    containerDescriptionLink,
                    (r) -> {
                        if (r.hasException()) {
                            Throwable ex = r.getException();
                            logWarning("Failure retrieving closure description: "
                                    + (ex instanceof CancellationException ?
                                    ex.getMessage() :
                                    Utils.toString(ex)));
                            failTask("Failure retrieving description state", ex);
                            return;
                        } else if (r.hasResult()) {
                            ContainerDescription contDesc = r.getResult();
                            this.cachedContainerDescription = contDesc;
                            logInfo("Already created execution container description: %s",
                                    contDesc.documentSelfLink);
                            callbackFunction.accept(contDesc);
                        } else {
                            logInfo("Unable to find execution container description: %s",
                                    containerDescriptionLink);

                            // container description not found... proceed with creation
                            proceedWithDescriptionCreation(state, checksum);
                        }
                    });

        } catch (Throwable ex) {
            String errorMsg = "Unable to allocate execution container";
            logSevere(errorMsg, ex);
            throw new RuntimeException(ex);
        }
    }

    private void proceedWithDescriptionCreation(AdmiralAdapterTaskState state,
            String configChecksum) {
        if (!ClosureUtils.isEmpty(state.configuration.sourceURL)) {
            URI sourceURI = UriUtils.buildUri(state.configuration.sourceURL);
            // Replace with HEAD as soon as it is supported
            getHost().sendRequest(Operation.createGet(sourceURI)
                    .setReferer(getHost().getUri())
                    .setCompletion((op, ex) -> {
                        if (ex != null) {
                            logWarning("Unable to fetch external source from uri: "
                                    + sourceURI, ex);
                            failTask("Unable to fetch external source from uri: "
                                    + sourceURI, ex);
                        } else {
                            String lastChanged = op.getResponseHeader("Last-Modified");
                            Long contentLenght = op.getContentLength();
                            String imageTag = ClosureUtils.prepareImageTag(state.configuration,
                                    state.imageConfig.imageNameVersion,
                                    state.configuration.sourceURL, lastChanged,
                                    contentLenght.toString());
                            createContainerDescription(state, configChecksum, imageTag);

                        }
                    }));
        } else {
            //            String imageTag = prepareImageTag(state.configuration);
            createContainerDescription(state, configChecksum, state.imageConfig.imageNameVersion);
        }
    }

    private void createContainerDescription(AdmiralAdapterTaskState state,
            String configChecksum, String imageTag) {
        // Create container description
        ContainerDescription containerDesc = prepareContainerDescription(state.imageConfig, state
                .configuration, configChecksum, imageTag);
        URI uri = UriUtils.buildUri(getHost(), ContainerDescriptionService.FACTORY_LINK);

        logInfo("Creating execution container description: %s", uri);
        getHost().sendRequest(OperationUtil.createForcedPost(uri)
                .setBody(containerDesc)
                .setReferer(getHost().getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Exception while creating container description: ", e);
                        failTask("Exception while creating container description", e);
                        return;
                    }

                    logInfo("Execution container description created successfully.");
                    proceedWithContainerDescription(state, null);
                }));
    }

    private ContainerDescription prepareContainerDescription(ImageConfiguration imageConfig,
            ContainerConfiguration configuration, String configChecksum, String imageTag) {
        ContainerDescription containerDesc = new ContainerDescription();

        containerDesc.documentSelfLink = CLOSURES_CONTAINER_DESC + "-" +
                configChecksum;
        containerDesc.name = configuration.name;
        containerDesc.image = imageConfig.imageName + ":" + imageTag;
        containerDesc.memoryLimit = ClosureUtils.toBytes(configuration.memoryMB);
        containerDesc.cpuShares = configuration.cpuShares;
        containerDesc.ulimits = new Ulimit[] { new Ulimit("nofile",
                ClosureProps.MAX_FILE_DESCRIPTORS, ClosureProps.MAX_FILE_DESCRIPTORS) };
        containerDesc.env = configuration.envVars;
        containerDesc.logConfig = prepareLogConfig(configuration);
        containerDesc.customProperties = new HashMap<>();
        containerDesc.customProperties
                .put(DOCKER_CONTAINER_CREATE_USE_LOCAL_IMAGE_WITH_PRIORITY, "true");

        return containerDesc;
    }

    private LogConfig prepareLogConfig(ContainerConfiguration configuration) {
        JsonElement jsonLogConfig = configuration.logConfiguration;
        LogConfig logConfig = new LogConfig();
        if (jsonLogConfig == null || !jsonLogConfig.isJsonObject()) {
            // set default log configuration
            logConfig.type = "json-file";
            logConfig.config = new HashMap<>();
            logConfig.config.put("max-size", ClosureProps.MAX_LOG_FILE_SIZE);
            return logConfig;
        }

        JsonObject jsonObject = (JsonObject) jsonLogConfig;
        JsonElement typeElement = jsonObject.get("type");
        if (typeElement == null || typeElement.isJsonNull()) {
            logConfig.type = "json-file";
        } else {
            logConfig.type = typeElement.getAsString();
        }

        Map<String, String> configMap = new HashMap<>();
        JsonElement configElement = jsonObject.get("config");
        if (configElement == null || configElement.isJsonNull()) {
            logConfig.config = configMap;
            return logConfig;
        }
        JsonObject jsonConfig = configElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : jsonConfig.entrySet()) {
            configMap.put(entry.getKey(), entry.getValue().getAsString());
        }

        logConfig.config = configMap;
        return logConfig;
    }

}
