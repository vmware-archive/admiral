/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.pks;

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_STATUS_RESIZING_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_STATE_FAILED;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_STATE_SUCCEEDED;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_UPDATE;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_WORKER_INSTANCES_FIELD;
import static com.vmware.admiral.service.common.DefaultSubStage.COMPLETED;
import static com.vmware.admiral.service.common.DefaultSubStage.CREATED;
import static com.vmware.admiral.service.common.DefaultSubStage.PROCESSING;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.pks.PKSOperationType;
import com.vmware.admiral.adapter.pks.entities.PKSCluster;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceStateMapUpdateRequest;

public class PKSClusterResizeTaskService extends
        AbstractTaskStatefulService<PKSClusterResizeTaskService.PKSClusterResizeTaskState, DefaultSubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_RESIZE_PKS_CLUSTER_TASK;

    public static final String DISPLAY_NAME = "PKS Cluster Resize";

    public static final int POLL_PKS_ENDPOINT_INTERVAL_MICROS = Integer.getInteger(
            "com.vmware.admiral.request.pks.poll.interval.sec", 60) * 1000 * 1000;
    private static final int MAX_POLL_FAILURES = Integer.getInteger(
            "com.vmware.admiral.request.pks.poll.max.failures", 10);

    public static class PKSClusterResizeTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<DefaultSubStage> {

        /**
         * (Required) PKS endpoint self link.
         */
        @PropertyOptions(usage = { REQUIRED, SINGLE_ASSIGNMENT, AUTO_MERGE_IF_NOT_NULL },
                indexing = STORE_ONLY)
        public String endpointLink;
        /**
         * (Required) The resource on which the given operation will be applied
         */
        @PropertyOptions(usage = { REQUIRED, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String resourceLink;

        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String clusterName;

        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String hostLink;

        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public int failureCounter = 0;
    }

    public PKSClusterResizeTaskService() {
        super(PKSClusterResizeTaskState.class, DefaultSubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(POLL_PKS_ENDPOINT_INTERVAL_MICROS);
        super.transientSubStages = DefaultSubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(PKSClusterResizeTaskState task)
            throws IllegalArgumentException {
        AssertUtil.assertNotNull(task, "task");
        AssertUtil.assertNotEmpty(task.endpointLink, "endpointLink");
        AssertUtil.assertNotEmpty(task.resourceLink, "resourceLink");
        AssertUtil.assertNotNull(task.customProperties, "customProperties");
        AssertUtil.assertNotEmpty(task.getCustomProperty(PKS_CLUSTER_NAME_PROP_NAME),
                "customProperties [" + PKS_CLUSTER_NAME_PROP_NAME + "]");
        AssertUtil.assertNotEmpty(task.getCustomProperty(PKS_WORKER_INSTANCES_FIELD),
                "customProperties [" + PKS_WORKER_INSTANCES_FIELD + "]");
    }

    @Override
    protected void handleStartedStagePatch(PKSClusterResizeTaskState task) {
        switch (task.taskSubStage) {
        case CREATED:
            process(task);
            break;
        case PROCESSING:
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
    public void handlePeriodicMaintenance(Operation post) {
        sendRequest(Operation.createGet(getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logSevere("Failed to load PKSClusterResizeTask %s, reason: %s",
                                getSelfLink(), ex.getMessage());
                        post.fail(new Exception("Failed to load PKSClusterResizeTask state"));
                    } else {
                        PKSClusterResizeTaskState task =
                                op.getBody(PKSClusterResizeTaskState.class);
                        if (task != null && task.taskSubStage == PROCESSING) {
                            checkResizeStatus(task, null);
                        }
                        post.complete();
                    }
                }));
    }

    private void checkResizeStatus(PKSClusterResizeTaskState task, PKSCluster cluster) {
        // get PKS cluster, verify status and proceed to COMPLETED or FAILED if resizing
        // completed or failed. If it is still resizing do nothing.

        if (cluster == null) {
            checkPKSClusterStatus(task);
            return;
        }

        if (PKS_LAST_ACTION_UPDATE.equals(cluster.lastAction)) {
            if (PKS_LAST_ACTION_STATE_SUCCEEDED.equals(cluster.lastActionState)) {
                resumeHost(task);
            } else if (PKS_LAST_ACTION_STATE_FAILED.equals(cluster.lastActionState)) {
                failTask("PKS cluster update failed", new Exception("Update PKS cluster failed: " +
                        cluster.lastActionDescription));
            }
        } else {
            String message = "Unexpected last action returned: " + cluster.lastAction;
            failTask(message, new Exception(message));
        }
    }

    private void resumeHost(PKSClusterResizeTaskState task) {
        ComputeState patch = new ComputeState();
        patch.powerState = PowerState.ON;
        Operation powerStatePatch = Operation.createPatch(this, task.hostLink)
                .setReferer(getUri())
                .setBodyNoCloning(patch);

        Map<String, Collection<Object>> keysToRemove = new HashMap<>();
        keysToRemove.put(ResourcePoolState.FIELD_NAME_CUSTOM_PROPERTIES,
                Collections.singleton(PKS_CLUSTER_STATUS_RESIZING_PROP_NAME));
        ServiceStateMapUpdateRequest x = ServiceStateMapUpdateRequest.create(null, keysToRemove);
        Operation removeResizingFlag = Operation.createPatch(this, task.hostLink)
                .setReferer(getUri())
                .setBodyNoCloning(x);

        OperationJoin.create(powerStatePatch, removeResizingFlag)
                .setCompletion((ops, failures) -> {
                    if (failures != null && !failures.isEmpty()) {
                        failTask("Failed to resume cluster host", failures.values().iterator().next());
                        return;
                    }
                    proceedTo(COMPLETED);
                }).sendWith(getHost());
    }

    private void checkPKSClusterStatus(PKSClusterResizeTaskState task) {
        AdapterRequest adapterRequest = createAdapterRequest(task, PKSOperationType.GET_CLUSTER);

        Operation.createPatch(getHost(), ManagementUriParts.ADAPTER_PKS)
                .setBodyNoCloning(adapterRequest)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("failed getting pks cluster %s from %s", task.clusterName,
                                task.endpointLink);
                        if (task.failureCounter++ >= MAX_POLL_FAILURES) {
                            LocalizableValidationException le = new LocalizableValidationException(
                                    "max failures reached connecting to " + task.endpointLink,
                                    "compute.add.host.connection.error", "pks", e.getMessage());
                            failTask("pks adapter request failed for: " + task.endpointLink
                                    + " cluster: ", le);
                            return;
                        }
                        proceedTo(PROCESSING, t -> t.failureCounter = task.failureCounter);
                        return;
                    }
                    PKSCluster pksCluster = o.getBody(PKSCluster.class);
                    checkResizeStatus(task, pksCluster);
                })
                .sendWith(this);
    }

    private void process(PKSClusterResizeTaskState task) {
        if (task.clusterName == null) {
            fetchPKSClusterName(task);
            return;
        }

        logInfo("Starting resize of pks cluster: %s endpoint: %s", task.resourceLink,
                task.endpointLink);

        AdapterRequest request = createAdapterRequest(task, PKSOperationType.RESIZE_CLUSTER);
        Operation.createPatch(getHost(), ManagementUriParts.ADAPTER_PKS)
                .setBodyNoCloning(request)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("PKS cluster resize failed, endpoint: " + task.endpointLink, e);
                        return;
                    }
                    logInfo("PKS cluster resize started on %s", task.endpointLink);

                    suspendHost(task.hostLink, () -> proceedTo(PROCESSING));
                })
                .sendWith(this);
    }

    private void suspendHost(String hostLink, Runnable callback) {
        ComputeState patch = new ComputeState();
        patch.powerState = PowerState.SUSPEND;
        patch.customProperties = new HashMap<>();
        patch.customProperties.put(PKS_CLUSTER_STATUS_RESIZING_PROP_NAME, Boolean.TRUE.toString());

        Operation.createPatch(this, hostLink)
        .setBody(patch)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Error suspending host " + hostLink, e);
                        return;
                    }
                    callback.run();
                }).sendWith(this);
    }

    private AdapterRequest createAdapterRequest(PKSClusterResizeTaskState task,
            PKSOperationType operationType) {
        AdapterRequest adapterRequest = new AdapterRequest();

        adapterRequest.operationTypeId = operationType.id;
        adapterRequest.resourceReference = URI.create(task.endpointLink);
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        adapterRequest.customProperties = task.customProperties;
        return adapterRequest;
    }


    private void fetchPKSClusterName(PKSClusterResizeTaskState task) {
        getClusterDto(task.resourceLink, clusterDto -> {
            ComputeService.ComputeState host = clusterDto.nodes.values().iterator().next();

            if (host.customProperties != null) {
                task.clusterName = host.customProperties.get(PKS_CLUSTER_NAME_PROP_NAME);
            }
            task.hostLink = host.documentSelfLink;

            if (task.clusterName == null) {
                Exception err = new Exception("Cannot get PKS cluster name");
                logWarning("Cannot get PKS cluster name for %s, skipping resizing PKS cluster",
                        task.resourceLink);
                failTask(err.getMessage(), err);
                return;
            }
            proceedTo(CREATED, t -> {
                t.clusterName = task.clusterName;
                t.hostLink = task.hostLink;
            });
        });
    }

    private void getClusterDto(String clusterLink, Consumer<ClusterService.ClusterDto> consumer) {
        Operation.createGet(this, clusterLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failed to get cluster " + clusterLink, e);
                        return;
                    }

                    ClusterDto clusterDto = o.getBody(ClusterService.ClusterDto.class);

                    if (clusterDto == null || clusterDto.nodes == null || clusterDto.nodes.size() < 1) {
                        logInfo("Cluster %s does not contain any hosts, task failed %s",
                                clusterLink, getSelfLink());
                        failTask("Cluster does not container any hosts " + clusterLink, null);
                        return;
                    }

                    consumer.accept(clusterDto);
                })
                .sendWith(this);
    }
}
