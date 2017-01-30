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

package com.vmware.admiral.compute.container;

import static com.vmware.admiral.compute.container.SystemContainerDescriptions.isSystemContainer;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.HostConfigCertificateDistributionService;
import com.vmware.admiral.compute.HostConfigCertificateDistributionService.HostConfigCertificateDistributionState;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.util.ContainerUtil;
import com.vmware.admiral.service.common.AbstractCallbackServiceHandler;
import com.vmware.admiral.service.common.AbstractCallbackServiceHandler.CallbackServiceHandlerState;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.SslTrustImportService;
import com.vmware.admiral.service.common.SslTrustImportService.SslTrustImportRequest;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost.ServiceAlreadyStartedException;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Synchronize the ContainerStates with a list of container IDs
 */
public class HostContainerListDataCollection extends StatefulService {
    private static final String SYSTEM_CONTAINER_NAME = "systemContainerName";
    protected static final long DATA_COLLECTION_LOCK_TIMEOUT_MILLISECONDS = Long.getLong(
            "com.vmware.admiral.data.collection.lock.timeout.milliseconds", 30000);

    private static final int SYSTEM_CONTAINER_SSL_RETRIES_COUNT = Integer.getInteger(
            "com.vmware.admiral.system.container.ssl.retries", 3);
    private static final long SYSTEM_CONTAINER_SSL_RETRIES_WAIT = Long.getLong(
            "com.vmware.admiral.system.container.ssl.retries.wait.millis", 1000);

    public static class HostContainerListDataCollectionFactoryService extends FactoryService {
        public static final String SELF_LINK = ManagementUriParts.HOST_CONTAINER_LIST_DATA_COLLECTION;

        public static final String DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_ID = "__default-list-data-collection";
        public static final String DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK = UriUtils
                .buildUriPath(SELF_LINK, DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_ID);

        public HostContainerListDataCollectionFactoryService() {
            super(HostContainerListDataCollectionState.class);
        }

        @Override
        public void handlePost(Operation post) {
            if (!post.hasBody()) {
                post.fail(new IllegalArgumentException("body is required"));
                return;
            }

            HostContainerListDataCollectionState initState = post
                    .getBody(HostContainerListDataCollectionState.class);
            if (initState.documentSelfLink == null
                    || !initState.documentSelfLink
                            .endsWith(DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_ID)) {
                post.fail(new LocalizableValidationException(
                        "Only one instance of containers data collection can be started",
                        "compute.container.data-collection.single"));
                return;
            }

            post.setBody(initState).complete();
        }

        @Override
        public Service createServiceInstance() throws Throwable {
            return new HostContainerListDataCollection();
        }

        public static ServiceDocument buildDefaultStateInstance() {
            HostContainerListDataCollectionState state = new HostContainerListDataCollectionState();
            state.documentSelfLink = DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK;
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskStage.STARTED;
            state.containerHostLinks = new HashMap<>();
            return state;
        }
    }

    public static class HostContainerListDataCollectionState extends
            TaskServiceDocument<DefaultSubStage> {
        @Documentation(description = "The map of container host links.")
        @PropertyOptions(indexing = {
                PropertyIndexingOption.STORE_ONLY,
                PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE })
        public Map<String, Long> containerHostLinks;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        // don't keep any versions for the document
        template.documentDescription.versionRetentionLimit = 1;
        return template;
    }

    public static class ContainerListCallback extends ServiceTaskCallbackResponse {
        private static final String NAME_SEPARATOR = ",";
        public String containerHostLink;
        public URI hostAdapterReference;
        public Map<String, String> containerIdsAndNames = new HashMap<>();
        public Map<String, String> containerIdsAndImage = new HashMap<>();
        public boolean unlockDataCollectionForHost;

        public void addIdAndNames(String id, String[] names) {
            AssertUtil.assertNotNull(id, "containerId");

            String namesValue = null;
            if (names != null && names.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (String name : names) {
                    sb.append(name.startsWith("/") ? name.substring(1) : name);
                    sb.append(NAME_SEPARATOR);
                }
                sb.deleteCharAt(sb.length() - 1);
                namesValue = sb.toString();
            }

            containerIdsAndNames.put(id, namesValue);
        }
    }

    public static class SystemContainerOperationCallbackHandler
            extends AbstractCallbackServiceHandler {

        private final BiConsumer<CallbackServiceHandlerState, Boolean> consumer;

        public SystemContainerOperationCallbackHandler(
                BiConsumer<CallbackServiceHandlerState, Boolean> consumer) {

            this.consumer = consumer;
        }

        @Override
        protected void handleFailedStagePatch(CallbackServiceHandlerState state) {
            ServiceErrorResponse err = state.taskInfo.failure;
            logWarning("Failed deleting system container");
            if (err != null && err.stackTrace != null) {
                logFine("Task failure stack trace: %s", err.stackTrace);
                consumer.accept(state, true);

                if (completionCallback != null) {
                    completionCallback.run();
                }
            }
        }

        @Override
        protected void handleFinishedStagePatch(CallbackServiceHandlerState state) {
            consumer.accept(state, false);

            if (completionCallback != null) {
                completionCallback.run();
            }
        }
    }

