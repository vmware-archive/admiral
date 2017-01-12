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

package com.vmware.admiral.request.compute;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINKS;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.request.allocation.filter.AffinityFilters;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.compute.ComputePlacementSelectionTaskService.ComputePlacementSelectionTaskState.SubStage;
import com.vmware.admiral.request.compute.allocation.filter.FilterContext;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.util.ResourcePoolQueryHelper;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Task implementing compute placement selection for provisioning a given number of instances of a
 * {@link ComputeDescription}.
 */
public class ComputePlacementSelectionTaskService extends
        AbstractTaskStatefulService<ComputePlacementSelectionTaskService.ComputePlacementSelectionTaskState, ComputePlacementSelectionTaskService.ComputePlacementSelectionTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_COMPUTE_PLACEMENT_TASKS;
    public static final String DISPLAY_NAME = "Compute Placement Selection";

    // cached description of the requested compute resource
    private volatile ComputeDescription computeDescription;

    /**
     * Task parameters and internal state.
     */
    public static class ComputePlacementSelectionTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputePlacementSelectionTaskState.SubStage> {

        public static enum SubStage {
            CREATED, FILTER, COMPLETED, ERROR;
        }

        @Documentation(description = "The description that defines the requested resource.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED, LINK }, indexing = STORE_ONLY)
        public String computeDescriptionLink;

        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public long resourceCount;

        @Documentation(description = "The resource pool to be used for this placement.")
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public List<String> resourcePoolLinks;

        @Documentation(description = "Set by the task as result of the selection algorithm filters."
                + " The number of selected computes matches the given resourceCount.")
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL,
                LINKS }, indexing = STORE_ONLY)
        public Collection<HostSelection> selectedComputePlacementHosts;

        @ServiceDocument.Documentation(description = "(Required) The overall contextId of this"
                + "request (could be the same across multiple request - composite allocation)")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public String contextId;

        /**
         * HostLink to HostSelection map. It is passed from CREATED to FILTER stage in order to
         * track result from hosts query.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Map<String, HostSelection> hostSelectionMap;
    }

    /**
     * Task output.
     */
    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        /**
         * Set by the task as result of the selection algorithm filters. The number of selected
         * computes matches the given {@code resourceCount}.
         */
        Collection<HostSelection> selectedComputePlacementHosts;
    }

    /**
     * Constructs a new instance.
     */
    public ComputePlacementSelectionTaskService() {
        super(ComputePlacementSelectionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(ComputePlacementSelectionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            selectPlacement(state);
            break;
        case FILTER:
            filter(state, computeDescription);
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
    protected void validateStateOnStart(ComputePlacementSelectionTaskState state) {
        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.", "request.resource-count.zero");
        }
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ComputePlacementSelectionTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.selectedComputePlacementHosts = state.selectedComputePlacementHosts;
        return finishedResponse;
    }

    private void selectPlacement(ComputePlacementSelectionTaskState state) {

        Query.Builder queryBuilder = Query.Builder.create()
                .addKindFieldClause(ComputeDescription.class)
                .addCollectionItemClause(
                        ComputeDescription.FIELD_NAME_SUPPORTED_CHILDREN,
                        ComputeType.VM_GUEST.toString());

        QueryTask queryTask = QueryTask.Builder.create().setQuery(queryBuilder.build()).build();
        QueryUtil.addExpandOption(queryTask);

        final List<String> computeDescriptionLinks = new ArrayList<>();
        Map<String, Long> hostToAvailableMemory = new LinkedHashMap<>();
        ServiceDocumentQuery<ComputeDescription> query = new ServiceDocumentQuery<ComputeDescription>(
                getHost(), ComputeDescription.class);
        query.query(queryTask, (r) -> {
            if (r.hasException()) {
                failTask("Error querying for placement compute description.", r.getException());
            } else if (r.hasResult()) {
                computeDescriptionLinks.add(r.getDocumentSelfLink());
                hostToAvailableMemory.put(r.getDocumentSelfLink(), r.getResult().totalMemoryBytes);
            } else {
                if (computeDescriptionLinks.isEmpty()) {
                    failTask(null, new LocalizableValidationException(
                            "No ComputeDescription found for compute placement",
                            "request.compute.placement.compute-description.missing"));
                    return;
                }
                proceedComputeSelection(state, computeDescriptionLinks, hostToAvailableMemory);
            }
        });
    }

    private void proceedComputeSelection(ComputePlacementSelectionTaskState state,
            Collection<String> computeDescriptionLinks, Map<String, Long> hostToAvailableMemory) {

        ResourcePoolQueryHelper helper = ResourcePoolQueryHelper.createForResourcePools(getHost(),
                state.resourcePoolLinks);
        helper.setExpandComputes(true);
        helper.setAdditionalQueryClausesProvider(qb -> {
            qb.addInClause(ComputeState.FIELD_NAME_DESCRIPTION_LINK, computeDescriptionLinks)
                    .addFieldClause(ComputeState.FIELD_NAME_POWER_STATE, PowerState.ON.toString())
                    .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.VM_HOST);
        });

        helper.query(qr -> {
            if (qr.error != null) {
                failTask("Error querying for compute placements.", qr.error);
                return;
            }

            if (qr.computesByLink.isEmpty()) {
                failTask(null, new LocalizableValidationException(
                        "No powered-on compute placement candidates found in "
                                + "placement zones: " + state.resourcePoolLinks,
                                "request.compute.placement.powered-on.placements.unavailable", state.resourcePoolLinks));
                return;
            }

            Map<String, HostSelection> hostSelectionMap = buildHostSelectionMap(qr,
                    hostToAvailableMemory);

            proceedTo(SubStage.FILTER, s -> {
                s.hostSelectionMap = hostSelectionMap;
            });
        });
    }

    private void filter(ComputePlacementSelectionTaskState state, ComputeDescription desc) {
        if (desc == null) {
            getComputeDescription(state,
                    (description) -> this.filter(state, description));
            return;
        }

        AffinityFilters affinityFilters = AffinityFilters.build(this.getHost(), desc);

        FilterContext filterContext = FilterContext.from(state);

        filter(state, filterContext, state.hostSelectionMap,
                affinityFilters.getQueue());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void filter(ComputePlacementSelectionTaskState state,
            FilterContext filterContext,
            final Map<String, HostSelection> hostSelectionMap,
            final Queue<HostSelectionFilter> filters) {

        if (isNoSelection(hostSelectionMap)) {
            failTask(null, new LocalizableValidationException(
                    "No compute placement candidates found in placement zones: "
                            + state.resourcePoolLinks,
                            "request.compute.placement.placements.unavailable", state.resourcePoolLinks));
            return;
        }

        final HostSelectionFilter<FilterContext> filter = filters.poll();
        if (filter == null) {
            selection(state, hostSelectionMap);
        } else {
            filter.filter(filterContext, hostSelectionMap, (filteredHostSelectionMap, e) -> {
                if (e != null) {
                    if (e instanceof HostSelectionFilter.HostSelectionFilterException) {
                        failTask("Allocation filter error: " + e.getMessage(), null);
                    } else {
                        failTask("Allocation filter exception", e);
                    }
                    return;
                }
                filter(state, filterContext, filteredHostSelectionMap, filters);
            });
        }
    }

    private static boolean isNoSelection(Map<String, HostSelection> filteredHostSelectionMap) {
        return filteredHostSelectionMap == null || filteredHostSelectionMap.isEmpty();
    }

    private Map<String, HostSelection> buildHostSelectionMap(
            ResourcePoolQueryHelper.QueryResult rpQueryResult,
            Map<String, Long> hostToAvailableMemory) {
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
            if (hostToAvailableMemory.containsKey(computeState.descriptionLink)) {
                hostSelection.availableMemory = hostToAvailableMemory
                        .get(computeState.descriptionLink);
            }
            initHostSelectionMap.put(hostSelection.hostLink, hostSelection);
        }
        return initHostSelectionMap;
    }

    private void selection(final ComputePlacementSelectionTaskState state,
            final Map<String, HostSelection> filtered) {
        if (filtered.isEmpty()) {
            failTask("No compute placements found", null);
            return;
        }
        ArrayList<HostSelection> selectedComputeHosts = new ArrayList<>(filtered.values());
        Collections.shuffle(selectedComputeHosts);

        int initialSize = selectedComputeHosts.size();
        int diff = (int) (state.resourceCount - initialSize);
        if (diff > 0) {
            /*
             * Cycle the list of compute selections until we reach resourceCount number of entries,
             * i.e. if we have 3 computes [A, B, C] and resourceCount is 7 we will have [A, B, C, A,
             * B, C, A]
             */
            for (int i = 0; i < diff / initialSize; ++i) {
                selectedComputeHosts.addAll(selectedComputeHosts.subList(0, initialSize));
            }

            selectedComputeHosts.addAll(selectedComputeHosts.subList(0, diff % initialSize));
        }

        logInfo("The following computes selected for provisioning %d resources: %s",
                state.resourceCount,
                selectedComputeHosts.stream().map(hs -> hs.hostLink).collect(Collectors.toList()));

        proceedTo(SubStage.COMPLETED, s -> {
            s.selectedComputePlacementHosts = selectedComputeHosts;
        });
    }

    private void getComputeDescription(ComputePlacementSelectionTaskState state,
            Consumer<ComputeDescription> callbackFunction) {
        if (computeDescription != null) {
            callbackFunction.accept(computeDescription);
            return;
        }
        sendRequest(Operation.createGet(this, state.computeDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving compute description "
                                + state.computeDescriptionLink, e);
                        return;
                    }

                    ComputeDescription desc = o.getBody(ComputeDescription.class);
                    this.computeDescription = desc;
                    callbackFunction.accept(desc);
                }));
    }
}
