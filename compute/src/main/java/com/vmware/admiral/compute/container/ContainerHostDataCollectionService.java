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

import static com.vmware.admiral.compute.ContainerHostService.RETRIES_COUNT_PROP_NAME;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ResourcePoolQueryHelper;
import com.vmware.admiral.compute.ResourcePoolQueryHelper.QueryResult.ResourcePoolData;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.ContainerListCallback;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionFactoryService;
import com.vmware.admiral.service.common.AbstractCallbackServiceHandler;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class ContainerHostDataCollectionService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.CONTAINER_HOST_DATA_COLLECTION;
    private static final String HOST_INFO_DATA_COLLECTION_ID = "host-info-data-collection";
    public static final String HOST_INFO_DATA_COLLECTION_LINK = UriUtilsExtended.buildUriPath(
            FACTORY_LINK, HOST_INFO_DATA_COLLECTION_ID);

    private static final long MAINTENANCE_INTERVAL_MICROS = Long.getLong(
            "com.vmware.admiral.compute.container.maintenance.interval.micros",
            TimeUnit.MINUTES.toMicros(5));
    private static final int MAX_RETRIES_COUNT = Integer.getInteger(
            "com.vmware.admiral.compute.container.host.maintenance.max.retries", 3);

    private static final long FREQUENCY_OF_GENERAL_HOST_COLLECTION_MICROS = Long.getLong(
            "com.vmware.admiral.compute.container.host.frequency.interval.micros",
            TimeUnit.SECONDS.toMicros(20));

    public static final String RESOURCE_POOL_CPU_USAGE_CUSTOM_PROP = "__cpuUsage";
    public static final String RESOURCE_POOL_AVAILABLE_MEMORY_CUSTOM_PROP = "__availableMemory";

    public static ServiceDocument buildDefaultStateInstance() {
        ContainerHostDataCollectionState state = new ContainerHostDataCollectionState();
        state.documentSelfLink = HOST_INFO_DATA_COLLECTION_LINK;
        return state;
    }

    public static class ContainerHostDataCollectionState extends ServiceDocument {
        @Documentation(description = "List of container host links to be updated as part of Patch triggered data collection.")
        @PropertyOptions(indexing = {
                PropertyIndexingOption.STORE_ONLY,
                PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE })
        public Collection<String> computeContainerHostLinks;
        @Documentation(description = "Flag indicating if this is data-collection after container remove.")
        @PropertyOptions(indexing = {
                PropertyIndexingOption.STORE_ONLY,
                PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE })
        public boolean remove;

        @Documentation(description = "Indicator of the last run of data-collection.")
        @PropertyOptions(indexing = {
                PropertyIndexingOption.STORE_ONLY,
                PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE })
        public long lastRunTimeMicros;

        @Documentation(description = "Count of how many times the last run data-collection has been run"
                + " within very small time period.")
        @PropertyOptions(indexing = {
                PropertyIndexingOption.STORE_ONLY,
                PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE })
        public long skipRunCount;
    }

    public ContainerHostDataCollectionService() {
        super(ContainerHostDataCollectionState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.setMaintenanceIntervalMicros(MAINTENANCE_INTERVAL_MICROS);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        // don't keep any versions for the logs
        template.documentDescription.versionRetentionLimit = 1;
        return template;
    }

    @Override
    public void handleStart(Operation post) {
        super.handleStart(post);

        // perform maintenance on startup to refresh the container attributes
        getHost().registerForServiceAvailability((o, ex) -> {
            if (ex != null) {
                logWarning("Skipping maintenance because service failed to start: "
                        + ex.getMessage());

            } else {
                handleMaintenance(o);
            }
        }, getSelfLink());
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!checkForBody(patch)) {
            return;
        }

        ContainerHostDataCollectionState body = patch
                .getBody(ContainerHostDataCollectionState.class);

        if (body.computeContainerHostLinks == null || body.computeContainerHostLinks.isEmpty()) {
            ContainerHostDataCollectionState state = getState(patch);
            long now = Utils.getNowMicrosUtc();
            if (state.lastRunTimeMicros + FREQUENCY_OF_GENERAL_HOST_COLLECTION_MICROS > now) {
                if (state.skipRunCount++ > 2) {
                    // don't run general data collection on all hosts if the requests are too
                    // frequent
                    patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
                    patch.complete();
                    return;
                }
            } else {
                // reset the count if the frequency time has passed.
                state.skipRunCount = 0;
            }

            state.lastRunTimeMicros = Utils.getNowMicrosUtc();
            updateHostInfoDataCollection(patch);
        } else {
            // retrieve resource pools for the given computes
            ResourcePoolQueryHelper rpHelper = ResourcePoolQueryHelper.createForComputes(getHost(),
                    body.computeContainerHostLinks);

            rpHelper.query(qr -> {
                if (qr.error != null) {
                    patch.fail(qr.error);
                    return;
                }

                for (ComputeState computeState : qr.computesByLink.values()) {
                    if (!body.remove) {
                        // if we're adding a host we need to wait for the host info to be populated
                        // first
                        updateContainerHostInfo(computeState.documentSelfLink, (o, error) -> {
                            if (error) {
                                handleHostNotAvailable(computeState);
                            } else {
                                handleHostAvailable(computeState);
                                // TODO multiple operations in parallel for the same RP;
                                //      needs to be reworked
                                updateResourcePool(computeState,
                                        qr.rpLinksByComputeLink.get(computeState.documentSelfLink),
                                        body.remove);
                                updateContainerHostContainers(computeState.documentSelfLink);
                                updateHostStats(computeState.documentSelfLink);
                            }
                        }, null);
                    } else {
                        // TODO multiple operations in parallel for the same RP;
                        //      needs to be reworked
                        updateResourcePool(computeState,
                                qr.rpLinksByComputeLink.get(computeState.documentSelfLink),
                                body.remove);
                    }
                }
            });

            patch.complete();
        }
    }

    private void handleHostAvailable(ComputeState computeState) {
        PowerState patchPowerState = computeState.powerState;
        String countString = computeState.customProperties.get(RETRIES_COUNT_PROP_NAME);
        int count = Integer.parseInt(countString != null ? countString : "0");

        if (PowerState.SUSPEND.equals(computeState.powerState) && count == 0) {
            patchPowerState = PowerState.SUSPEND;
        } else if (!PowerState.OFF.equals(computeState.powerState)) {
            /* when a host is disabled manually the state should not be changed to ON */
            count = 0;
            patchPowerState = PowerState.ON;
        }

        ComputeState patchState = new ComputeState();
        patchState.powerState = patchPowerState;
        patchState.customProperties = new HashMap<String, String>();
        patchState.customProperties.put(RETRIES_COUNT_PROP_NAME, String.valueOf(count));
        sendRequest(Operation.createPatch(this, computeState.documentSelfLink)
                .setBody(patchState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error while patching computeState: %s", e);
                        return;
                    }
                }));
    }

    private void handleHostNotAvailable(ComputeState computeState) {
        if (computeState.powerState.equals(PowerState.OFF)) {
            return;
        }
        PowerState patchPowerState;
        int count;
        if (computeState.customProperties.get(RETRIES_COUNT_PROP_NAME) != null) {
            count = Integer.parseInt(computeState.customProperties.get(RETRIES_COUNT_PROP_NAME));
            count++;
            if (count == MAX_RETRIES_COUNT) {
                patchPowerState = PowerState.OFF;
                count = 0;
            } else {
                patchPowerState = PowerState.SUSPEND;
            }
        } else {
            count = 1;
            patchPowerState = PowerState.SUSPEND;
        }
        ComputeState patchState = new ComputeState();
        patchState.powerState = patchPowerState;
        patchState.customProperties = new HashMap<>();
        patchState.customProperties.put(RETRIES_COUNT_PROP_NAME, Integer.toString(count));

        CompletionHandler cc = (operation, e) -> {
            if (e != null) {
                logWarning("Failed updating ComputeState: " + computeState.documentSelfLink);
                return;
            }

            if (PowerState.OFF.equals(patchPowerState)) {
                disableContainersForHost(computeState.documentSelfLink);
            }
        };
        sendRequest(Operation
                .createPatch(this, computeState.documentSelfLink)
                .setBody(patchState)
                .setCompletion(cc));
    }

    private void updateResourcePool(ComputeState computeState, Collection<String> rpLinks,
            boolean remove) {
        Long totalMemory = PropertyUtils.getPropertyLong(computeState.customProperties,
                ContainerHostService.DOCKER_HOST_TOTAL_MEMORY_PROP_NAME).orElse(Long.MAX_VALUE);

        Long hostAvailableMemory = PropertyUtils.getPropertyLong(computeState.customProperties,
                ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME)
                .orElse(totalMemory);

        CompletionHandler cc = (o1, ex1) -> {
            if (ex1 != null) {
                logSevere(Utils.toString(ex1));
                return;
            }
            ResourcePoolService.ResourcePoolState resourcePoolState = o1
                    .getBody(ResourcePoolService.ResourcePoolState.class);

            int coef = remove ? -1 : 1;

            resourcePoolState.minMemoryBytes = 0;
            if (resourcePoolState.maxMemoryBytes == Long.MAX_VALUE || totalMemory
                    .equals(Long.MAX_VALUE)) {
                resourcePoolState.maxMemoryBytes = Long.MAX_VALUE;
            } else {
                resourcePoolState.maxMemoryBytes += totalMemory * coef;
            }

            Long resourcePoolAvailableMemory = resourcePoolState.customProperties != null
                    ? PropertyUtils
                            .getPropertyLong(resourcePoolState.customProperties,
                                    RESOURCE_POOL_AVAILABLE_MEMORY_CUSTOM_PROP)
                            .orElse(null)
                    : null;
            if (hostAvailableMemory != Long.MAX_VALUE
                    && resourcePoolAvailableMemory != null
                    && resourcePoolAvailableMemory != Long.MAX_VALUE) {
                resourcePoolAvailableMemory += hostAvailableMemory * coef;
                resourcePoolState.customProperties
                        .put(RESOURCE_POOL_AVAILABLE_MEMORY_CUSTOM_PROP,
                                Long.toString(resourcePoolAvailableMemory));
            }

            sendRequest(Operation.createPut(this, resourcePoolState.documentSelfLink)
                    .setBody(resourcePoolState).setCompletion((op, e) -> {
                        if (e != null) {
                            logSevere("Unable to update the resource pool with link "
                                    + resourcePoolState.documentSelfLink);
                        }
                        updatePolicies(resourcePoolState);
                    }));
        };

        // update all resource pools this compute is part of
        if (rpLinks != null) {
            for (String rpLink : rpLinks) {
                sendRequest(Operation.createGet(this, rpLink).setCompletion(cc));
            }
        }
    }

    /**
     * Update the policies if we have more resources reserved than what's actually in the resource
     * pool. Sort the policies by priority and decrease from their reservations
     *
     * @param resourcePoolState
     */
    private void updatePolicies(ResourcePoolService.ResourcePoolState resourcePoolState) {
        QueryTask queryTask = QueryUtil.buildPropertyQuery(
                GroupResourcePolicyService.GroupResourcePolicyState.class,
                GroupResourcePolicyService.GroupResourcePolicyState.FIELD_NAME_RESOURCE_POOL_LINK,
                resourcePoolState.documentSelfLink);
        QueryUtil.addExpandOption(queryTask);
        ServiceDocumentQuery<GroupResourcePolicyState> query = new ServiceDocumentQuery<>(
                getHost(), GroupResourcePolicyState.class);
        List<GroupResourcePolicyState> policies = new ArrayList<>();
        query.query(queryTask, (r) -> {
            if (r.hasException()) {
                logSevere(r.getException());
            } else if (r.hasResult()) {
                policies.add(r.getResult());
            } else {
                if (policies.isEmpty()) {
                    return;
                }

                long diff = policies.stream()
                        .map(q -> q.memoryLimit).reduce(0L, (a, b) -> a + b)
                        - resourcePoolState.maxMemoryBytes;
                if (diff <= 0) {
                    return;
                }

                // Sort the policies by their "normalized" priority (priority divided by the sum of
                // all
                // priorities in the group). We do that because the priorities are relative within
                // the group. E.g. Group A has two policies with priorities 1 and 2; group B has two
                // policies with priorities 100 and 200 thus the normalized priorities will be:
                // 0.33; 0.66 for A and 0.33 and 0.66 for B
                Map<String, Integer> sumOfPrioritiesByGroup = policies
                        .stream().collect(
                                Collectors.groupingBy(
                                        (GroupResourcePolicyState policy) -> getGroup(policy),
                                        Collectors.summingInt((
                                                GroupResourcePolicyState policy) -> policy.priority)));

                Comparator<GroupResourcePolicyService.GroupResourcePolicyState> comparator = (q1,
                        q2) -> Double.compare(
                                ((double) q2.priority) / sumOfPrioritiesByGroup.get(getGroup(q2)),
                                ((double) q1.priority) / sumOfPrioritiesByGroup.get(getGroup(q1)));

                policies.sort(comparator);
                Set<GroupResourcePolicyService.GroupResourcePolicyState> policiesToUpdate = new HashSet<>();
                for (GroupResourcePolicyService.GroupResourcePolicyState policy : policies) {
                    if (policy.availableMemory == 0 || policy.memoryLimit == 0) {
                        continue;
                    }

                    policiesToUpdate.add(policy);
                    if (diff > policy.availableMemory) {
                        policy.memoryLimit -= policy.availableMemory;
                        diff -= policy.availableMemory;
                    } else {
                        policy.memoryLimit -= diff;
                        break;
                    }
                }

                for (GroupResourcePolicyService.GroupResourcePolicyState policyToUpdate : policiesToUpdate) {
                    sendRequest(Operation.createPut(this, policyToUpdate.documentSelfLink)
                            .setBody(policyToUpdate));
                }

            }
        });
    }

    // Assume for now there's only one
    private static String getGroup(GroupResourcePolicyService.GroupResourcePolicyState policy) {
        if (policy.tenantLinks != null) {
            return policy.tenantLinks.get(0);
        } else {
            return "";
        }
    }

    private void updateResourcePool(ResourcePoolState resourcePoolState,
            Collection<ComputeState> computeStates) {
        if (getHost().isStopping()) {
            return;
        }
        Long memorySum = 0L;
        double totalCpuUsage = 0.0;
        long totalAvailableMemory = 0L;
        long totalNumCores = 0;
        for (ComputeState computeState : computeStates) {

            Long hostTotalMemory = PropertyUtils.getPropertyLong(computeState.customProperties,
                    ContainerHostService.DOCKER_HOST_TOTAL_MEMORY_PROP_NAME).orElse(Long.MAX_VALUE);

            if (memorySum != Long.MAX_VALUE) {
                memorySum += hostTotalMemory;
            }

            Long numCores = PropertyUtils.getPropertyLong(computeState.customProperties,
                    ContainerHostService.DOCKER_HOST_NUM_CORES_PROP_NAME).orElse(1L);

            Double hostCpuUsage = PropertyUtils.getPropertyDouble(computeState.customProperties,
                    ContainerHostService.DOCKER_HOST_CPU_USAGE_PCT_PROP_NAME).orElse(0.0);

            totalCpuUsage += numCores * hostCpuUsage;
            totalNumCores += numCores;

            // get the available memory, if missing => use the total memory, if missing => 0
            Long availableMemory = PropertyUtils
                    .getPropertyLong(computeState.customProperties,
                            ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME)
                    .orElse(PropertyUtils
                            .getPropertyLong(computeState.customProperties,
                                    ContainerHostService.DOCKER_HOST_TOTAL_MEMORY_PROP_NAME)
                            .orElse(0L));

            totalAvailableMemory += availableMemory;
        }

        // the aggregateCpuUsage is calculated:
        // (H1.cpuUsage * H1.numCores + ... + Hn.cpuUsage * Hn.numCores) / (H1.numCores + ... +
        // Hn.numCores)
        double aggregateCpuUsage = totalNumCores == 0 ? 0 : totalCpuUsage / totalNumCores;
        long resourcePoolAvailableMemory = totalAvailableMemory;

        final Long totalMemory = memorySum;

        // TODO this will not work in a multi-node setting, with consensus. There is a race.
        // Resource pool should support PATCH
        // need to do a GET and then PUT because PATCH is not implemented for these fields
        ResourcePoolState rpPutState = Utils.clone(resourcePoolState);
        if (rpPutState.customProperties == null) {
            rpPutState.customProperties = new HashMap<>();
        }
        rpPutState.customProperties
                .put(RESOURCE_POOL_CPU_USAGE_CUSTOM_PROP, Double.toString(aggregateCpuUsage));
        rpPutState.customProperties
                .put(RESOURCE_POOL_AVAILABLE_MEMORY_CUSTOM_PROP,
                        Long.toString(resourcePoolAvailableMemory));
        rpPutState.maxMemoryBytes = totalMemory;
        rpPutState.minMemoryBytes = 0;
        sendRequest(Operation.createPut(this, rpPutState.documentSelfLink)
                .setBody(rpPutState).setCompletion((op, e) -> {
                    if (e != null) {
                        logSevere("Unable to update the resource pool with link "
                                + rpPutState.documentSelfLink);
                    }
                    updatePolicies(rpPutState);
                }));
    }

    private void updateHostStats(String computeHostLink) {
        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = ContainerHostOperationType.STATS.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = UriUtilsExtended.buildUri(getHost(), computeHostLink);
        sendRequest(Operation.createPatch(this, ManagementUriParts.ADAPTER_DOCKER_HOST)
                .setBody(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning(Utils.toString(ex));
                        return;
                    }
                }));
    }

    @Override
    public void handleMaintenance(Operation post) {
        if (getProcessingStage() != ProcessingStage.AVAILABLE) {
            logFine("Skipping maintenance since service is not available: %s ", getUri());
            return;
        }

        if (DeploymentProfileConfig.getInstance().isTest()) {
            logWarning("Skipping maintenance in test mode...");
            post.complete();
            return;
        }

        logFine("Performing maintenance for: %s", getUri());
        updateHostInfoDataCollection(post);
    }

    private void updateHostInfoDataCollection(Operation maintOp) {
        List<String> descriptionLinks = new ArrayList<>();
        QueryTask q = createDockerComputeDescriptionQuery();
        ServiceDocumentQuery<ComputeDescription> query = new ServiceDocumentQuery<>(getHost(),
                ComputeDescription.class);
        query.query(q, (r) -> {
            if (getHost().isStopping()) {
                maintOp.complete();
                return;
            }
            if (r.hasException()) {
                logWarning(
                        "Exception while retrieving docker host descriptions. Error: %s",
                        (r.getException() instanceof CancellationException)
                                ? r.getException().getClass().getName()
                                : Utils.toString(r.getException()));
                maintOp.fail(r.getException());
            } else if (r.hasResult()) {
                descriptionLinks.add(r.getDocumentSelfLink());
                maintOp.complete();
            } else {
                findAllContainerHosts(descriptionLinks, maintOp);
            }
        });
    }

    private void findAllContainerHosts(Collection<String> computeDescriptionLinks,
            Operation maintOp) {
        if (computeDescriptionLinks == null || computeDescriptionLinks.isEmpty()) {
            logFine("No docker host descriptions.");
            maintOp.complete();
            return;
        }

        if (getHost().isStopping()) {
            maintOp.complete();
            return;
        }

        ResourcePoolQueryHelper rpHelper = ResourcePoolQueryHelper.create(getHost());
        rpHelper.setAdditionalQueryClausesProvider(qb -> {
            qb.addInClause(ComputeState.FIELD_NAME_DESCRIPTION_LINK, computeDescriptionLinks);
            qb.addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME, "true");
        });

        rpHelper.query(qr -> {
            if (qr.error != null) {
                logWarning("Exception while retrieving docker host. Error: %s",
                        (qr.error instanceof CancellationException)
                                ? qr.error.getClass().getName()
                                : Utils.toString(qr.error));
                maintOp.fail(qr.error);
                return;
            }

            for (ComputeState compute : qr.computesByLink.values()) {
                updateContainerHostInfo(compute.documentSelfLink, (o, error) -> {
                    // we complete maintOp here, not waiting for container update
                    maintOp.complete();
                    if (error) {
                        handleHostNotAvailable(compute);
                    } else {
                        handleHostAvailable(compute);
                    }
                }, null);

                updateContainerHostContainers(compute.documentSelfLink);
            }

            for (ResourcePoolData rpData : qr.resourcesPools.values()) {
                updateResourcePool(rpData.resourcePoolState, rpData.computeStates);
            }
        });
    }

    private QueryTask createDockerComputeDescriptionQuery() {
        QueryTask q = QueryUtil.buildQuery(ComputeDescription.class, true);
        QueryTask.Query hostTypeClause = new QueryTask.Query()
                .setTermPropertyName(QuerySpecification.buildCollectionItemName(
                        ComputeDescription.FIELD_NAME_SUPPORTED_CHILDREN))
                .setTermMatchValue(ComputeType.DOCKER_CONTAINER.name());
        q.querySpec.query.addBooleanClause(hostTypeClause);
        q.querySpec.resultLimit = ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT;
        q.documentExpirationTimeMicros = ServiceDocumentQuery.getDefaultQueryExpiration();
        return q;
    }

    private void updateContainerHostInfo(
            String documentSelfLink,
            BiConsumer<AbstractCallbackServiceHandler.CallbackServiceHandlerState, Boolean> consumer,
            ServiceTaskCallback serviceTaskCallback) {

        if (serviceTaskCallback == null) {
            startAndCreateCallbackHandlerService(consumer,
                    (callback) -> updateContainerHostInfo(documentSelfLink, consumer, callback));
            return;
        }

        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = ContainerHostOperationType.INFO.id;
        request.serviceTaskCallback = serviceTaskCallback;
        request.resourceReference = UriUtilsExtended.buildUri(getHost(), documentSelfLink);
        sendRequest(Operation.createPatch(this, ManagementUriParts.ADAPTER_DOCKER_HOST)
                .setBody(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning(Utils.toString(ex));
                        return;
                    }
                }));
    }

    private void updateContainerHostContainers(String documentSelfLink) {
        ContainerListCallback body = new ContainerListCallback();
        body.containerHostLink = documentSelfLink;
        sendRequest(Operation
                .createPatch(
                        this,
                        HostContainerListDataCollectionFactoryService.DEFAULT_HOST_CONAINER_LIST_DATA_COLLECTION_LINK)
                .setBody(body)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning(Utils.toString(ex));
                        return;
                    }
                }));
    }

    private void startAndCreateCallbackHandlerService(
            BiConsumer<AbstractCallbackServiceHandler.CallbackServiceHandlerState, Boolean> actualCallback,
            Consumer<ServiceTaskCallback> caller) {
        if (actualCallback == null) {
            caller.accept(ServiceTaskCallback.createEmpty());
            return;
        }
        AbstractCallbackServiceHandler.CallbackServiceHandlerState body = new AbstractCallbackServiceHandler.CallbackServiceHandlerState();
        String callbackLink = ManagementUriParts.REQUEST_CALLBACK_HANDLER_TASKS
                + UUID.randomUUID().toString();
        body.documentSelfLink = callbackLink;
        URI callbackUri = UriUtils.buildUri(getHost(), callbackLink);
        Operation startPost = Operation
                .createPost(callbackUri)
                .setBody(body)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failure creating callback handler. Error %s",
                                Utils.toString(e));
                        return;
                    }
                    logInfo("Callbacktask created with uri: %s, %s", callbackUri, o.getUri());
                    caller.accept(ServiceTaskCallback.create(callbackUri.toString()));
                });

        getHost().startService(startPost, new HostInfoUpdatedCallbackHandler(actualCallback));
    }

    private static class HostInfoUpdatedCallbackHandler extends
            AbstractCallbackServiceHandler {

        private final BiConsumer<CallbackServiceHandlerState, Boolean> consumer;

        public HostInfoUpdatedCallbackHandler(
                BiConsumer<CallbackServiceHandlerState, Boolean> consumer) {

            this.consumer = consumer;
        }

        @Override
        protected void handleFailedStagePatch(CallbackServiceHandlerState state) {
            ServiceErrorResponse err = state.taskInfo.failure;
            logWarning("Failed updating host info");
            if (err != null && err.stackTrace != null) {
                logFine("Task failure stack trace: %s", err.stackTrace);
                logWarning("Task failure error message: %s", err.message);
                consumer.accept(state, true);
            }
        }

        @Override
        protected void handleFinishedStagePatch(CallbackServiceHandlerState state) {
            consumer.accept(state, false);
        }
    }

    private void disableContainersForHost(String computeStateSelfLink) {
        QueryTask containerQuery = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_PARENT_LINK, computeStateSelfLink);
        ContainerState errorState = new ContainerState();
        errorState.powerState = com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState.ERROR;
        new ServiceDocumentQuery<>(getHost(), ContainerState.class).query(
                containerQuery, (r) -> {
                    if (r.hasException()) {
                        logWarning("Failed to retrieve containers for ComputeState: "
                                + computeStateSelfLink);
                        return;
                    } else if (r.hasResult()) {
                        sendRequest(Operation
                                .createPatch(this, r.getDocumentSelfLink())
                                .setBody(errorState));
                    }
                });
    }
}
