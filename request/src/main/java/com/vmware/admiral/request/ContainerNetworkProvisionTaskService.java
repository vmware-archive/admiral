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

package com.vmware.admiral.request;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.PropertyUtils.mergeLists;
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;
import static com.vmware.admiral.request.utils.RequestUtils.getContextId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.ContainerNetworkProvisionTaskService.ContainerNetworkProvisionTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Task implementing the provisioning of a container network.
 */
public class ContainerNetworkProvisionTaskService
        extends
        AbstractTaskStatefulService<ContainerNetworkProvisionTaskService.ContainerNetworkProvisionTaskState, ContainerNetworkProvisionTaskService.ContainerNetworkProvisionTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_CONTAINER_NETWORK_TASKS;

    public static final String DISPLAY_NAME = "Container Network Provision";

    // cached network description
    private volatile ContainerNetworkDescription networkDescription;

    public static class ContainerNetworkProvisionTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerNetworkProvisionTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            PROVISIONING,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(PROVISIONING));
        }

        /** (Required) The description that defines the requested resource. */
        @Documentation(description = "Type of resource to create.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String resourceDescriptionLink;

        /** (Required) Number of resources to provision. */
        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public Long resourceCount;

        /** (Required) Links to already allocated resources that are going to be provisioned. */
        @Documentation(description = "Links to already allocated resources that are going to be provisioned.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public List<String> resourceLinks;

        // Service use fields:

        /** (Internal) Reference to the adapter that will fulfill the provision request. */
        @Documentation(description = "Reference to the adapter that will fulfill the provision request.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public URI instanceAdapterReference;

    }

    public ContainerNetworkProvisionTaskService() {
        super(ContainerNetworkProvisionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(ContainerNetworkProvisionTaskState state) {
        assertNotNull(state.resourceDescriptionLink, "resourceDescriptionLink");
        assertNotNull(state.resourceLinks, "resourceLinks");

        state.resourceCount = (long) state.resourceLinks.size();

        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
        }
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            ContainerNetworkProvisionTaskState patchBody,
            ContainerNetworkProvisionTaskState currentState) {

        currentState.instanceAdapterReference = mergeProperty(
                currentState.instanceAdapterReference, patchBody.instanceAdapterReference);

        currentState.resourceLinks = mergeLists(
                currentState.resourceLinks, patchBody.resourceLinks);

        currentState.resourceCount = mergeProperty(currentState.resourceCount,
                patchBody.resourceCount);

        return false;
    }

    @Override
    protected void handleStartedStagePatch(ContainerNetworkProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            provisionNetworks(state);
            break;
        case PROVISIONING:
            break;
        case COMPLETED:
            complete(state, SubStage.COMPLETED);
            break;
        case ERROR:
            completeWithError(state, SubStage.ERROR);
            break;
        default:
            break;
        }
    }

    private void provisionNetworks(ContainerNetworkProvisionTaskState state) {

        logInfo("Provision request for %s networks", state.resourceCount);

        createTaskCallbackAndGetNetworkDescription(state, (taskCallback, networkDescription) -> {
            state.instanceAdapterReference = networkDescription.instanceAdapterReference;
            selectHosts(state, networkDescription, (hosts) -> {

                if (hosts.size() == 1 || state.resourceLinks.size() == hosts.size()) {
                    Iterator<ComputeState> hostIt = hosts.iterator();
                    ComputeState host = hostIt.next();
                    for (String networkLink : state.resourceLinks) {
                        provisionNetwork(state, networkLink, host, taskCallback);

                        if (hostIt.hasNext()) {
                            host = hostIt.next();
                        }
                    }
                } else {
                    String err = String.format(
                            "Unexpected size of resource links and hosts, hosts should be one or equal to resource links! Actual resources - [%s], hosts - [%s]",
                            state.resourceLinks.size(), hosts.size());
                    failTask(err, null);
                }

            });
        });

        sendSelfPatch(createUpdateSubStageTask(state, SubStage.PROVISIONING));
    }

    private void provisionNetwork(ContainerNetworkProvisionTaskState state,
            String networkLink, ComputeState host, ServiceTaskCallback taskCallback) {
        getNetwork(networkLink, (network) -> {
            updateContainerNetworkStateWithContainerHostLink(network, host,
                    () -> createAndSendContainerNetworkRequest(network, state, taskCallback,
                            networkLink));
        });
    }

    private void getNetwork(String networkLink, Consumer<ContainerNetworkState> callback) {
        Operation.createGet(this, networkLink).setCompletion((op, ex) -> {
            if (ex != null) {
                failTask("Failed retrieving network: " + Utils.toString(ex), null);
                return;
            }

            ContainerNetworkState network = op.getBody(ContainerNetworkState.class);
            callback.accept(network);
        }).sendWith(this);
    }

    private void getHost(String hostLink, Consumer<ComputeState> callback) {
        Operation.createGet(this, hostLink).setCompletion((op, ex) -> {
            if (ex != null) {
                failTask("Failed retrieving host: " + Utils.toString(ex), null);
                return;
            }

            ComputeState host = op.getBody(ComputeState.class);
            callback.accept(host);
        }).sendWith(this);
    }

    private void updateContainerNetworkStateWithContainerHostLink(
            ContainerNetworkState currentNetworkState,
            ComputeState host, Runnable callbackFunction) {

        ContainerNetworkState patch = new ContainerNetworkState();
        patch.originatingHostLink = host.documentSelfLink;
        patch.parentLinks = new ArrayList<>(Arrays.asList(host.documentSelfLink));

        if (currentNetworkState.driver == null || currentNetworkState.driver.isEmpty()) {

            String cluster = host.customProperties
                    .get(ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME);
            if (cluster != null && !cluster.isEmpty()) {
                patch.driver = ContainerNetworkDescription.NETWORK_DRIVER_OVERLAY;
            }
        }

        sendRequest(Operation
                .createPatch(this, currentNetworkState.documentSelfLink)
                .setBody(patch)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                String errMsg = String.format("Error while updating network: %s",
                                        currentNetworkState.documentSelfLink);
                                logWarning(errMsg);
                                failTask(errMsg, e);
                            } else {
                                callbackFunction.run();
                            }
                        }));
    }

    private void createAndSendContainerNetworkRequest(ContainerNetworkState networkState,
            ContainerNetworkProvisionTaskState state, ServiceTaskCallback taskCallback,
            String networkSelfLink) {

        AdapterRequest networkRequest = new AdapterRequest();
        networkRequest.resourceReference = UriUtils.buildUri(getHost(), networkSelfLink);
        networkRequest.serviceTaskCallback = taskCallback;
        if (Boolean.TRUE.equals(networkState.external)) {
            // The network is defined as external, just validate that it exists actually.
            networkRequest.operationTypeId = NetworkOperationType.INSPECT.id;
        } else {
            networkRequest.operationTypeId = NetworkOperationType.CREATE.id;
        }
        networkRequest.customProperties = state.customProperties;

        sendRequest(Operation.createPatch(state.instanceAdapterReference)
                .setBody(networkRequest)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("AdapterRequest failed for network: " + networkSelfLink, e);
                        return;
                    }
                    logInfo("Network '%s' request started for: %s", networkRequest.operationTypeId,
                            networkSelfLink);
                }));
    }

    private void createTaskCallbackAndGetNetworkDescription(
            ContainerNetworkProvisionTaskState state,
            BiConsumer<ServiceTaskCallback, ContainerNetworkDescription> callbackFunction) {
        AtomicReference<ServiceTaskCallback> taskCallback = new AtomicReference<>();
        AtomicReference<ContainerNetworkDescription> networkDescription = new AtomicReference<>();

        createCounterSubTaskCallback(
                state,
                state.resourceCount,
                false,
                SubStage.COMPLETED,
                (callback) -> {
                    taskCallback.set(callback);
                    ContainerNetworkDescription nd = networkDescription.get();
                    if (nd != null) {
                        callbackFunction.accept(callback, nd);
                    }
                });

        getContainerNetworkDescription(state, (nd) -> {
            networkDescription.set(nd);
            ServiceTaskCallback callback = taskCallback.get();
            if (callback != null) {
                callbackFunction.accept(callback, nd);
            }
        });
    }

    private void getContainerNetworkDescription(ContainerNetworkProvisionTaskState state,
            Consumer<ContainerNetworkDescription> callbackFunction) {
        if (networkDescription != null) {
            callbackFunction.accept(networkDescription);
            return;
        }

        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                failTask("Failure retrieving container network description state",
                                        e);
                                return;
                            }

                            ContainerNetworkDescription desc = o
                                    .getBody(ContainerNetworkDescription.class);
                            this.networkDescription = desc;
                            callbackFunction.accept(desc);
                        }));
    }

    private void getContextContainerStates(ContainerNetworkProvisionTaskState state,
            Consumer<Map<String, List<ContainerState>>> callback) {
        String contextId = getContextId(state);

        QueryTask q = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_COMPOSITE_COMPONENT_LINK, UriUtils.buildUriPath(
                        CompositeComponentFactoryService.SELF_LINK, contextId));
        q.taskInfo.isDirect = false;
        QueryUtil.addExpandOption(q);

        Map<String, List<ContainerState>> containersByDescriptionLink = new HashMap<>();

        new ServiceDocumentQuery<ContainerState>(getHost(), ContainerState.class)
                .query(q,
                        (r) -> {
                            if (r.hasException()) {
                                failTask(
                                        "Exception while selecting containers with contextId ["
                                                + contextId + "]",
                                        r.getException());
                            } else if (r.hasResult()) {
                                ContainerState result = r.getResult();

                                List<ContainerState> containers = containersByDescriptionLink
                                        .get(result.descriptionLink);
                                if (containers == null) {
                                    containers = new ArrayList<>();
                                    containersByDescriptionLink.put(result.descriptionLink,
                                            containers);
                                }

                                containers.add(result);
                            } else {
                                callback.accept(containersByDescriptionLink);
                            }
                        });
    }

    private void getContextContainerDescriptions(
            Map<String, List<ContainerState>> containersByDescriptionLink,
            Consumer<List<ContainerDescription>> callback) {

        if ((containersByDescriptionLink == null) || (containersByDescriptionLink.isEmpty())) {
            callback.accept(Collections.emptyList());
            return;
        }

        QueryTask q = QueryUtil.buildQuery(ContainerDescription.class, true);

        QueryUtil.addExpandOption(q);
        QueryUtil.addListValueClause(q, ContainerDescription.FIELD_NAME_SELF_LINK,
                containersByDescriptionLink.keySet());

        q.taskInfo.isDirect = false;

        List<ContainerDescription> result = new ArrayList<>();

        new ServiceDocumentQuery<ContainerDescription>(getHost(), ContainerDescription.class)
                .query(q, (r) -> {
                    if (r.hasException()) {
                        failTask("Exception while selecting container descriptions",
                                r.getException());
                    } else if (r.hasResult()) {
                        result.add(r.getResult());
                    } else {
                        callback.accept(result);
                    }
                });
    }

    private List<ContainerState> getDependantContainerStates(
            List<ContainerDescription> containerDescriptions,
            Map<String, List<ContainerState>> containersByDescriptionLink,
            ContainerNetworkDescription networkDescription) {
        List<ContainerState> result = new ArrayList<>();
        for (ContainerDescription cd : containerDescriptions) {
            if (cd.networks != null && cd.networks.get(networkDescription.name) != null) {
                result.addAll(containersByDescriptionLink.get(cd.documentSelfLink));
            }
        }
        return result;
    }

    private void selectHosts(ContainerNetworkProvisionTaskState state,
            ContainerNetworkDescription networkDescription, Consumer<List<ComputeState>> callback) {

        // If hosts are provided use them directly to try to provision the network
        // (e.g. when External network CRUD operations)
        List<String> providedHostIds = ContainerNetworkAllocationTaskService
                .getProvidedHostIds(state);

        if (providedHostIds != null) {
            retrieveContainerHostsByIds(state, providedHostIds, (hosts) -> {
                callback.accept(hosts);
            });
            return;
        }

        getContextContainerStates(state, (states) -> {
            getContextContainerDescriptions(states, (descriptions) -> {
                List<ContainerState> containerStatesForNetwork = getDependantContainerStates(
                        descriptions, states, networkDescription);
                if (containerStatesForNetwork.isEmpty()) {
                    String err = String.format(
                            "No container states depending on network description [%s] found.",
                            networkDescription.name);
                    failTask(err, null);
                } else {
                    String hostLink = containerStatesForNetwork.get(0).parentLink;
                    getHost(hostLink, (host) -> {
                        callback.accept(Collections.singletonList(host));
                    });
                }
            });
        });
    }

    private void retrieveContainerHostsByIds(ContainerNetworkProvisionTaskState state,
            List<String> hostIds,
            Consumer<List<ComputeState>> callbackFunction) {

        final List<ComputeState> result = new ArrayList<>();

        List<String> remainingHostIds = new ArrayList<>(hostIds);

        final QueryTask queryTask = QueryUtil.buildQuery(ComputeState.class, true);
        QueryUtil.addListValueClause(queryTask, ComputeState.FIELD_NAME_ID, hostIds);
        QueryUtil.addExpandOption(queryTask);

        new ServiceDocumentQuery<ComputeState>(getHost(), ComputeState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        failTask(String.format(
                                "Exception during retrieving hosts with ids [%s]. Error: [%s]",
                                hostIds,
                                Utils.toString(r.getException())), r.getException());
                    } else if (r.hasResult()) {
                        ComputeState cs = r.getResult();
                        result.add(cs);
                        remainingHostIds.remove(cs.id);
                    } else {
                        if (!remainingHostIds.isEmpty()) {
                            failTask(String.format(
                                    "Not all hosts were found! Remaining hosts: [%s]!",
                                    remainingHostIds),
                                    r.getException());
                            return;
                        }

                        callbackFunction.accept(result);
                    }
                });
    }
}
