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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState.PowerState;
import com.vmware.admiral.compute.container.network.NetworkUtils;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.FactoryService;
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
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Synchronize the ContainerNetworkStates with a list of network IDs
 */
public class HostNetworkListDataCollection extends StatefulService {

    public static class HostNetworkListDataCollectionFactoryService extends FactoryService {
        public static final String SELF_LINK = ManagementUriParts.HOST_NETWORK_LIST_DATA_COLLECTION;

        public static final String DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_ID = "__default-list-data-collection";
        public static final String DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK = UriUtils
                .buildUriPath(SELF_LINK, DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_ID);

        public HostNetworkListDataCollectionFactoryService() {
            super(HostNetworkListDataCollectionState.class);
            super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        }

        @Override
        public void handlePost(Operation post) {
            if (!post.hasBody()) {
                post.fail(new IllegalArgumentException("body is required"));
                return;
            }

            HostNetworkListDataCollectionState initState = post
                    .getBody(HostNetworkListDataCollectionState.class);
            if (initState.documentSelfLink == null
                    || !initState.documentSelfLink
                            .endsWith(DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_ID)) {
                post.fail(new IllegalArgumentException(
                        "Only one instance of list containers data collection can be started"));
                return;
            }

            post.setBody(initState).complete();
        }

        @Override
        public Service createServiceInstance() throws Throwable {
            return new HostNetworkListDataCollection();
        }

        public static ServiceDocument buildDefaultStateInstance() {
            HostNetworkListDataCollectionState state = new HostNetworkListDataCollectionState();
            state.documentSelfLink = DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK;
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskStage.STARTED;
            state.containerHostLinks = new HashSet<>();
            return state;
        }
    }

    public static class HostNetworkListDataCollectionState extends
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

    public static class NetworkListCallback extends ServiceTaskCallbackResponse {
        public String containerHostLink;
        public Map<String, String> networkIdsAndNames = new HashMap<>();
        public boolean unlockDataCollectionForHost;

        public void addIdAndNames(String id, String name) {
            AssertUtil.assertNotNull(id, "networkId");
            networkIdsAndNames.put(id, name);
        }
    }

