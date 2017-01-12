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

package com.vmware.admiral.compute.container;

import static com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState.FIELD_NAME_ORIGINATING_HOST_LINK;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState.PowerState;
import com.vmware.admiral.compute.container.volume.VolumeUtil;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Synchronize the ContainerVolumeStates with a list of volume names
 */
public class HostVolumeListDataCollection extends StatefulService {

    public static class HostVolumeListDataCollectionFactoryService extends FactoryService {
        public static final String SELF_LINK = ManagementUriParts.HOST_VOLUME_LIST_DATA_COLLECTION;

        public static final String DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_ID = "__default-list-data-collection";
        public static final String DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK = UriUtils
                .buildUriPath(SELF_LINK, DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_ID);

        public HostVolumeListDataCollectionFactoryService() {
            super(HostVolumeListDataCollectionState.class);
            super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        }

        @Override
        public void handlePost(Operation post) {
            if (!post.hasBody()) {
                post.fail(new IllegalArgumentException("body is required"));
                return;
            }

            HostVolumeListDataCollectionState initState = post
                    .getBody(HostVolumeListDataCollectionState.class);
            if (initState.documentSelfLink == null
                    || !initState.documentSelfLink
                            .endsWith(DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_ID)) {
                post.fail(new LocalizableValidationException(
                        "Only one instance of list containers data collection can be started",
                        "compute.volumes.data-collection.single"));
                return;
            }

            post.setBody(initState).complete();
        }

        @Override
        public Service createServiceInstance() throws Throwable {
            return new HostVolumeListDataCollection();
        }

        public static ServiceDocument buildDefaultStateInstance() {
            HostVolumeListDataCollectionState state = new HostVolumeListDataCollectionState();
            state.documentSelfLink = DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK;
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskStage.STARTED;
            state.containerHostLinks = new HashSet<>();
            return state;
        }
    }

    public static class HostVolumeListDataCollectionState extends
            TaskServiceDocument<DefaultSubStage> {
        @Documentation(description = "The list of container host links.")
        @PropertyOptions(indexing = {
                PropertyIndexingOption.STORE_ONLY,
                PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE })
        public Set<String> containerHostLinks;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        // don't keep any versions for the document
        template.documentDescription.versionRetentionLimit = 1;
        return template;
    }

    public static class VolumeListCallback extends ServiceTaskCallbackResponse {
        public String containerHostLink;
        public List<String> volumeNames = new ArrayList<>();
        public boolean unlockDataCollectionForHost;

        public void addName(String name) {
            AssertUtil.assertNotNull(name, "volumeName");
            volumeNames.add(name);
        }
    }

