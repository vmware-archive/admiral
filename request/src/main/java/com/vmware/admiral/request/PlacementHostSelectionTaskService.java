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

package com.vmware.admiral.request;

import static com.vmware.admiral.common.util.PropertyUtils.getPropertyLong;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState.SubStage;
import com.vmware.admiral.request.allocation.filter.AffinityFilters;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelectionFilterException;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.tasks.helpers.ResourcePoolQueryHelper;
import com.vmware.photon.controller.model.tasks.helpers.ResourcePoolQueryHelper.QueryResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

/**
 * Task implementing the placement selection of a given compute host.
 */
public class PlacementHostSelectionTaskService
        extends
        AbstractTaskStatefulService<PlacementHostSelectionTaskService.PlacementHostSelectionTaskState, PlacementHostSelectionTaskService.PlacementHostSelectionTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_PLACEMENT_TASKS;
    public static final String DISPLAY_NAME = "Host Selection";
    private static final int QUERY_RETRY_COUNT = Integer.getInteger(
            "com.vmware.admiral.service.placement.query.retries", 2);

    // cached component description
    private volatile ReservationComponentDescription description;

    public static class PlacementHostSelectionTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<PlacementHostSelectionTaskState.SubStage> {

        public static enum SubStage {
            CREATED, FILTER, COMPLETED, ERROR;
        }

        /** (Required) The description that defines the requested resource. */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        /** (Required) Type of resource to create. */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public String resourceType;

        /** (Required) Number of resources to provision. */
        public long resourceCount;

        /** (Required) The resourcePool to be used for this provisioning allocation */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public List<String> resourcePoolLinks;

        /**
         * (Required) The overall contextId of this request (could be the same across multiple
         * request - composite allocation)
         */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public String contextId;

        // Internal service properties:

        /** Set by the Task as result of the selection algorithm filters. */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Collection<HostSelection> hostSelections;

        /**
         * HostLink to HostSelection map. It is passed from CREATED to FILTER stage in order to
         * track result from hosts query.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Map<String, HostSelection> hostSelectionMap;
    }

    public PlacementHostSelectionTaskService() {
        super(PlacementHostSelectionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(PlacementHostSelectionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            selectBasedOnDescAndResourcePool(state, description, QUERY_RETRY_COUNT);
            break;
        case FILTER:
            selection(state, null);
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

    @Override
    protected void validateStateOnStart(PlacementHostSelectionTaskState state) {
        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.",
                    "request.resource-count.zero");
        }
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            PlacementHostSelectionTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.hostSelections = state.hostSelections;
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Collection<HostSelection> hostSelections;
        Map<String, HostSelection> hostSelectionMap;
    }

    private void selectBasedOnDescAndResourcePool(PlacementHostSelectionTaskState state,
            ReservationComponentDescription desc, int retries) {
        if (desc == null) {
            getDescription(state,
                    (contDesc) -> this
                            .selectBasedOnDescAndResourcePool(state, contDesc, retries));
            return;
        }

        QueryTask q = QueryUtil.buildQuery(ComputeDescription.class, false);
        QueryTask.Query hostTypeClause = new QueryTask.Query()
                .setTermPropertyName(QuerySpecification.buildCollectionItemName(
                        ComputeDescription.FIELD_NAME_SUPPORTED_CHILDREN))
                .setTermMatchValue(state.resourceType);
        q.querySpec.query.addBooleanClause(hostTypeClause);

        String zoneId = desc.getCommonDescription().zoneId;
        if (zoneId != null && !zoneId.isEmpty()) {
            QueryTask.Query zoneIdClause = new QueryTask.Query()
                    .setTermPropertyName(ComputeDescription.FIELD_NAME_ZONE_ID)
                    .setTermMatchValue(zoneId);
            q.querySpec.query.addBooleanClause(zoneIdClause);
        }

        final List<String> computeDescriptionLinks = new ArrayList<>();
        ServiceDocumentQuery<ComputeDescription> query = new ServiceDocumentQuery<ComputeDescription>(
                getHost(), ComputeDescription.class);
        query.query(q, (r) -> {
            if (r.hasException()) {
                failTask("Error querying for placement compute description.", r.getException());
            } else if (r.hasResult()) {
                computeDescriptionLinks.add(r.getDocumentSelfLink());
            } else {
                if (computeDescriptionLinks.isEmpty()) {
                    failTask(null, new LocalizableValidationException(
                            "Available host ComputeDescription not found supporting the type: "
                                    + state.resourceType,
                            "request.placement.compute-description.unsupported",
                            state.resourceType));
                    return;
                }
                proceedComputeSelection(state, desc, computeDescriptionLinks, retries);
            }
        });
    }

    private void proceedComputeSelection(PlacementHostSelectionTaskState state,
            ReservationComponentDescription desc,
            Collection<String> computeDescriptionLinks, int retries) {

        ResourcePoolQueryHelper helper = ResourcePoolQueryHelper.createForResourcePools(getHost(),
                state.resourcePoolLinks);
        helper.setExpandComputes(true);
        helper.setAdditionalQueryClausesProvider(qb -> {
            qb.addInClause(ComputeState.FIELD_NAME_DESCRIPTION_LINK, computeDescriptionLinks)
                    .addFieldClause(ComputeState.FIELD_NAME_POWER_STATE, PowerState.ON.toString());
        });

        helper.query(qr -> {
            if (qr.error != null) {
                failTask("Error querying for placement compute hosts.", qr.error);
                return;
            }

            if (qr.computesByLink.isEmpty()) {
                if (retries > 0) {
                    logWarning("No powered-on container hosts found in placement zones %s matching"
                                    + " descriptions %s, retrying (%d left)...",
                            state.resourcePoolLinks, computeDescriptionLinks, retries - 1);
                    getHost().schedule(
                            () -> selectBasedOnDescAndResourcePool(state, desc, retries - 1),
                            QueryUtil.QUERY_RETRY_INTERVAL_MILLIS,
                            TimeUnit.MILLISECONDS);
                } else {
                    failTask(null, new LocalizableValidationException(
                            "No powered-on container hosts found in placement zones: "
                                    + state.resourcePoolLinks,
                            "request.placement.hosts.missing", state.resourcePoolLinks));
                }
                return;
            }

            Map<String, HostSelection> initHostSelectionMap = buildHostSelectionMap(qr);

            proceedTo(SubStage.FILTER, s -> {
                s.hostSelectionMap = initHostSelectionMap;
            });

        });
    }

    private Map<String, HostSelection> buildHostSelectionMap(QueryResult rpQueryResult) {
        Collection<ComputeState> computes = rpQueryResult.computesByLink.values();
        final Map<String, HostSelection> initHostSelectionMap = new LinkedHashMap<>(
                computes.size());
        for (ComputeState computeState : computes) {
            final HostSelection hostSelection = new HostSelection();
            hostSelection.hostLink = computeState.documentSelfLink;
            hostSelection.resourcePoolLinks = rpQueryResult.rpLinksByComputeLink
                    .get(computeState.documentSelfLink);
            hostSelection.deploymentPolicyLink = computeState.customProperties
                    .get(ContainerHostService.CUSTOM_PROPERTY_DEPLOYMENT_POLICY);
            hostSelection.availableMemory = getPropertyLong(
                    computeState.customProperties,
                    ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME)
                            .orElse(Long.MAX_VALUE);
            hostSelection.clusterStore = computeState.customProperties
                    .get(ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME);
            hostSelection.plugins = computeState.customProperties
                    .get(ContainerHostService.DOCKER_HOST_PLUGINS_PROP_NAME);
            hostSelection.name = computeState.name != null ? computeState.name : "N/A";
            hostSelection.hostType = ContainerHostUtil.getDeclaredContainerHostType(computeState);

            initHostSelectionMap.put(hostSelection.hostLink, hostSelection);
        }
        return initHostSelectionMap;
    }

    private void selection(final PlacementHostSelectionTaskState state,
            final ReservationComponentDescription description) {
        if (description == null) {
            getDescription(state,
                    (desc) -> this.selection(state, desc));
            return;
        }

        Map<String, HostSelection> filteredByHostType = filterHostsByType(state, description,
                state.hostSelectionMap);

        Map<String, HostSelection> filteredByMemory = filterHostsByMemory(description,
                filteredByHostType);

        try {
            final AffinityFilters filters = AffinityFilters.build(getHost(),
                    description.getServiceDocument());
            filter(state, filteredByMemory, filters.getQueue());
        } catch (Exception e) {
            failTask(null, e);
        }

    }

    private List<ContainerHostType> getSupportedHostTypes(
            PlacementHostSelectionTaskState state, ComponentDescription desc) {
        ServiceDocument serviceDocument = desc.getServiceDocument();
        if (serviceDocument instanceof CompositeDescription) {
            CompositeDescription cd = (CompositeDescription) serviceDocument;

            List<ContainerHostType> commonSupportedHostTypes = null;

            for (String descriptionLink : cd.descriptionLinks) {
                ComponentMeta meta = CompositeComponentRegistry
                        .metaByDescriptionLink(descriptionLink);
                ResourceType resourceType = ResourceType.fromName(meta.resourceType);
                List<ContainerHostType> supportedHostTypes = ContainerHostUtil
                        .getContainerHostTypesForResourceType(resourceType);
                if (commonSupportedHostTypes == null) {
                    commonSupportedHostTypes = supportedHostTypes;
                } else {
                    AssertUtil.assertTrue(commonSupportedHostTypes.equals(supportedHostTypes),
                            "supported host types are not the same for different components");
                }
            }

            return commonSupportedHostTypes;
        } else {
            ResourceType resourceType = ResourceType.fromName(state.resourceType);
            return ContainerHostUtil.getContainerHostTypesForResourceType(resourceType);
        }
    }

    private Map<String, HostSelection> filterHostsByType(
            PlacementHostSelectionTaskState state,
            ComponentDescription desc,
            Map<String, HostSelection> initHostSelectionMap) {

        List<ContainerHostType> supportedHostTypes = getSupportedHostTypes(state, desc);
        AssertUtil.assertNotEmpty(supportedHostTypes, "supportedHostTypes");

        Map<String, HostSelection> map = initHostSelectionMap.entrySet().stream()
                .filter(e -> supportedHostTypes.contains(e.getValue().hostType))
                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));

        return map;
    }

    private Map<String, HostSelection> filterHostsByMemory(
            ReservationComponentDescription desc,
            Map<String, HostSelection> initHostSelectionMap) {

        Long memoryLimit = desc.getCommonDescription().memoryLimit;

        if (memoryLimit == null) {
            return initHostSelectionMap;
        }

        return initHostSelectionMap.entrySet().stream()
                .filter(e -> e.getValue().availableMemory == null ||
                        e.getValue().availableMemory >= memoryLimit)
                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void filter(final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> hostSelectionMap,
            final Queue<HostSelectionFilter> filters) {
        if (isNoSelection(hostSelectionMap)) {
            failTask(null, new LocalizableValidationException("No suitable host available",
                    "request.placement.compute.missing"));
            return;
        } else {
            final HostSelectionFilter filter = filters.poll();
            if (filter == null) {
                complete(state, hostSelectionMap);
            } else {
                filter.filter(state, hostSelectionMap, (filteredHostSelectionMap, e) -> {
                    if (e != null) {
                        if (e instanceof HostSelectionFilterException) {
                            failTask("Allocation Filter Error: " + e.getMessage(), null);
                        } else {
                            failTask("Allocation Filter Exception", e);
                        }
                        return;
                    }
                    filter(state, filteredHostSelectionMap, filters);
                });
            }
        }
    }

    private void complete(final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> hostSelectionMap) {
        if (hostSelectionMap.isEmpty()) {
            failTask("No compute hostLinks selected", null);
            return;
        }
        ArrayList<HostSelection> hostSelections = new ArrayList<>(hostSelectionMap.values());
        Collections.shuffle(hostSelections);

        int initialSize = hostSelections.size();
        int diff = (int) (state.resourceCount - initialSize);
        if (diff > 0) {
            /*
             * Cycle the list of host selections until we reach resourceCount number of entries i.e.
             * if we have 3 hosts: [A, B, C] and resourceCount is 7 we will have [A, B, C, A, B, C,
             * A]
             */
            for (int i = 0; i < diff / initialSize; ++i) {
                hostSelections.addAll(hostSelections.subList(0, initialSize));
            }

            hostSelections.addAll(hostSelections.subList(0, diff % initialSize));
        }

        proceedTo(SubStage.COMPLETED, s -> {
            s.hostSelections = hostSelections;
        });
    }

    private void getDescription(PlacementHostSelectionTaskState state,
            Consumer<ReservationComponentDescription> callbackFunction) {
        if (description != null) {
            callbackFunction.accept(description);
            return;
        }
        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving description state", e);
                        return;
                    }

                    ReservationComponentDescription cd = new ReservationComponentDescription();

                    if (state.resourceDescriptionLink
                            .startsWith(CompositeDescriptionFactoryService.SELF_LINK)) {
                        cd.updateServiceDocument(o.getBody(CompositeDescription.class));
                    } else {
                        ComponentMeta metaByDescriptionLink = CompositeComponentRegistry
                                .metaByDescriptionLink(state.resourceDescriptionLink);
                        cd.updateServiceDocument(o.getBody(metaByDescriptionLink.descriptionClass));
                    }

                    this.description = cd;
                    callbackFunction.accept(cd);
                }));
    }

    private boolean isNoSelection(Map<String, HostSelection> filteredHostSelectionMap) {
        return filteredHostSelectionMap == null || filteredHostSelectionMap.isEmpty();
    }
}