    public HostNetworkListDataCollection() {
        super(HostNetworkListDataCollectionState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handlePatch(Operation op) {
        NetworkListCallback body = op.getBody(NetworkListCallback.class);
        if (body.containerHostLink == null) {
            logFine("'containerHostLink' is required");
            op.complete();
            return;
        }

        HostNetworkListDataCollectionState state = getState(op);
        if (body.unlockDataCollectionForHost) {
            // patch to mark that there is no active list networks data collection for a given host.
            state.containerHostLinks.remove(body.containerHostLink);
            op.complete();
            return;
        }

        AssertUtil.assertNotNull(body.networkIdsAndNames, "networkIdsAndNames");

        if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
            logFine("Host network list callback invoked for host [%s] with network IDs: %s",
                    body.containerHostLink, body.networkIdsAndNames.keySet().stream()
                            .collect(Collectors.toList()));
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

        List<ContainerNetworkState> networkStates = new ArrayList<ContainerNetworkState>();

        QueryTask queryTask = QueryUtil.buildQuery(ContainerNetworkState.class, true);

        // Clause to find all networks for the given host.

        String parentLinksItemField = QueryTask.QuerySpecification
                .buildCollectionItemName(ContainerNetworkState.FIELD_NAME_PARENT_LINKS);
        QueryTask.Query parentsClause = new QueryTask.Query()
                .setTermPropertyName(parentLinksItemField)
                .setTermMatchValue(body.containerHostLink)
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.SHOULD_OCCUR);
        queryTask.querySpec.query.addBooleanClause(parentsClause);

        // Clause to find all overlay networks (that may already exist on different hosts).

        QueryTask.Query overlayClause = new QueryTask.Query()
                .setTermPropertyName(ContainerNetworkState.FIELD_NAME_DRIVER)
                .setTermMatchValue("overlay")
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.SHOULD_OCCUR);
        queryTask.querySpec.query.addBooleanClause(overlayClause);

        QueryUtil.addExpandOption(queryTask);
        QueryUtil.addBroadcastOption(queryTask);

        final String hostId = Service.getId(body.containerHostLink);

        new ServiceDocumentQuery<ContainerNetworkState>(getHost(), ContainerNetworkState.class)
                .query(queryTask,
                        (r) -> {
                            if (r.hasException()) {
                                logSevere(
                                        "Failed to query for existing ContainerNetworkState instances: %s",
                                        r.getException() instanceof CancellationException
                                                ? r.getException().getMessage()
                                                : Utils.toString(r.getException()));
                                unlockCurrentDataCollectionForHost(body.containerHostLink);
                            } else if (r.hasResult()) {
                                networkStates.add(r.getResult());
                            } else {
                                AdapterRequest request = new AdapterRequest();
                                request.operationTypeId = ContainerHostOperationType.LIST_NETWORKS.id;
                                request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
                                request.resourceReference = UriUtilsExtended.buildUri(getHost(),
                                        body.containerHostLink);
                                sendRequest(Operation
                                        .createPatch(this, ManagementUriParts.ADAPTER_DOCKER_HOST)
                                        .setBody(request)
                                        .addPragmaDirective(
                                                Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                                        .setCompletion(
                                                (o, ex) -> {
                                                    if (ex == null) {
                                                        NetworkListCallback callback = o
                                                                .getBody(NetworkListCallback.class);
                                                        updateContainerNetworkStates(callback,
                                                                networkStates, hostId);
                                                    } else {
                                                        unlockCurrentDataCollectionForHost(
                                                                body.containerHostLink);
                                                    }
                                                }));
                            }
                        });
    }

    private void updateContainerNetworkStates(NetworkListCallback callback,
            List<ContainerNetworkState> networkStates, String hostId) {
        for (ContainerNetworkState networkState : networkStates) {

            boolean exists = false;
            if (networkState.id != null) {
                exists = callback.networkIdsAndNames.containsKey(networkState.id);
                callback.networkIdsAndNames.remove(networkState.id);
            } else if (networkState.powerState.equals(PowerState.PROVISIONING)
                    || networkState.powerState.equals(PowerState.RETIRED)
                    || networkState.powerState.equals(PowerState.ERROR)) {
                String name = networkState.name;
                exists = callback.networkIdsAndNames.containsValue(name);
                callback.networkIdsAndNames.values().remove(name);
            }

            // if networkId doesn't exists, mark the ContainerNetworkState as missing
            // provisioning, allocating networks in error might not have id associated yet.
            if (!exists) {
                boolean active = networkState.powerState == PowerState.CONNECTED;
                if (active) {
                    handleMissingContainerNetwork(networkState);
                }
            } else {
                callback.networkIdsAndNames.remove(networkState.id);
            }
        }

        // finished removing existing ContainerNetworkState, now deal with remaining IDs
        List<ContainerNetworkState> networksLeft = new ArrayList<ContainerNetworkState>();

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

                            for (Entry<String, String> entry : callback.networkIdsAndNames
                                    .entrySet()) {
                                ContainerNetworkState networkState = new ContainerNetworkState();
                                networkState.id = entry.getKey();
                                networkState.name = entry.getValue();

                                networkState.tenantLinks = group;
                                networkState.descriptionLink = UriUtils
                                        .buildUriPath(
                                                ContainerNetworkDescriptionService.DISCOVERED_DESCRIPTION_LINK,
                                                UUID.randomUUID().toString());

                                networkState.originatingHostLink = callback.containerHostLink;

                                networkState.parentLinks = new ArrayList<>(
                                        Arrays.asList(callback.containerHostLink));

                                networkState.adapterManagementReference = UriUtils.buildUri(
                                        getHost(), ManagementUriParts.ADAPTER_DOCKER_NETWORK);

                                networksLeft.add(networkState);
                            }

                            createDiscoveredContainerNetworks(
                                    networksLeft,
                                    (e) -> {
                                        unlockCurrentDataCollectionForHost(
                                                callback.containerHostLink);
                                    });
                        });