    public HostVolumeListDataCollection() {
        super(HostVolumeListDataCollectionState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handlePatch(Operation op) {
        VolumeListCallback body = op.getBody(VolumeListCallback.class);
        if (body.containerHostLink == null) {
            logFine("'containerHostLink' is required");
            op.complete();
            return;
        }

        HostVolumeListDataCollectionState state = getState(op);
        if (body.unlockDataCollectionForHost) {
            // patch to mark that there is no active list volumes data collection for a given host.
            state.containerHostLinks.remove(body.containerHostLink);
            op.complete();
            return;
        }

        AssertUtil.assertNotNull(body.volumeNames, "volumeNames");

        if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
            logFine("Host volume list callback invoked for host [%s] with volume names: %s",
                    body.containerHostLink, body.volumeNames);
        }

        // the patch will succeed regardless of the synchronization process
        if (state.containerHostLinks.contains(body.containerHostLink)) {
            op.complete();
            return;// return since there is an active data collection for this host.
        } else {
            state.containerHostLinks.add(body.containerHostLink);
            op.complete();
            // continue with the data collection.
        }

        List<ContainerVolumeState> volumeStates = new ArrayList<ContainerVolumeState>();

        QueryTask queryTask = QueryUtil.buildQuery(ContainerVolumeState.class, true);

        // Clause to find all volumes for the given host.

        QueryTask.Query parentsClause = new QueryTask.Query()
                .setTermPropertyName(FIELD_NAME_ORIGINATING_HOST_LINK)
                .setTermMatchValue(body.containerHostLink)
                .setTermMatchType(MatchType.TERM);
        queryTask.querySpec.query.addBooleanClause(parentsClause);

        QueryUtil.addExpandOption(queryTask);
        QueryUtil.addBroadcastOption(queryTask);

        new ServiceDocumentQuery<ContainerVolumeState>(getHost(), ContainerVolumeState.class)
                .query(queryTask,
                    (r) -> {
                        if (r.hasException()) {
                            logSevere(
                                    "Failed to query for existing ContainerVolumeState instances: %s",
                                    r.getException() instanceof CancellationException
                                            ? r.getException().getMessage()
                                            : Utils.toString(r.getException()));
                            unlockCurrentDataCollectionForHost(body.containerHostLink);
                        } else if (r.hasResult()) {
                            volumeStates.add(r.getResult());
                        } else {
                            AdapterRequest request = new AdapterRequest();
                            request.operationTypeId = ContainerHostOperationType.LIST_VOLUMES.id;
                            request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
                            request.resourceReference = UriUtils.buildUri(getHost(),
                                    body.containerHostLink);
                            sendRequest(Operation
                                    .createPatch(this, ManagementUriParts.ADAPTER_DOCKER_HOST)
                                    .setBody(request)
                                    .addPragmaDirective(
                                            Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                                    .setCompletion(
                                            (o, ex) -> {
                                                if (ex == null) {
                                                    VolumeListCallback callback = o
                                                            .getBody(VolumeListCallback.class);
                                                    updateContainerVolumeStates(callback,
                                                            volumeStates,
                                                            body.containerHostLink);
                                                } else {
                                                    unlockCurrentDataCollectionForHost(
                                                            body.containerHostLink);
                                                }
                                            }));
                        }
                    });
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        HostVolumeListDataCollectionState putBody = put
                .getBody(HostVolumeListDataCollectionState.class);

        this.setState(put, putBody);
        put.setBody(putBody).complete();
    }

    private void updateContainerVolumeStates(VolumeListCallback callback,
            List<ContainerVolumeState> volumeStates, String callbackHostLink) {

        for (ContainerVolumeState volumeState : volumeStates) {

            boolean existsInCallbackHost = callback.volumeNames.contains(volumeState.name);
            callback.volumeNames.remove(volumeState.name);

            if (volumeState.parentLinks == null) {
                volumeState.parentLinks = new ArrayList<>();
            }

            if (!existsInCallbackHost) {
                handleMissingContainerVolume(volumeState);
            }
        }

        // finished removing existing ContainerVolumeState, now deal with remaining names
        List<ContainerVolumeState> volumesLeft = new ArrayList<ContainerVolumeState>();

        Operation operation = Operation
                .createGet(this, callback.containerHostLink)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logSevere("Failure to retrieve host [%s].",
                                        callback.containerHostLink, Utils.toString(ex));
                                unlockCurrentDataCollectionForHost(callback.containerHostLink);
                                return;
                            }
                            ComputeState host = o.getBody(ComputeState.class);
                            List<String> group = host.tenantLinks;

                            for (String volumeName : callback.volumeNames) {
                                ContainerVolumeState volumeState = new ContainerVolumeState();
                                volumeState.name = volumeName;
                                volumeState.external = true;

                                volumeState.tenantLinks = group;
                                volumeState.descriptionLink = String.format("%s-%s",
                                        ContainerVolumeDescriptionService.DISCOVERED_DESCRIPTION_LINK,
                                        UUID.randomUUID().toString());

                                volumeState.originatingHostLink = callback.containerHostLink;

                                volumeState.parentLinks = new ArrayList<>(
                                        Arrays.asList(callback.containerHostLink));

                                volumeState.adapterManagementReference = UriUtils
                                        .buildUri(ManagementUriParts.ADAPTER_DOCKER_VOLUME);

                                volumesLeft.add(volumeState);
                            }

                            createDiscoveredContainerVolumes(
                                    volumesLeft,
                                    (e) -> {
                                        unlockCurrentDataCollectionForHost(
                                                callback.containerHostLink);
                                    });
                        });

