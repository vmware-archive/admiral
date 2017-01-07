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

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.request.compute.ComputeNetworkProvisionTaskService.ComputeNetworkProvisionTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Task implementing the provisioning of a compute network.
 */
public class ComputeNetworkProvisionTaskService
        extends
        AbstractTaskStatefulService<ComputeNetworkProvisionTaskService.ComputeNetworkProvisionTaskState, ComputeNetworkProvisionTaskService.ComputeNetworkProvisionTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_COMPUTE_NETWORK_TASKS;

    public static final String DISPLAY_NAME = "Compute Network Provision";

    public static final String COMPOSITE_CUSTOM_PROP_NAME_PREFIX = "__cmp_";

    // cached network description
    private volatile ComputeNetworkDescription networkDescription;

    public static class ComputeNetworkProvisionTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeNetworkProvisionTaskState.SubStage> {

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
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Long resourceCount;

        /** (Required) Links to already allocated resources that are going to be provisioned. */
        @Documentation(description = "Links to already allocated resources that are going to be provisioned.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public Set<String> resourceLinks;

        // Service use fields:

        /** (Internal) Reference to the adapter that will fulfill the provision request. */
        @Documentation(description = "Reference to the adapter that will fulfill the provision request.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public URI instanceAdapterReference;

    }

    public ComputeNetworkProvisionTaskService() {
        super(ComputeNetworkProvisionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(ComputeNetworkProvisionTaskState state) {
        state.resourceCount = (long) state.resourceLinks.size();

        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
        }
    }

    @Override
    protected void handleStartedStagePatch(ComputeNetworkProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            provisionNetworks(state);
            break;
        case PROVISIONING:
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

    private void provisionNetworks(ComputeNetworkProvisionTaskState state) {

        logInfo("Provision request for %s networks", state.resourceCount);

        getComputeNetworkDescription(state, (networkDescription) -> {
            if (Boolean.TRUE.equals(networkDescription.external)) {
                getNetworkByName(state, networkDescription.name, (networkState) -> {
                    updateComputeNetworkState(state, networkState, () -> {
                        proceedTo(SubStage.COMPLETED, s -> {
                            // Small workaround to get the actual self link for discovered
                            // networks...
                            s.customProperties = new HashMap<>();
                            s.customProperties.put("__externalNetworkSelfLink",
                                    networkState.documentSelfLink);
                        });
                    });
                });
            } else {
                // TODO: Do network provisioning
                proceedTo(SubStage.COMPLETED);
            }
        });

        proceedTo(SubStage.PROVISIONING);
    }

    private void getNetworkByName(ComputeNetworkProvisionTaskState state, String networkName,
            Consumer<NetworkState> callback) {

        Query query = Query.Builder.create()
                .addKindFieldClause(NetworkState.class)
                .addFieldClause(NetworkState.FIELD_NAME_NAME, networkName)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setQuery(query)
                .build();
        queryTask.tenantLinks = state.tenantLinks;

        QueryUtils.startQueryTask(this, queryTask)
                .whenComplete((qrt, e) -> {
                    if (e != null) {
                        failTask("Failed to query for active networks by name '"
                                + networkName + "'!", e);
                        return;
                    }

                    if (qrt.results.documents != null && qrt.results.documents.size() == 1) {
                        NetworkState networkState = Utils.fromJson(
                                qrt.results.documents.values().iterator().next(),
                                NetworkState.class);
                        callback.accept(networkState);
                        return;
                    }
                    failTask(qrt.results.documents.size()
                            + " active network(s) found by name '" + networkName
                            + "'!", null);

                });
    }

    private void updateComputeNetworkState(ComputeNetworkProvisionTaskState state,
            NetworkState currentNetworkState, Runnable callbackFunction) {

        NetworkState patch = new NetworkState();

        String contextId;
        if (state.customProperties != null && (contextId = state.customProperties
                .get(FIELD_NAME_CONTEXT_ID_KEY)) != null) {

            String currentValue = currentNetworkState.customProperties
                    .get(COMPOSITE_CUSTOM_PROP_NAME_PREFIX + contextId);
            if (currentValue == null) {
                patch.customProperties = new HashMap<>();
                patch.customProperties.put(COMPOSITE_CUSTOM_PROP_NAME_PREFIX + contextId,
                        UriUtils.buildUriPath(
                                CompositeComponentFactoryService.SELF_LINK, contextId));
            }
        } else {
            callbackFunction.run();
            return;
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

    private void getComputeNetworkDescription(ComputeNetworkProvisionTaskState state,
            Consumer<ComputeNetworkDescription> callbackFunction) {
        if (networkDescription != null) {
            callbackFunction.accept(networkDescription);
            return;
        }

        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                failTask("Failure retrieving compute network description state",
                                        e);
                                return;
                            }

                            ComputeNetworkDescription desc = o
                                    .getBody(ComputeNetworkDescription.class);
                            this.networkDescription = desc;
                            callbackFunction.accept(desc);
                        }));
    }
}