        sendRequest(operation);
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        HostNetworkListDataCollectionState putBody = put
                .getBody(HostNetworkListDataCollectionState.class);

        this.setState(put, putBody);
        put.setBody(putBody).complete();
    }

    private void unlockCurrentDataCollectionForHost(String containerHostLink) {
        NetworkListCallback body = new NetworkListCallback();
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

    private void createDiscoveredContainerNetworks(List<ContainerNetworkState> networkStates,
            Consumer<Throwable> callback) {
        if (networkStates.isEmpty()) {
            callback.accept(null);
        } else {
            AtomicInteger counter = new AtomicInteger(networkStates.size());
            for (ContainerNetworkState networkState : networkStates) {
                if (networkState.name == null) {
                    logInfo("Name not set for network: %s", networkState.documentSelfLink);
                    if (counter.decrementAndGet() == 0) {
                        callback.accept(null);
                    }
                    continue;
                }
                // check again if the network state already exists by name. This is needed in
                // cluster mode not to create container network states that we already have
                Operation operation = Operation
                        .createGet(this, UriUtilsExtended.buildUriPath(
                                ContainerNetworkService.FACTORY_LINK, networkState.name))
                        .setCompletion(
                                (o, ex) -> {
                                    if (ex != null) {
                                        if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                                            createDiscoveredContainerNetwork(callback, counter,
                                                    networkState);
                                        } else {
                                            logSevere("Failed to get network %s : %s",
                                                    networkState.name, ex.getMessage());
                                            callback.accept(ex);
                                        }
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

    private void createDiscoveredContainerNetwork(Consumer<Throwable> callback,
            AtomicInteger counter, ContainerNetworkState networkState) {

        logFine("Creating ContainerNetworkState for discovered network: %s", networkState.id);

        sendRequest(OperationUtil
                .createForcedPost(this, ContainerNetworkService.FACTORY_LINK)
                .setBody(networkState)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logSevere(
                                        "Failed to create ContainerNetworkState for discovered network (id=%s): %s",
                                        networkState.id,
                                        ex.getMessage());
                                callback.accept(ex);
                                return;
                            } else {
                                logInfo("Created ContainerNetworkState for discovered network: %s",
                                        networkState.id);
                            }

                            ContainerNetworkState body = o.getBody(ContainerNetworkState.class);
                            createDiscoveredContainerNetworkDescription(body);

                            if (counter.decrementAndGet() == 0) {
                                callback.accept(null);
                            }
                        }));
    }

    private void createDiscoveredContainerNetworkDescription(ContainerNetworkState networkState) {

        logFine("Creating ContainerNetworkDescription for discovered network: %s", networkState.id);

        ContainerNetworkDescription networkDesc = NetworkUtils
                .createContainerNetworkDescription(networkState);

        sendRequest(OperationUtil
                .createForcedPost(this, ContainerNetworkDescriptionService.FACTORY_LINK)
                .setBody(networkDesc)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logSevere(
                                        "Failed to create ContainerNetworkDescription for discovered network (id=%s): %s",
                                        networkState.id, ex.getMessage());
                            } else {
                                logInfo("Created ContainerNetworkDescription for discovered network: %s",
                                        networkState.id);
                            }
                        }));

    }

    private void handleMissingContainerNetwork(ContainerNetworkState networkState) {
        // patch network status to RETIRED
        ContainerNetworkState patchNetworkState = new ContainerNetworkState();
        patchNetworkState.powerState = PowerState.RETIRED;
        sendRequest(Operation
                .createPatch(this, networkState.documentSelfLink)
                .setBody(patchNetworkState)
                .setCompletion((o, ex) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        logFine("Network %s not found to be marked as missing.",
                                networkState.documentSelfLink);
                    } else if (ex != null) {
                        logWarning("Failed to mark network %s as missing: %s",
                                networkState.documentSelfLink, Utils.toString(ex));
                    } else {
                        logInfo("Marked network as missing: %s",
                                networkState.documentSelfLink);
                    }
                }));
    }

}