        sendRequest(operation);
    }

    private void createDiscoveredContainerVolumes(List<ContainerVolumeState> volumeStates,
            Consumer<Throwable> callback) {
        if (volumeStates.isEmpty()) {
            callback.accept(null);
        } else {
            AtomicInteger counter = new AtomicInteger(volumeStates.size());
            for (ContainerVolumeState volumeState : volumeStates) {
                createDiscoveredContainerVolume(callback, counter, volumeState);
            }
        }
    }

    private void createDiscoveredContainerVolume(Consumer<Throwable> callback,
            AtomicInteger counter, ContainerVolumeState volumeState) {

        logFine("Creating ContainerVolumeState for discovered volume: %s", volumeState.name);

        sendRequest(OperationUtil
                .createForcedPost(this, ContainerVolumeService.FACTORY_LINK)
                .setBody(volumeState)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logSevere(
                                        "Failed to create ContainerVolumeState for discovered volume (name=%s): %s",
                                        volumeState.name,
                                        ex.getMessage());
                                callback.accept(ex);
                                return;
                            } else {
                                logInfo("Created ContainerVolumeState for discovered volume: %s",
                                        volumeState.name);
                            }

                            ContainerVolumeState body = o.getBody(ContainerVolumeState.class);
                            createDiscoveredContainerVolumeDescription(body);

                            if (counter.decrementAndGet() == 0) {
                                callback.accept(null);
                            }
                        }));
    }

    private void createDiscoveredContainerVolumeDescription(ContainerVolumeState volumeState) {

        logFine("Creating ContainerVolumeDescription for discovered volume: %s", volumeState.name);

        ContainerVolumeDescription volumeDesc = VolumeUtil
                .createContainerVolumeDescription(volumeState);

        sendRequest(OperationUtil
                .createForcedPost(this, ContainerVolumeDescriptionService.FACTORY_LINK)
                .setBody(volumeDesc)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logSevere(
                                        "Failed to create ContainerVolumeDescription for discovered volume (name=%s): %s",
                                        volumeState.name, ex.getMessage());
                            } else {
                                logInfo("Created ContainerVolumeDescription for discovered volume: %s",
                                        volumeState.name);
                            }
                        }));

    }

    private void handleMissingContainerVolume(ContainerVolumeState volumeState) {
        // patch volume status to RETIRED
        ContainerVolumeState patchVolumeState = new ContainerVolumeState();
        patchVolumeState.powerState = PowerState.RETIRED;
        sendRequest(Operation
                .createPatch(this, volumeState.documentSelfLink)
                .setBody(patchVolumeState)
                .setCompletion((o, ex) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        logFine("Volume %s not found to be marked as missing.",
                                volumeState.documentSelfLink);
                    } else if (ex != null) {
                        logWarning("Failed to mark volume %s as missing: %s",
                                volumeState.documentSelfLink, Utils.toString(ex));
                    } else {
                        logInfo("Marked volume as missing: %s",
                                volumeState.documentSelfLink);
                    }
                }));
    }

    private void unlockCurrentDataCollectionForHost(String containerHostLink) {
        VolumeListCallback body = new VolumeListCallback();
        body.containerHostLink = containerHostLink;
        body.unlockDataCollectionForHost = true;
        sendRequest(Operation.createPatch(getUri())
                .setBody(body)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Self patch failed: %s",
                                ex instanceof CancellationException ? ex.getMessage()
                                        : Utils.toString(ex));
                    }
                }));
    }
}