    public static class ContainerVersion implements Comparable<ContainerVersion> {
        public static final String LATEST_VERSION = "latest";
        private final String version;

        private ContainerVersion(String version) {
            AssertUtil.assertNotNull(version, "version");
            this.version = version;
        }

        @Override
        public int compareTo(ContainerVersion o) {
            try {
                if (version.equals(LATEST_VERSION) && o.version.equals(LATEST_VERSION)) {
                    return 0;
                } else if (version.equals(LATEST_VERSION)) {
                    return -1;
                } else if (o.version.equals(LATEST_VERSION)) {
                    return 1;
                } else {
                    String[] v = version.split("\\.");
                    String[] vo = o.version.split("\\.");
                    for (int i = 0; i < v.length; i++) {
                        int vInt = Integer.parseInt(v[i]);
                        int voInt = Integer.parseInt(vo[i]);
                        if (vInt < voInt) {
                            return -1;
                        } else if (vInt > voInt) {
                            return 1;
                        }
                    }
                    return 0;
                }
            } catch (RuntimeException e) {
                Utils.logWarning("Unable to compare container versions [%s-%s]", version,
                        o.version);
                return 0;
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((version == null) ? 0 : version.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ContainerVersion other = (ContainerVersion) obj;
            if (version == null) {
                if (other.version != null) {
                    return false;
                }
            } else if (!version.equals(other.version)) {
                return false;
            }
            return true;
        }

        public static ContainerVersion fromImageName(String image) {
            AssertUtil.assertNotNull(image, "image");
            String version = null;
            int idx = image.indexOf(':');
            if (idx != -1) {
                version = image.substring(idx + 1);
            } else {
                version = LATEST_VERSION;
            }
            return new ContainerVersion(version);
        }
    }

    public HostContainerListDataCollection() {
        super(HostContainerListDataCollectionState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handlePatch(Operation op) {
        ContainerListCallback body = op.getBody(ContainerListCallback.class);

        if (body.hostAdapterReference == null) {
            body.hostAdapterReference =
                    ContainerHostDataCollectionService.getDefaultHostAdapter(getHost());
        }

        String containerHostLink = body.containerHostLink;
        if (containerHostLink == null) {
            logWarning("'containerHostLink' is required");
            op.complete();
            return;
        }

        HostContainerListDataCollectionState state = getState(op);
        if (body.unlockDataCollectionForHost) {
            // patch to mark that there is no active list containers data collection for a given
            // host.
            state.containerHostLinks.remove(containerHostLink);
            op.complete();
            return;
        }

        AssertUtil.assertNotNull(body.containerIdsAndNames, "containerIdsAndNames");

        if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
            logFine("Host container list callback invoked for host [%s] with container IDs: %s",
                    containerHostLink, body.containerIdsAndNames.keySet().stream()
                            .collect(Collectors.toList()));
        }

        // the patch will succeed regardless of the synchronization process
        if (state.containerHostLinks.get(containerHostLink) != null &&
                Instant.now().isBefore(Instant.ofEpochMilli(
                        (state.containerHostLinks.get(containerHostLink))))) {
            if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
                logFine("Host container list callback for host [%s] with container IDs: %s " +
                        "skipped, another instance is active",
                        containerHostLink,
                        body.containerIdsAndNames.keySet().stream().collect(Collectors.toList()));
            }
            op.complete();
            return; // return since there is an active data collection for this host.
        } else {
            state.containerHostLinks.put(containerHostLink,
                    Instant.now().toEpochMilli() + DATA_COLLECTION_LOCK_TIMEOUT_MILLISECONDS);
            op.complete();
            // continue with the data collection.
        }

        List<ContainerState> containerStates = new ArrayList<ContainerState>();
        QueryTask queryTask = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_PARENT_LINK, containerHostLink);
        QueryUtil.addExpandOption(queryTask);

        QueryUtil.addBroadcastOption(queryTask);
        new ServiceDocumentQuery<ContainerState>(getHost(), ContainerState.class)
                .query(queryTask,
                        (r) -> {
                            if (r.hasException()) {
                                logSevere(
                                        "Failed to query for existing ContainerState instances: %s",
                                        r.getException() instanceof CancellationException
                                                ? r.getException().getMessage()
                                                : Utils.toString(r.getException()));
                                unlockCurrentDataCollectionForHost(containerHostLink);
                            } else if (r.hasResult()) {
                                containerStates.add(r.getResult());
                            } else {
                                AdapterRequest request = new AdapterRequest();
                                request.operationTypeId = ContainerHostOperationType.LIST_CONTAINERS.id;
                                request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
                                request.resourceReference = UriUtils.buildUri(getHost(),
                                        containerHostLink);
                                sendRequest(Operation
                                        .createPatch(body.hostAdapterReference)
                                        .setBody(request)
                                        .addPragmaDirective(
                                                Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                                        .setCompletion(
                                                (o, ex) -> {
                                                    if (ex == null) {
                                                        ContainerListCallback callback = o
                                                                .getBody(
                                                                        ContainerListCallback.class);
                                                        if (callback.hostAdapterReference == null) {
                                                            callback.hostAdapterReference =
                                                                    ContainerHostDataCollectionService.getDefaultHostAdapter(getHost());
                                                        }
                                                        updateContainerStates(callback,
                                                                containerStates, containerHostLink);
                                                    } else {
                                                        unlockCurrentDataCollectionForHost(
                                                                containerHostLink);
                                                    }
                                                }));
                            }
                        });
    }

    private void updateContainerStates(ContainerListCallback callback,
            List<ContainerState> containerStates, String containerHostLink) {
        final List<String> systemContainersToInstall = SystemContainerDescriptions
                .getSystemContainerNames();
        for (ContainerState containerState : containerStates) {

            boolean exists = false;
            if (containerState.id != null) {
                exists = callback.containerIdsAndNames
                        .containsKey(containerState.id);
                callback.containerIdsAndNames.remove(containerState.id);
            } else if (containerState.powerState.equals(PowerState.PROVISIONING)
                    || containerState.powerState.equals(PowerState.RETIRED)
                    || containerState.powerState.equals(PowerState.ERROR)) {
                String names = containerNamesToString(containerState.names);
                exists = callback.containerIdsAndNames.containsValue(names);
                callback.containerIdsAndNames.values().remove(names);
            }

            // if containerId doesn't exists, mark the ContainerState as missing
            // provisioning, allocating containers in error might not have
            // id associated yet.
            if (!exists) {
                boolean active = containerState.powerState == PowerState.RUNNING
                        || containerState.powerState == PowerState.STOPPED
                        || containerState.powerState == PowerState.PAUSED;
                if (active) {
                    handleMissingContainer(containerState);
                }
            } else {
                callback.containerIdsAndNames.remove(containerState.id);
                String systemContainerName = matchSystemContainerName(
                        systemContainersToInstall, containerState.names);
                if (systemContainerName != null) {
                    systemContainersToInstall.remove(systemContainerName);
                    if (containerState.powerState == PowerState.STOPPED) {
                        logWarning("System container found but is OFF. Starting.");
                        startSystemContainer(containerState, null);
                    }
                }
            }
        }

        // finished removing existing ContainerState, now deal with remaining IDs
        List<ContainerState> containersLeft = new ArrayList<ContainerState>();
        Set<ContainerState> systemContainersToStart = new HashSet<>();

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

                            for (Entry<String, String> entry : callback.containerIdsAndNames
                                    .entrySet()) {
                                ContainerState containerState = new ContainerState();
                                containerState.id = entry.getKey();
                                containerState.names = entry.getValue() == null ? null
                                        : new ArrayList<>(Arrays.asList(entry.getValue()
                                                .split(ContainerListCallback.NAME_SEPARATOR)));

                                String systemContainerName = matchSystemContainerName(
                                        systemContainersToInstall,
                                        containerState.names);

                                if (systemContainerName != null) {
                                    systemContainersToStart.add(containerState);
                                    systemContainersToInstall.remove(systemContainerName);
                                    /* need to build the full uri */
                                    containerState.documentSelfLink = SystemContainerDescriptions
                                            .getSystemContainerSelfLink(systemContainerName,
                                                    Service.getId(containerHostLink));
                                    containerState.system = Boolean.TRUE;
                                    containerState.descriptionLink = SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK;
                                    containerState.volumes = SystemContainerDescriptions.AGENT_CONTAINER_VOLUMES;
                                    if (callback.containerIdsAndImage != null) {
                                        containerState.image = callback.containerIdsAndImage
                                                .get(containerState.id);
                                    }
                                } else {

                                    containerState.tenantLinks = group;
                                    containerState.descriptionLink = String.format("%s-%s",
                                            SystemContainerDescriptions.DISCOVERED_DESCRIPTION_LINK,
                                            UUID.randomUUID().toString());
                                    containerState.image = callback.containerIdsAndImage
                                            .get(containerState.id);
                                }
                                containerState.parentLink = callback.containerHostLink;
                                containerState.adapterManagementReference =
                                        getContainerAdapterReference(callback.hostAdapterReference);

                                containersLeft.add(containerState);
                            }

                            for (String systemContainerName : systemContainersToInstall) {
                                installSystemContainerToHost(containerHostLink,
                                        systemContainerName, null);
                            }

                            createDiscoveredContainers(
                                    containersLeft,
                                    (e) -> {
                                        if (e == null) {
                                            updateNumberOfContainers(callback.containerHostLink);
                                        }

                                        for (ContainerState containerState : systemContainersToStart) {
                                            handleDiscoveredSystemContainer(containerState,
                                                    containerHostLink, null);
                                        }

                                        unlockCurrentDataCollectionForHost(
                                                callback.containerHostLink);
                                    });
                        });

