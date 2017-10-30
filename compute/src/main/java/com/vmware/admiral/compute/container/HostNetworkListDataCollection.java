/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.google.common.collect.Iterables;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.container.maintenance.ContainerNetworkMaintenance;
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
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Synchronize the ContainerNetworkStates with a list of network IDs
 */
public class HostNetworkListDataCollection extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.HOST_NETWORK_LIST_DATA_COLLECTION;

    public static final String DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_ID = "__default-list-data-collection";
    public static final String DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK = UriUtils
            .buildUriPath(FACTORY_LINK, DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_ID);

    protected static final long DATA_COLLECTION_LOCK_TIMEOUT_MILLISECONDS = Long.getLong(
            "com.vmware.admiral.compute.container.network.datacollection.lock.timeout.milliseconds",
            TimeUnit.MINUTES.toMillis(5));

    private static final int NETWORKS_INSPECT_DELAY_SECONDS = Integer.parseInt(System.getProperty(
            "com.vmware.admiral.compute.container.network.inspect.delay.seconds", "2"));
    private static final int NETWORKS_INSPECT_BATCH_SIZE = Integer.parseInt(System.getProperty(
            "com.vmware.admiral.compute.container.network.inspect.batch.size", "50"));

    public static class HostNetworkListDataCollectionState extends
            TaskServiceDocument<DefaultSubStage> {
        @Documentation(description = "The list of container host links.")
        @PropertyOptions(indexing = {
                PropertyIndexingOption.STORE_ONLY,
                PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE })
        public Map<String, Long> containerHostLinks;
    }

    public static class NetworkListCallback extends ServiceTaskCallbackResponse {
        public String containerHostLink;
        public URI hostAdapterReference;
        public Map<String, String> networkIdsAndNames = new HashMap<>();
        public boolean unlockDataCollectionForHost;

        public void addIdAndNames(String id, String name) {
            AssertUtil.assertNotNull(id, "networkId");
            networkIdsAndNames.put(id, name);
        }
    }

    public static ServiceDocument buildDefaultStateInstance() {
        HostNetworkListDataCollectionState state = new HostNetworkListDataCollectionState();
        state.documentSelfLink = DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK;
        state.taskInfo = new TaskState();
        state.taskInfo.stage = TaskStage.STARTED;
        state.containerHostLinks = new HashMap<>();
        return state;
    }

    public HostNetworkListDataCollection() {
        super(HostNetworkListDataCollectionState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
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
            post.fail(new LocalizableValidationException(
                    "Only one instance of networks data collection can be started",
                    "compute.networks.data-collection.single"));
            return;
        }

        post.setBodyNoCloning(initState).complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            logFine("Ignoring converted PUT.");
            put.complete();
            return;
        }

        if (!checkForBody(put)) {
            return;
        }

        HostNetworkListDataCollectionState putBody = put
                .getBody(HostNetworkListDataCollectionState.class);

        this.setState(put, putBody);
        put.setBodyNoCloning(putBody).complete();
    }

    @Override
    public void handlePatch(Operation op) {
        NetworkListCallback body = op.getBody(NetworkListCallback.class);
        if (body.hostAdapterReference == null) {
            body.hostAdapterReference = ContainerHostDataCollectionService
                    .getDefaultHostAdapter(getHost());
        }
        if (body.containerHostLink == null) {
            logFine("'containerHostLink' is required");
            op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
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

        logFine(() -> String.format(
                "Host network list callback invoked for host [%s] with network IDs: %s",
                body.containerHostLink, new ArrayList<>(body.networkIdsAndNames.keySet()))
        );

        // the patch will succeed regardless of the synchronization process
        if (state.containerHostLinks.get(body.containerHostLink) != null &&
                Instant.now().isBefore(Instant.ofEpochMilli(
                        (state.containerHostLinks.get(body.containerHostLink))))) {
            op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            op.complete();
            return;// return since there is an active data collection for this host.
        } else {
            state.containerHostLinks.put(body.containerHostLink,
                    Instant.now().toEpochMilli() + DATA_COLLECTION_LOCK_TIMEOUT_MILLISECONDS);
            op.complete();
            // complete patch operation and continue with the data collection
        }

        queryExistingNetworkStates(body);
    }

    private void queryExistingNetworkStates(NetworkListCallback body) {
        QueryTask queryTask = QueryUtil.buildQuery(ContainerNetworkState.class, true);

        // Clause to find all networks for the given host.
        String parentLinksItemField = QueryTask.QuerySpecification
                .buildCollectionItemName(ContainerNetworkState.FIELD_NAME_PARENT_LINKS);
        Query parentsClause = new Query()
                .setTermPropertyName(parentLinksItemField)
                .setTermMatchValue(body.containerHostLink)
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.SHOULD_OCCUR);

        // Clause to find all overlay networks (that may already exist on different hosts).
        Query overlayClause = new Query()
                .setTermPropertyName(ContainerNetworkState.FIELD_NAME_DRIVER)
                .setTermMatchValue("overlay")
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.SHOULD_OCCUR);

        // Intermediate query because of Xenon ?!
        Query intermediate = new Query().setOccurance(Occurance.MUST_OCCUR);
        intermediate.addBooleanClause(parentsClause);
        intermediate.addBooleanClause(overlayClause);

        queryTask.querySpec.query.addBooleanClause(intermediate);

        QueryUtil.addExpandOption(queryTask);
        QueryUtil.addBroadcastOption(queryTask);

        new ServiceDocumentQuery<>(getHost(), ContainerNetworkState.class)
                .query(queryTask, processNetworkStatesQueryResults(body));
    }

    private Consumer<ServiceDocumentQuery.ServiceDocumentQueryElementResult<ContainerNetworkState>> processNetworkStatesQueryResults(
            NetworkListCallback body) {
        List<ContainerNetworkState> existingNetworkStates = new ArrayList<>();

        return (r) -> {
            if (r.hasException()) {
                logSevere("Failed to query for existing ContainerNetworkState instances: %s",
                        r.getException() instanceof CancellationException
                                ? r.getException().getMessage()
                                : Utils.toString(r.getException()));
                unlockCurrentDataCollectionForHost(body.containerHostLink);
            } else if (r.hasResult()) {
                existingNetworkStates.add(r.getResult());
            } else {
                listHostNetworks(body, (o, ex) -> {
                    if (ex == null) {
                        NetworkListCallback callback = o.getBody(NetworkListCallback.class);
                        if (callback.hostAdapterReference == null) {
                            callback.hostAdapterReference = ContainerHostDataCollectionService
                                    .getDefaultHostAdapter(getHost());
                        }
                        updateContainerNetworkStates(callback, existingNetworkStates,
                                body.containerHostLink);
                    } else {
                        unlockCurrentDataCollectionForHost(body.containerHostLink);
                    }
                });
            }
        };
    }

    private void listHostNetworks(NetworkListCallback body, Operation.CompletionHandler c) {
        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = ContainerHostOperationType.LIST_NETWORKS.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = UriUtils.buildUri(getHost(), body.containerHostLink);
        sendRequest(Operation
                .createPatch(body.hostAdapterReference)
                .setBodyNoCloning(request)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion(c));
    }

    private void updateContainerNetworkStates(NetworkListCallback callback,
            List<ContainerNetworkState> networkStates, String callbackHostLink) {

        // inspect existing network states
        inspectExistingNetworks(networkStates);

        // process existing network states - update parent links and missing networks
        processExistingNetworks(callback, networkStates, callbackHostLink);

        // create newly discovered networks
        processDiscoveredNetworks(callback);
    }

    private void inspectExistingNetworks(
            List<ContainerNetworkState> networkStates) {
        // inspect networks in batches
        ContainerNetworkMaintenance networkMaintenance = new ContainerNetworkMaintenance(getHost());
        AtomicInteger counter = new AtomicInteger();
        Iterables.partition(networkStates, NETWORKS_INSPECT_BATCH_SIZE)
                .forEach(list -> getHost().schedule(() -> {
                    networkMaintenance.requestNetworksInspectIfNeeded(list);
                }, counter.getAndIncrement() * NETWORKS_INSPECT_DELAY_SECONDS, TimeUnit.SECONDS));
    }

    private void processExistingNetworks(NetworkListCallback callback,
            List<ContainerNetworkState> networkStates,
            String callbackHostLink) {
        for (ContainerNetworkState networkState : networkStates) {
            boolean isOverlay = "overlay".equals(networkState.driver);

            boolean existsInCallbackHost = false;
            if (networkState.id != null) {
                existsInCallbackHost = callback.networkIdsAndNames.containsKey(networkState.id);
                callback.networkIdsAndNames.remove(networkState.id);
            } else if (networkState.powerState == PowerState.PROVISIONING
                    || networkState.powerState == PowerState.RETIRED
                    || networkState.powerState == PowerState.ERROR) {
                String name = networkState.name;
                existsInCallbackHost = callback.networkIdsAndNames.containsValue(name);
                callback.networkIdsAndNames.values().remove(name);
            }

            if (networkState.parentLinks == null) {
                networkState.parentLinks = new ArrayList<>();
            }

            if (!existsInCallbackHost) {
                boolean active = networkState.powerState == PowerState.CONNECTED;
                if (!isOverlay) {
                    if (active) {
                        handleMissingContainerNetwork(networkState);
                    }
                } else {
                    if (networkState.parentLinks.contains(callbackHostLink)) {
                        networkState.parentLinks.remove(callbackHostLink);
                        handleUpdateParentLinks(networkState);
                    } else if (active && networkState.parentLinks.isEmpty()) {
                        handleMissingContainerNetwork(networkState);
                    }
                }
            } else {
                if (isOverlay && networkState.originatingHostLink != null
                        && !networkState.parentLinks.contains(callbackHostLink)) {
                    networkState.parentLinks.add(callbackHostLink);
                    handleUpdateParentLinks(networkState);
                }
            }
        }
    }

    private void processDiscoveredNetworks(NetworkListCallback callback) {
        // finished removing existing ContainerNetworkState, now deal with remaining IDs
        List<ContainerNetworkState> networksLeft = new ArrayList<>();

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
                                ContainerNetworkState networkState = buildDiscoveredNetworkState(
                                        callback, group, ContainerHostUtil.isVicHost(host), entry);

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

    private ContainerNetworkState buildDiscoveredNetworkState(NetworkListCallback nlc,
            List<String> group, boolean isVch, Entry<String, String> entry) {
        ContainerNetworkState state = new ContainerNetworkState();
        state.id = entry.getKey();
        state.name = entry.getValue();
        state.documentSelfLink = NetworkUtils.buildNetworkLink(state.id);
        state.external = true;

        state.tenantLinks = group;
        state.descriptionLink = String.format("%s-%s",
                ContainerNetworkDescriptionService.DISCOVERED_DESCRIPTION_LINK,
                UUID.randomUUID().toString());

        state.originatingHostLink = nlc.containerHostLink;

        state.parentLinks = new ArrayList<>(Arrays.asList(nlc.containerHostLink));

        state.adapterManagementReference = getNetworkAdapterReference(nlc.hostAdapterReference);

        if (isVch) {
            // VIC supports IPv4 range notation when configured during the VCH creation,
            // and Admiral discovers it during the data collection.
            state.customProperties = new HashMap<>();
            state.customProperties.put(
                    ContainerNetworkDescription.CUSTOM_PROPERTY_NETWORK_RANGE_FORMAT_ALLOWED,
                    Boolean.TRUE.toString());
        }

        return state;
    }

    private URI getNetworkAdapterReference(URI hostAdapter) {
        switch (hostAdapter.getPath()) {
        case ManagementUriParts.ADAPTER_DOCKER_HOST:
            return UriUtils.buildUri(ManagementUriParts.ADAPTER_DOCKER_NETWORK);
        case ManagementUriParts.ADAPTER_KUBERNETES_HOST:
            return UriUtils.buildUri(ManagementUriParts.ADAPTER_KUBERNETES_NETWORK);
        default:
            throw new IllegalArgumentException(
                    String.format("No network adapter for %s", hostAdapter.getPath()));
        }
    }

    private void unlockCurrentDataCollectionForHost(String containerHostLink) {
        NetworkListCallback body = new NetworkListCallback();
        body.containerHostLink = containerHostLink;
        body.unlockDataCollectionForHost = true;
        sendRequest(Operation.createPatch(getUri())
                .setBodyNoCloning(body)
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
                // check again if the network state already exists by id. This is needed in
                // cluster mode not to create container network states that we already have

                List<ContainerNetworkState> networkStatesFound = new ArrayList<>();
                QueryTask networkServicesQuery = QueryUtil.buildPropertyQuery(
                        ContainerNetworkState.class,
                        ContainerNetworkState.FIELD_NAME_ID, networkState.id);
                new ServiceDocumentQuery<>(getHost(), ContainerNetworkState.class)
                        .query(networkServicesQuery, (r) -> {
                            if (r.hasException()) {
                                logSevere("Failed to get network %s : %s",
                                        networkState.name, r.getException().getMessage());
                                callback.accept(r.getException());
                            } else if (r.hasResult()) {
                                networkStatesFound.add(r.getResult());
                            } else {
                                if (networkStatesFound.isEmpty()) {
                                    createDiscoveredContainerNetwork(callback, counter,
                                            networkState);
                                } else {
                                    if (counter.decrementAndGet() == 0) {
                                        callback.accept(null);
                                    }
                                }
                            }
                        });
            }
        }
    }

    private void createDiscoveredContainerNetwork(Consumer<Throwable> callback,
            AtomicInteger counter, ContainerNetworkState networkState) {
        logFine("Creating ContainerNetworkState for discovered network: %s", networkState.id);

        sendRequest(OperationUtil
                .createForcedPost(this, ContainerNetworkService.FACTORY_LINK)
                .setBodyNoCloning(networkState)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere("Failed to create ContainerNetworkState for discovered network"
                                + " (id=%s): %s", networkState.id, ex.getMessage());
                        callback.accept(ex);
                        return;
                    } else {
                        logInfo("Created ContainerNetworkState for discovered network: %s",
                                networkState.id);
                    }

                    ContainerNetworkState body = o.getBody(ContainerNetworkState.class);
                    createDiscoveredContainerNetworkDescription(body);

                    ContainerNetworkMaintenance networkMaintenance = new ContainerNetworkMaintenance(
                            getHost());
                    networkMaintenance.requestNetworkInspection(body);

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
                .setBodyNoCloning(networkDesc)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere("Failed to create ContainerNetworkDescription for discovered"
                                + " network (id=%s): %s", networkState.id, ex.getMessage());
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
                .setBodyNoCloning(patchNetworkState)
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

    private void handleUpdateParentLinks(ContainerNetworkState networkState) {
        ContainerNetworkState patchNetworkState = new ContainerNetworkState();
        patchNetworkState.parentLinks = networkState.parentLinks;

        if ((networkState.originatingHostLink != null) && !networkState.parentLinks.isEmpty()
                && (!networkState.parentLinks.contains(networkState.originatingHostLink))) {
            // set another parent like the "owner" of the network
            patchNetworkState.originatingHostLink = networkState.parentLinks.get(0);
        }

        sendRequest(Operation
                .createPatch(this, networkState.documentSelfLink)
                .setBodyNoCloning(patchNetworkState)
                .setCompletion((o, ex) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        logFine("Network %s not found to be updated its parent links.",
                                networkState.documentSelfLink);
                    } else if (ex != null) {
                        logWarning("Failed to update network %s parent links: %s",
                                networkState.documentSelfLink, Utils.toString(ex));
                    } else {
                        logInfo("Updated network parent links: %s",
                                networkState.documentSelfLink);
                    }
                }));
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

}
