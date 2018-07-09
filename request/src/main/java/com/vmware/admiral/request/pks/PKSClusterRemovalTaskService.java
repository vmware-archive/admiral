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
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_ENDPOINT_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_DELETE;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_STATE_FAILED;
import static com.vmware.admiral.request.pks.PKSClusterRemovalTaskService.PKSClusterRemovalTaskState.SubStage;
import static com.vmware.admiral.request.pks.PKSClusterRemovalTaskService.PKSClusterRemovalTaskState.SubStage.INSTANCES_REMOVED;
import static com.vmware.admiral.request.pks.PKSClusterRemovalTaskService.PKSClusterRemovalTaskState.SubStage.INSTANCES_REMOVING;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.pks.PKSException;
import com.vmware.admiral.adapter.pks.PKSOperationType;
import com.vmware.admiral.adapter.pks.entities.PKSCluster;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;

/**
 * Task implementing removal of PKS clusters.
 */
public class PKSClusterRemovalTaskService extends
        AbstractTaskStatefulService<PKSClusterRemovalTaskService.PKSClusterRemovalTaskState, SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_REMOVE_PKS_CLUSTER_TASK;

    public static final String DISPLAY_NAME = "PKS Cluster Removal";

    public static final int POLL_PKS_ENDPOINT_INTERVAL_MICROS = Integer.getInteger(
            "com.vmware.admiral.request.pks.poll.interval.sec", 60) * 1000 * 1000;
    private static final int MAX_POLL_FAILURES = Integer.getInteger(
            "com.vmware.admiral.request.pks.poll.max.failures", 10);

    public static class PKSClusterRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<PKSClusterRemovalTaskState.SubStage> {

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

        /**
         * remove cluster dto state without destroying actual PKS cluster
         */
        public boolean removeOnly;

        /**
         * If this is a cleanup removal task, it will try to delete the pks cluster state even if it
         * fails to delete an actual k8s cluster on the pks endpoint
         */
        public boolean cleanupRemoval;

        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public int failureCounter = 0;

        public enum SubStage {
            CREATED,
            INSTANCES_REMOVING,
            INSTANCES_REMOVED,
            REMOVING_RESOURCE_STATES,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(INSTANCES_REMOVING, REMOVING_RESOURCE_STATES));
        }
    }

    public PKSClusterRemovalTaskService() {
        super(PKSClusterRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(POLL_PKS_ENDPOINT_INTERVAL_MICROS);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(PKSClusterRemovalTaskState task)
            throws IllegalArgumentException {
        AssertUtil.assertNotNull(task, "task");
        AssertUtil.assertNotEmpty(task.resourceLink, "resourceLink");
    }

    @Override
    protected void handleStartedStagePatch(PKSClusterRemovalTaskState task) {
        switch (task.taskSubStage) {
        case CREATED:
            destroyPKSClusterInstance(task);
            break;
        case INSTANCES_REMOVING:
            break;
        case INSTANCES_REMOVED:
            removeResources(task);
            break;
        case REMOVING_RESOURCE_STATES:
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
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> task) {
        TaskStatusState statusTask = super.fromTask(task);
        statusTask.name = ((PKSClusterRemovalTaskState) task).clusterName;
        return statusTask;
    }

    @Override
    public void handlePeriodicMaintenance(Operation post) {
        sendRequest(Operation.createGet(getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logSevere("Failed to load PKSClusterRemovalTask %s, reason: %s",
                                getSelfLink(), ex.getMessage());
                        post.fail(new Exception("Failed to load PKSClusterRemovalTask state"));
                    } else {
                        PKSClusterRemovalTaskState task =
                                op.getBody(PKSClusterRemovalTaskState.class);
                        if (task != null && task.taskSubStage == SubStage.INSTANCES_REMOVING) {
                            checkDeletionStatus(task, null);
                        }
                        post.complete();
                    }
                }));
    }

    private void destroyPKSClusterInstance(PKSClusterRemovalTaskState task) {
        if (task.removeOnly) {
            logInfo("Skip destroy PKS cluster since the removeOnly flag is set");

            // skip the actual removal of pks cluster through the adapter
            proceedTo(INSTANCES_REMOVED);
            return;
        }

        if (task.clusterName == null || task.endpointLink == null) {
            fetchClusterAndUpdateTask(task);
            return;
        }

        logInfo("Starting destroy of pks cluster: %s endpoint: %s", task.resourceLink,
                task.endpointLink);

        AdapterRequest request = createAdapterRequest(task, PKSOperationType.DELETE_CLUSTER);
        Operation.createPatch(getHost(), ManagementUriParts.ADAPTER_PKS)
                .setBodyNoCloning(request)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (task.cleanupRemoval) {
                            logInfo("PKS cluster destroy call failed but cleanup removal is set"
                                            + " to TRUE, going to remove cluster state. Error: %s",
                                    e.getMessage());
                            proceedTo(INSTANCES_REMOVED);
                            return;
                        }
                        failTask("PKS cluster delete failed, endpoint: " + task.endpointLink, e);
                        return;
                    }
                    logInfo("PKS cluster delete started on %s", task.endpointLink);
                    proceedTo(SubStage.INSTANCES_REMOVING);
                })
                .sendWith(this);
    }

    private void removeResources(PKSClusterRemovalTaskState task) {
        try {
            sendRequest(Operation
                    .createDelete(this, task.resourceLink)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failed deleting PKS cluster: " + task.resourceLink, e);
                            return;
                        }
                        proceedTo(SubStage.COMPLETED);
                    }));
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting resources", e);
        }
    }

    private AdapterRequest createAdapterRequest(PKSClusterRemovalTaskState task,
            PKSOperationType operationType) {
        AdapterRequest adapterRequest = new AdapterRequest();

        adapterRequest.operationTypeId = operationType.id;
        adapterRequest.resourceReference = URI.create(task.endpointLink);
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        adapterRequest.customProperties = new HashMap<>(2);
        adapterRequest.customProperties.put(PKS_CLUSTER_NAME_PROP_NAME, task.clusterName);
        return adapterRequest;
    }

    private void checkDeletionStatus(PKSClusterRemovalTaskState task, PKSCluster cluster) {
        // get pks cluster, verify status and proceed to COMPLETED or FAILED if cluster is
        // deleted or failed. If it is still being deleted do nothing

        if (cluster == null) {
            checkPKSClusterStatus(task);
            return;
        }

        if (PKS_LAST_ACTION_DELETE.equals(cluster.lastAction)) {
            if (PKS_LAST_ACTION_STATE_FAILED.equals(cluster.lastActionState)) {
                if (task.cleanupRemoval) {
                    logInfo("PKS cluster destroy call failed but cleanup removal is set to TRUE,"
                                    + " going to remove cluster state. Error: %s",
                            cluster.lastActionDescription);
                    proceedTo(INSTANCES_REMOVED);
                    return;
                }
                Exception e = new IllegalStateException(cluster.lastActionDescription);
                failTask("PKS cluster delete failed, endpoint: " + task.endpointLink, e);
            }
        }
    }

    private void checkPKSClusterStatus(PKSClusterRemovalTaskState task) {
        AdapterRequest adapterRequest = createAdapterRequest(task, PKSOperationType.GET_CLUSTER);

        Operation.createPatch(getHost(), ManagementUriParts.ADAPTER_PKS)
                .setBodyNoCloning(adapterRequest)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (checkForNotFoundException(e)) {
                            logInfo("PKS cluster %s has been deleted, GET status code is 404",
                                    task.clusterName);
                            proceedTo(INSTANCES_REMOVED);
                            return;
                        }
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
                        proceedTo(INSTANCES_REMOVING, t -> t.failureCounter = task.failureCounter);
                        return;
                    }
                    PKSCluster pksCluster = o.getBody(PKSCluster.class);
                    if (pksCluster != null) {
                        checkDeletionStatus(task, pksCluster);
                        if (task.failureCounter > 0) {
                            // reset the failure counter
                            proceedTo(INSTANCES_REMOVING, t -> t.failureCounter = 0);
                        }
                    }
                })
                .sendWith(this);
    }

    private boolean checkForNotFoundException(Throwable e) {
        if (e != null && e.getCause() instanceof PKSException) {
            PKSException pksException = (PKSException) e.getCause();
            return pksException.getErrorCode() == Operation.STATUS_CODE_NOT_FOUND;
        }
        return false;
    }

    private void fetchClusterAndUpdateTask(PKSClusterRemovalTaskState task) {
        getClusterDto(task.resourceLink, clusterDto -> {
            if (clusterDto == null || clusterDto.nodes == null || clusterDto.nodes.size() < 1) {
                // nothing to delete from PKS
                logInfo("Cluster %s does not contain PKS cluster name, skipping adapter request",
                        task.resourceLink);
                proceedTo(INSTANCES_REMOVED);
                return;
            }
            ComputeService.ComputeState host = clusterDto.nodes.values().iterator().next();

            if (host.customProperties != null) {
                task.clusterName = host.customProperties.get(PKS_CLUSTER_NAME_PROP_NAME);
                task.endpointLink = host.customProperties.get(PKS_ENDPOINT_PROP_NAME);
            }

            if (task.clusterName == null || task.endpointLink == null) {
                logWarning("Cannot get PKS cluster name / endpoint for %s, skipping destroying PKS"
                        + " cluster and proceed to remove states", task.resourceLink);
                proceedTo(INSTANCES_REMOVED);
                return;
            }
            proceedTo(SubStage.CREATED, t -> {
                t.clusterName = task.clusterName;
                t.endpointLink = task.endpointLink;
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
                    consumer.accept(o.getBody(ClusterService.ClusterDto.class));
                })
                .sendWith(this);
    }

}