        sendRequest(operation);
    }

    private URI getContainerAdapterReference(URI hostAdapter) {
        switch (hostAdapter.getPath()) {
        case ManagementUriParts.ADAPTER_DOCKER_HOST:
            return UriUtils.buildUri(ManagementUriParts.ADAPTER_DOCKER);
        case ManagementUriParts.ADAPTER_KUBERNETES_HOST:
            return UriUtils.buildUri(ManagementUriParts.ADAPTER_KUBERNETES);
        default:
            throw new IllegalArgumentException(
                    String.format("No container adapter for %s", hostAdapter.getPath()));
        }
    }

    private String containerNamesToString(List<String> names) {
        if (names != null && names.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (String name : names) {
                sb.append(name.startsWith("/") ? name.substring(1) : name);
                sb.append(ContainerListCallback.NAME_SEPARATOR);
            }
            sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        } else {
            return null;
        }
    }

    private void startSystemContainer(ContainerState containerState,
            ServiceTaskCallback serviceTaskCallback) {
        AdapterRequest adapterRequest = new AdapterRequest();
        adapterRequest.resourceReference = UriUtils
                .buildUri(getHost(), containerState.documentSelfLink);
        adapterRequest.operationTypeId = ContainerOperationType.START.id;

        if (serviceTaskCallback == null) {
            String systemContainerName = matchSystemContainerName(
                    SystemContainerDescriptions.getSystemContainerNames(), containerState.names);

            startAndCreateCallbackHandlerService(systemContainerName,
                    createSystemContainerReadyHandler(containerState),
                    (callback) -> startSystemContainer(containerState, callback));
            return;
        }

        adapterRequest.serviceTaskCallback = serviceTaskCallback;

        sendRequest(Operation
                .createPatch(getHost(), containerState.adapterManagementReference.toString())
                .setBody(adapterRequest)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failure starting system container: " + Utils.toString(e));
                        return;
                    }
                    logInfo("Starting system container: %s with name: %s ... ",
                            containerState.documentSelfLink, containerState.names);
                }));
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        HostContainerListDataCollectionState putBody = put
                .getBody(HostContainerListDataCollectionState.class);

        this.setState(put, putBody);
        put.setBody(putBody).complete();
    }

    private void handleDiscoveredSystemContainer(ContainerState containerState,
            String containerHostLink,
            ContainerDescription containerDesc) {
        if (containerDesc == null) {
            OperationUtil.getDocumentState(this, containerState.descriptionLink,
                    ContainerDescription.class,
                    (ContainerDescription contDesc) -> handleDiscoveredSystemContainer(
                            containerState, containerHostLink, contDesc));
            return;
        }

        ContainerVersion containerVersion = ContainerVersion.fromImageName(containerState.image);
        ContainerVersion containerDescVersion = ContainerVersion.fromImageName(containerDesc.image);

        if (containerVersion.compareTo(containerDescVersion) < 0) {
            // if container version is old, delete the container and create it again
            recreateSystemContainer(containerState, containerHostLink);
        } else {
            // check if system ContainerState exists. If not, start won't work as docker-adapter
            // will refer to missing ContainerState resulting in failure in starting operation.
            checkIfSystemContainerStateExistsBeforeStartIt(containerState, containerDesc,
                    containerHostLink);
        }
    }

    private void checkIfSystemContainerStateExistsBeforeStartIt(ContainerState containerState,
            ContainerDescription containerDesc, String containerHostLink) {

        QueryTask containerQuery = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_DESCRIPTION_LINK,
                SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK,
                ContainerState.FIELD_NAME_PARENT_LINK, containerState.parentLink);

        QueryUtil.addExpandOption(containerQuery);
        AtomicBoolean stateExists = new AtomicBoolean(false);
        new ServiceDocumentQuery<ContainerState>(getHost(), ContainerState.class)
                .query(containerQuery,
                        (r) -> {
                            if (r.hasException()) {
                                logWarning("Failed to retrieve system container state: %s",
                                        containerState.documentSelfLink);
                            } else if (r.hasResult()) {
                                // If System ContainerState exists, all supported container
                                // operations start/stop will work.
                                stateExists.set(true);
                            } else {
                                if (stateExists.get()) {
                                    // If System ContainerState exists we can start it.
                                    // if version is valid, although we don't know the power state,
                                    // start the containers anyway as start is idempotent
                                    logFine("start existing system container %s",
                                            containerState.documentSelfLink);
                                    startSystemContainer(containerState, null);
                                } else {
                                    // If System ContainerState does not exists, we create it before
                                    // start operation.
                                    final ContainerState systemContainerState = createSystemContainerState(
                                            containerState, containerDesc, containerHostLink);

                                    sendRequest(OperationUtil
                                            .createForcedPost(this,
                                                    ContainerFactoryService.SELF_LINK)
                                            .setBody(systemContainerState)
                                            .setCompletion(
                                                    (o, e) -> {
                                                        if (e != null) {
                                                            logWarning(
                                                                    "Failure creating system container: "
                                                                            + Utils.toString(e));
                                                            return;
                                                        }
                                                        ContainerState body = o
                                                                .getBody(ContainerState.class);
                                                        logInfo("Created system ContainerState: %s ",
                                                                body.documentSelfLink);
                                                        createSystemContainerInstanceRequest(body,
                                                                null);
                                                        updateNumberOfContainers(containerHostLink);
                                                        startSystemContainer(containerState, null);
                                                    }));

                                }
                            }
                        });
    }

    private ContainerState createSystemContainerState(ContainerState containerState,
            ContainerDescription containerDesc, String containerHostLink) {

        String systemContainerName = matchSystemContainerName(
                SystemContainerDescriptions.getSystemContainerNames(), containerState.names);

        final ContainerState systemContainerState = new ContainerState();
        systemContainerState.documentSelfLink = containerState.documentSelfLink;
        systemContainerState.names = new ArrayList<>();
        systemContainerState.names.add(systemContainerName);
        systemContainerState.descriptionLink = containerDesc.documentSelfLink;
        systemContainerState.parentLink = containerHostLink;
        systemContainerState.powerState = ContainerState.PowerState.PROVISIONING;
        systemContainerState.adapterManagementReference = containerDesc.instanceAdapterReference;
        systemContainerState.image = containerDesc.image;
        systemContainerState.command = containerDesc.command;
        systemContainerState.groupResourcePlacementLink = GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK;
        systemContainerState.system = Boolean.TRUE;
        systemContainerState.volumes = containerDesc.volumes;
        systemContainerState.id = containerState.id;

        return systemContainerState;
    }

    private void recreateSystemContainer(ContainerState containerState, String containerHostLink) {
        logFine("recreate system container %s", containerState.documentSelfLink);
        deleteSystemContainer(containerState,
                (o, error) -> {
                    if (error) {
                        logSevere("Failure deleting system container.");
                    } else {
                        if (o.customProperties != null) {
                            String systemContainerName = o.customProperties
                                    .get(SYSTEM_CONTAINER_NAME);
                            installSystemContainerToHost(containerHostLink, systemContainerName,
                                    null);
                        }
                    }
                }, null);
    }

    private BiConsumer<CallbackServiceHandlerState, Boolean> createSystemContainerReadyHandler(
            ContainerState container) {
        return (o, error) -> {
            if (error) {
                logSevere("Failure creating system container.");
            } else {
                // Upload trusted self-signed registry certificates to host
                logFine("Distribute certificates for host %s", container.parentLink);
                HostConfigCertificateDistributionState distState = new HostConfigCertificateDistributionState();
                distState.hostLink = container.parentLink;
                distState.tenantLinks = container.tenantLinks;
                sendRequest(Operation.createPost(this,
                        HostConfigCertificateDistributionService.SELF_LINK)
                        .setBody(distState));

                // Import agent SSL certificate
                importAgentSslCertificate(container, null, SYSTEM_CONTAINER_SSL_RETRIES_COUNT);
            }
        };
    }

    private void importAgentSslCertificate(ContainerState container, ComputeState host,
            int retryCount) {

        if (container.ports == null) {
            OperationUtil.getDocumentState(this, container.documentSelfLink, ContainerState.class,
                    (ContainerState c) -> {
                        if (c.ports == null) {
                            logSevere("Couldn't get valid ports for system container %s",
                                    container.documentSelfLink);
                        } else {
                            importAgentSslCertificate(c, host, retryCount);
                        }
                    });
            return;
        }

        if (host == null) {
            OperationUtil.getDocumentState(this, container.parentLink, ComputeState.class,
                    (ComputeState h) -> {
                        importAgentSslCertificate(container, h, retryCount);
                    });
            return;
        }

        logFine("Import SSL certificate for system container %s", container.documentSelfLink);

        SslTrustImportRequest request = new SslTrustImportRequest();
        request.acceptCertificate = true;

        try {
            request.hostUri = ContainerUtil.getShellUri(host, container);
        } catch (Exception e) {
            logSevere("Exception getting shell URI for system container %s:\n%s",
                    container.documentSelfLink, Utils.toString(e));
            return;
        }

        sendRequest(Operation.createPut(this, SslTrustImportService.SELF_LINK)
                .setBody(request)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (retryCount > 0) {
                            logWarning(
                                    "Retrying with count %s after error importing system container SSL certificate from '%s':\n%s",
                                    retryCount, request.hostUri, Utils.toString(e));
                            try {
                                Thread.sleep(SYSTEM_CONTAINER_SSL_RETRIES_WAIT);
                                importAgentSslCertificate(container, host, retryCount - 1);
                            } catch (Exception ex) {
                                logWarning("Sleep interrupted!\n%s", Utils.toString(ex));
                            }
                        } else {
                            logSevere(
                                    "Exception importing system container SSL certificate from '%s':\n%s",
                                    request.hostUri, Utils.toString(e));
                        }
                        return;
                    }
                    logInfo("System container SSL certificate imported from '%s'", request.hostUri);
                }));
    }

    private void deleteSystemContainer(
            ContainerState containerState,
            BiConsumer<AbstractCallbackServiceHandler.CallbackServiceHandlerState, Boolean> consumer,
            ServiceTaskCallback serviceTaskCallback) {

        if (serviceTaskCallback == null) {
            String systemContainerName = matchSystemContainerName(
                    SystemContainerDescriptions.getSystemContainerNames(), containerState.names);

            startAndCreateCallbackHandlerService(systemContainerName, consumer,
                    (callback) -> deleteSystemContainer(containerState, consumer, callback));
            return;
        }

        AdapterRequest adapterRequest = new AdapterRequest();
        adapterRequest.resourceReference = UriUtils
                .buildUri(getHost(), containerState.documentSelfLink);
        adapterRequest.operationTypeId = ContainerOperationType.DELETE.id;
        adapterRequest.serviceTaskCallback = serviceTaskCallback;

        String host = containerState.adapterManagementReference.getHost();
        String targetPath = null;

        if (StringUtils.isBlank(host)) {
            // There isn't old version of system container.
            targetPath = containerState.adapterManagementReference.toString();
        } else {
            // Old versions of system container contains host address in adapter reference.
            targetPath = containerState.adapterManagementReference.getPath();
        }

        sendRequest(Operation.createPatch(getHost(), targetPath)
                .setBody(adapterRequest)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning("Failure deleting system container. Error %s",
                                        Utils.toString(e));
                                return;
                            }
                        }));
    }

    private void startAndCreateCallbackHandlerService(
            String systemContainerName,
            BiConsumer<AbstractCallbackServiceHandler.CallbackServiceHandlerState, Boolean> actualCallback,
            Consumer<ServiceTaskCallback> caller) {
        if (actualCallback == null) {
            caller.accept(ServiceTaskCallback.createEmpty());
            return;
        }
        String callbackLink = ManagementUriParts.REQUEST_CALLBACK_HANDLER_TASKS
                + UUID.randomUUID().toString();
        AbstractCallbackServiceHandler.CallbackServiceHandlerState body = new AbstractCallbackServiceHandler.CallbackServiceHandlerState();
        body.documentSelfLink = callbackLink;
        body.customProperties = new HashMap<>();
        body.customProperties.put(SYSTEM_CONTAINER_NAME, systemContainerName);
        URI callbackUri = UriUtils.buildUri(getHost(), callbackLink);

        Operation startPost = Operation.createPost(callbackUri)
                .setBody(body)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failure creating callback handler. Error %s",
                                Utils.toString(e));
                        return;
                    }
                    caller.accept(ServiceTaskCallback.create(callbackUri.toString()));
                });

        SystemContainerOperationCallbackHandler service = new SystemContainerOperationCallbackHandler(
                actualCallback);
        service.setCompletionCallback(() -> getHost().stopService(service));
        getHost().startService(startPost, service);
    }

    private void unlockCurrentDataCollectionForHost(String containerHostLink) {
        ContainerListCallback body = new ContainerListCallback();
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

    private void updateNumberOfContainers(String containerHostLink) {
        // There are two operations: get all the containers and get the system containers
        AtomicInteger counter = new AtomicInteger(2);
        ComputeState state = new ComputeState();
        state.customProperties = new HashMap<String, String>();
        QueryTask containerQuery = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_PARENT_LINK, containerHostLink);
        QueryUtil.addCountOption(containerQuery);

        new ServiceDocumentQuery<ContainerState>(getHost(), ContainerState.class)
                .query(containerQuery,
                        (r) -> {
                            if (r.hasException()) {
                                logWarning("Failed to retrieve containers for host:",
                                        containerHostLink);
                            } else {
                                logFine("Found %s containers for container host: %s",
                                        String.valueOf(r.getCount()), containerHostLink);
                                state.customProperties
                                        .put(
                                                ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME,
                                                String.valueOf(r.getCount()));
                                if (counter.decrementAndGet() == 0) {
                                    patchHostState(containerHostLink, state);
                                }
                            }
                        });
        QueryTask systemContainerQuery = QueryUtil.buildPropertyQuery(
                ContainerState.class, ContainerState.FIELD_NAME_PARENT_LINK,
                containerHostLink);
        QueryUtil.addListValueClause(systemContainerQuery,
                ContainerState.FIELD_NAME_SYSTEM,
                Arrays.asList(Boolean.TRUE.toString()));
        QueryUtil.addCountOption(systemContainerQuery);
        new ServiceDocumentQuery<ContainerState>(getHost(), ContainerState.class)
                .query(systemContainerQuery, (result) -> {
                    if (result.hasException()) {
                        logWarning("Failed to retrieve system containers for host:",
                                containerHostLink);
                    } else {
                        state.customProperties.put(
                                ContainerHostService.NUMBER_OF_SYSTEM_CONTAINERS_PROP_NAME,
                                String.valueOf(result.getCount()));
                        if (counter.decrementAndGet() == 0) {
                            patchHostState(containerHostLink, state);
                        }
                    }
                });
    }

    private void patchHostState(String containerHostLink, ComputeState state) {
        sendRequest(Operation
                .createPatch(this, containerHostLink)
                .setBody(state)
                .setCompletion(
                        (operation, e) -> {
                            if (e != null) {
                                logSevere(
                                        "Failure updating host [%s] with container count.",
                                        containerHostLink,
                                        Utils.toString(e));
                                return;
                            }
                            logInfo("Host [%s] updated with container count.",
                                    containerHostLink);
                        }));
    }

    private void createDiscoveredContainers(List<ContainerState> containerStates,
            Consumer<Throwable> callback) {
        if (containerStates.isEmpty()) {
            callback.accept(null);
        } else {
            AtomicInteger counter = new AtomicInteger(containerStates.size());
            for (ContainerState containerState : containerStates) {
                if (containerState.names == null || containerState.names.isEmpty()) {
                    logInfo("Names not set for container: %s", containerState.documentSelfLink);
                    if (counter.decrementAndGet() == 0) {
                        callback.accept(null);
                    }
                    continue;
                }
                // check again if the container state already exists by names. This is needed in
                // cluster mode not to create container states that we already have
                Operation operation = Operation
                        .createGet(this, UriUtils.buildUriPath(
                                ContainerFactoryService.SELF_LINK, containerState.names.get(0)))
                        .setCompletion(
                                (o, ex) -> {
                                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                                        createDiscoveredContainer(callback, counter,
                                                containerState);
                                    } else if (ex != null) {
                                        logSevere("Failed to get container %s : %s",
                                                containerState.names, ex.getMessage());
                                        callback.accept(ex);
                                    } else {
                                        if (counter.decrementAndGet() == 0) {
                                            callback.accept(null);
                                        }
                                    }

                                });
                sendRequest(operation);
            }

        }
    }

    private void createDiscoveredContainer(Consumer<Throwable> callback, AtomicInteger counter,
            ContainerState containerState) {
        logFine("Creating ContainerState for discovered container: %s",
                containerState.id);
        URI containerFactoryUri = UriUtils.buildUri(getHost(),
                ContainerFactoryService.class);
        sendRequest(OperationUtil
                .createForcedPost(containerFactoryUri)
                .setBody(containerState)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                if (ex instanceof ServiceAlreadyStartedException) {
                                    logWarning(
                                            "Container state already exists for container (id=%s)",
                                            containerState.id);
                                } else {
                                    logSevere(
                                            "Failed to create ContainerState for discovered container (id=%s): %s",
                                            containerState.id,
                                            ex.getMessage());
                                    callback.accept(ex);
                                    return;
                                }
                            } else {
                                logInfo("Created ContainerState for discovered container: %s",
                                        containerState.id);

                                // as soon as the ContainerService
                                // is started, its
                                // maintenance
                                // handler will be invoked to fetch
                                // up-to-date attributes
                            }

                            // Shouldn't create ContainerDescription
                            // for system containers.
                            String systemContainerName = matchSystemContainerName(
                                    SystemContainerDescriptions
                                            .getSystemContainerNames(),
                                    containerState.names);

                            if (systemContainerName == null) {
                                ContainerState body = o.getBody(
                                        ContainerState.class);
                                createDiscoveredContainerDescription(
                                        body);
                            }

                            if (counter.decrementAndGet() == 0) {
                                callback.accept(null);
                            }
                        }));
    }

    private void createDiscoveredContainerDescription(ContainerState containerState) {

        logFine("Creating ContainerDescription for discovered container: %s", containerState.id);

        ContainerDescription containerDesc = ContainerUtil
                .createContainerDescription(containerState);

        sendRequest(OperationUtil
                .createForcedPost(this, ContainerDescriptionService.FACTORY_LINK)
                .setBody(containerDesc)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logSevere(
                                        "Failed to create ContainerDescription for discovered container (id=%s): %s",
                                        containerState.id, ex.getMessage());
                            } else {
                                logInfo("Created ContainerDescription for discovered container: %s",
                                        containerState.id);
                            }
                        }));

    }

    private void handleMissingContainer(ContainerState containerState) {

        // do not set RETIRED state to the system container.
        if (isSystemContainer(containerState)) {
            return;
        }

        if (containerState.isDeleted) {
            // delete contaniner state
            sendRequest(Operation
                    .createDelete(this, containerState.documentSelfLink)
                    .setBody(new ServiceDocument())
                    .setCompletion(
                            (op, ex) -> {
                                if (ex != null) {
                                    logWarning(
                                            "Failed deleting ContainerState of missing container: "
                                                    + containerState.documentSelfLink,
                                            ex);
                                    return;
                                }
                                logInfo("Deleted ContainerState of missing container: "
                                        + containerState.documentSelfLink);
                            }));
        } else {
            // patch container status to RETIRED
            ContainerState patchContainerState = new ContainerState();
            patchContainerState.powerState = PowerState.RETIRED;
            sendRequest(Operation
                    .createPatch(this, containerState.documentSelfLink)
                    .setBody(patchContainerState)
                    .setCompletion((o, ex) -> {
                        if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                            logFine("Container %s not found to be marked as missing.",
                                    containerState.documentSelfLink);
                        } else if (ex != null) {
                            logWarning("Failed to mark container %s as missing: %s",
                                    containerState.documentSelfLink, Utils.toString(ex));
                        } else {
                            logInfo("Marked container as missing: %s",
                                    containerState.documentSelfLink);
                        }
                    }));
        }
    }

    private void installSystemContainerToHost(String containerHostLink,
            String systemContainerName, ContainerDescription containerDesc) {
        if (DeploymentProfileConfig.getInstance().isTest()) {
            logWarning("No system containers will be installed in test mode...");
            return;
        }

        if (containerDesc == null) {
            String descriptionLink;
            if (systemContainerName.equals(SystemContainerDescriptions.AGENT_CONTAINER_NAME)) {
                descriptionLink = SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK;
            } else {
                throw new LocalizableValidationException("Unknown systemContainerName: "
                        + systemContainerName, "compute.system-container.name.unknown", new String[] { systemContainerName });
            }

            OperationUtil.getDocumentState(this, descriptionLink, ContainerDescription.class,
                    (ContainerDescription contDesc) -> installSystemContainerToHost(
                            containerHostLink, systemContainerName, contDesc));
            return;
        }

        OperationUtil.getDocumentState(this, containerHostLink, ComputeState.class,
                (ComputeState host) -> {
                    if (ContainerHostUtil.isVicHost(host)) {
                        logInfo("VIC host detected, system containers will not be installed.");
                        return;
                    }
                    if (ContainerHostUtil.isKubernetesHost(host)) {
                        logInfo("Kubernetes host detected, system containers will not be installed.");
                        return;
                    }
                    createOrRetrieveSystemContainer(containerHostLink, systemContainerName,
                            containerDesc);
                });
    }

    private void createOrRetrieveSystemContainer(String containerHostLink,
            String systemContainerName, ContainerDescription containerDesc) {
        String containerStateLink = SystemContainerDescriptions.getSystemContainerSelfLink(
                systemContainerName, Service.getId(containerHostLink));
        ServiceDocumentQuery<ContainerState> query = new ServiceDocumentQuery<>(getHost(),
                ContainerState.class);

        query.queryDocument(
                containerStateLink,
                (r) -> {
                    if (r.hasException()) {
                        logWarning("Failure retrieving system container: "
                                + (r.getException() instanceof CancellationException ? r
                                        .getException().getMessage() : Utils.toString(r
                                                .getException())));
                        return;
                    }
                    final ContainerState containerState = new ContainerState();
                    containerState.documentSelfLink = containerStateLink;
                    containerState.names = new ArrayList<>();
                    containerState.names.add(systemContainerName);
                    containerState.descriptionLink = containerDesc.documentSelfLink;
                    containerState.parentLink = containerHostLink;
                    containerState.powerState = ContainerState.PowerState.PROVISIONING;
                    containerState.adapterManagementReference = containerDesc.instanceAdapterReference;
                    containerState.image = containerDesc.image;
                    containerState.command = containerDesc.command;
                    containerState.groupResourcePlacementLink = GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK;
                    containerState.system = Boolean.TRUE;
                    containerState.volumes = containerDesc.volumes;

                    Operation op;
                    if (r.hasResult()) {
                        logInfo("Already created system container state: %s",
                                r.getResult().documentSelfLink);
                        op = Operation.createPut(this, containerStateLink);
                    } else {
                        op = OperationUtil.createForcedPost(this,
                                ContainerFactoryService.SELF_LINK);
                    }

                    sendRequest(op
                            .setBody(containerState)
                            .setCompletion(
                                    (o, e) -> {
                                        if (e != null) {
                                            logWarning("Failure creating system container: "
                                                    + Utils.toString(e));
                                            return;
                                        }
                                        ContainerState body = o.getBody(ContainerState.class);
                                        logInfo("Created system ContainerState: %s ",
                                                body.documentSelfLink);
                                        createSystemContainerInstanceRequest(body, null);
                                        updateNumberOfContainers(containerHostLink);
                                    }));
                });
    }

    private void createSystemContainerInstanceRequest(ContainerState container,
            ServiceTaskCallback serviceTaskCallback) {
        AdapterRequest adapterRequest = new AdapterRequest();
        adapterRequest.resourceReference = UriUtils.buildUri(getHost(), container.documentSelfLink);
        adapterRequest.operationTypeId = ContainerOperationType.CREATE.id;

        if (serviceTaskCallback == null) {
            String systemContainerName = matchSystemContainerName(
                    SystemContainerDescriptions.getSystemContainerNames(), container.names);

            startAndCreateCallbackHandlerService(systemContainerName,
                    createSystemContainerReadyHandler(container),
                    (callback) -> createSystemContainerInstanceRequest(container, callback));
            return;
        }

        adapterRequest.serviceTaskCallback = serviceTaskCallback;

        String host = container.adapterManagementReference.getHost();
        String targetPath = null;
        // Operation patch;
        if (StringUtils.isBlank(host)) {
            // There isn't old version of system container.
            // patch = Operation.createPatch(getHost(),
            // container.adapterManagementReference.toString());
            targetPath = container.adapterManagementReference.toString();
        } else {
            // Old versions of system container contains host address in adapter reference.
            // patch = Operation.createPatch(container.adapterManagementReference);
            container.adapterManagementReference.getPath();
        }

        sendRequest(Operation.createPatch(getHost(), targetPath)
                .setBody(adapterRequest)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failure provisioning system container: " + Utils.toString(e));
                        return;
                    }
                    logInfo("Provisioning system container: %s with name: %s started ... ",
                            container.documentSelfLink, container.names);
                }));
    }

    private String matchSystemContainerName(List<String> systemContainerNames, List<String> names) {
        if (names != null) {
            for (String systemContainerName : systemContainerNames) {
                if (names.contains(systemContainerName)) {
                    return systemContainerName;
                }
            }
        }
        return null;
    }
}
