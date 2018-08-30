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
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_UUID_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_ENDPOINT_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_CREATE;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_STATE_FAILED;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_STATE_SUCCEEDED;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_MASTER_HOST_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_PLAN_NAME_FIELD;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.compute.ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME;
import static com.vmware.admiral.compute.cluster.ClusterService.CLUSTER_NAME_CUSTOM_PROP;
import static com.vmware.admiral.service.common.DefaultSubStage.COMPLETED;
import static com.vmware.admiral.service.common.DefaultSubStage.PROCESSING;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.pks.PKSOperationType;
import com.vmware.admiral.adapter.pks.entities.PKSCluster;
import com.vmware.admiral.adapter.pks.service.PKSClusterConfigService;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.TenantLinksUtil;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterStatus;
import com.vmware.admiral.compute.pks.PKSEndpointService.Endpoint;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.Utils;

/**
 * Task implementing the provisioning of a kubernetes composite component.
 */
public class PKSClusterProvisioningTaskService extends
        AbstractTaskStatefulService<PKSClusterProvisioningTaskService.PKSProvisioningTaskState,
                DefaultSubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_PKS_CLUSTER_TASK;

    public static final String DISPLAY_NAME = "PKS Cluster Provision";

    public static final int POLL_PKS_ENDPOINT_INTERVAL_MICROS = Integer.getInteger(
            "com.vmware.admiral.request.pks.poll.interval.sec", 60) * 1000 * 1000;
    private static final int MAX_POLL_FAILURES = Integer.getInteger(
            "com.vmware.admiral.request.pks.poll.max.failures", 10);

    static final String INVALID_PLAN_SELECTION_MESSAGE_FORMAT = "Plan selection [%s] is not valid in the context of PKS cluster provisioning task [%s]";

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    public static class PKSProvisioningTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<DefaultSubStage> {

        /**
         * (Required) PKS endpoint self link.
         */
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public String endpointLink;

        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

        /**
         * If set to true, use kubernetes master IP instead of master hostname to
         * connect to cluster.
         */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public boolean preferMasterIP;

        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public int failureCounter = 0;
    }

    public PKSClusterProvisioningTaskService() {
        super(PKSProvisioningTaskState.class, DefaultSubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(POLL_PKS_ENDPOINT_INTERVAL_MICROS);
        super.transientSubStages = DefaultSubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(PKSProvisioningTaskState task)
            throws IllegalArgumentException {
        AssertUtil.assertNotNull(task, "task");
        AssertUtil.assertNotNull(task.customProperties, "customProperties");

        AssertUtil.assertNotEmpty(task.getCustomProperty(PKS_CLUSTER_NAME_PROP_NAME),
                "customProperties [" + PKS_CLUSTER_NAME_PROP_NAME + "]");

        AssertUtil.assertNotEmpty(task.getCustomProperty(PKS_PLAN_NAME_FIELD),
                "customProperties [" + PKS_PLAN_NAME_FIELD + "]");

        AssertUtil.assertNotEmpty(task.getCustomProperty(PKS_MASTER_HOST_FIELD),
                "customProperties [" + PKS_MASTER_HOST_FIELD + "]");

        AssertUtil.assertNotNullOrEmpty(task.endpointLink, "endpointLink");

        AssertUtil.assertNotNull(task.tenantLinks, "tenantLinks");
        AssertUtil.assertNotEmpty(TenantLinksUtil.getProjectAndGroupLinks(task.tenantLinks),
                "tenantLinks(project/group links only)");
    }

    @Override
    protected void handleStartedStagePatch(PKSProvisioningTaskState task) {
        switch (task.taskSubStage) {
        case CREATED:
            validatePlanSelection(task, () -> process(task));
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

    private void validatePlanSelection(PKSProvisioningTaskState taskState, Runnable nextAction) {
        final String planName = taskState.getCustomProperty(PKS_PLAN_NAME_FIELD);
        Operation getEndpoint = Operation.createGet(getHost(), taskState.endpointLink)
                .setReferer(getSelfLink());

        getHost().sendWithDeferredResult(getEndpoint, Endpoint.class)
                .thenAccept(endpoint -> doValidatePlanSelection(planName, taskState.tenantLinks,
                        endpoint))
                .thenAccept(v -> nextAction.run())
                .exceptionally(ex -> {
                    failTask(String.format("Plan selection validation failed: %s",
                            Utils.toString(ex)), ex);
                    return null;
                });
    }

    void doValidatePlanSelection(String planName, Collection<String> taskTenantLinks,
            Endpoint endpoint) {
        Set<String> projectLinks = TenantLinksUtil.getProjectAndGroupLinks(taskTenantLinks);

        boolean validSelection = projectLinks.stream()
                .anyMatch(projectLink -> {
                    return endpoint.planAssignments.get(projectLink).plans.contains(planName);
                });

        if (!validSelection) {
            throw new IllegalArgumentException(String.format(
                    INVALID_PLAN_SELECTION_MESSAGE_FORMAT,
                    planName, getSelfId()));
        }

        logFine("Valid plan selection [%s] for PKS cluster provisioning task [%s]", planName,
                getSelfId());
    }

    @Override
    protected TaskStatusState fromTask(TaskServiceDocument<DefaultSubStage> state) {
        TaskStatusState statusTask = super.fromTask(state);
        statusTask.name = state.getCustomProperty(PKS_CLUSTER_NAME_PROP_NAME);
        return statusTask;
    }

    @Override
    public void handlePeriodicMaintenance(Operation post) {
        // complete the maintenance operation first
        post.complete();

        sendRequest(Operation.createGet(getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logSevere("Failed to load PKSClusterProvisioningTask %s, reason: %s",
                                getSelfLink(), ex.getMessage());
                    } else {
                        PKSProvisioningTaskState task = op.getBody(PKSProvisioningTaskState.class);
                        if (task != null && task.taskSubStage == PROCESSING) {
                            checkProvisioningStatus(task, null);
                        }
                    }
                }));
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            PKSProvisioningTaskState task) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(task.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = task.resourceLinks;
        return finishedResponse;
    }

    @Override
    protected ServiceTaskCallbackResponse getFailedCallbackResponse(PKSProvisioningTaskState task) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(task.serviceTaskCallback.getFailedResponse(task.taskInfo.failure));
        finishedResponse.resourceLinks = task.resourceLinks;
        return finishedResponse;
    }

    @Override
    public void handleExpiration(PKSProvisioningTaskState task) {
        super.handleExpiration(task);
        if (task.taskSubStage == PROCESSING) {
            logWarning("Task %s has expired, notifying parent task.", task.documentSelfLink);
            Exception e = new Exception("PKS cluster was provisioned, but is not reachable. Check"
                    + " network connectivity.");
            task.taskInfo.failure = ServiceErrorResponse.create(e, 0);
            notifyCallerService(task);
        }
    }

    private void process(PKSProvisioningTaskState task) {
        assertNotNull(task, "task");
        sendCreateClusterRequest(task, pksCluster -> createInitialClusterState(task, pksCluster));
    }

    private void sendCreateClusterRequest(PKSProvisioningTaskState task,
            Consumer<PKSCluster> consumer) {
        AdapterRequest adapterRequest = createAdapterRequest(task, PKSOperationType.CREATE_CLUSTER);

        Operation.createPatch(getHost(), ManagementUriParts.ADAPTER_PKS)
                .setBodyNoCloning(adapterRequest)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("PKS AdapterRequest failed, endpoint: " + task.endpointLink, e);
                        return;
                    }
                    logInfo("PKS cluster provisioning started on %s", task.endpointLink);
                    consumer.accept(o.getBody(PKSCluster.class));
                })
                .sendWith(this);
    }

    private void createInitialClusterState(PKSProvisioningTaskState task, PKSCluster pksCluster) {
        ContainerHostSpec clusterSpec = new ContainerHostSpec();
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.tenantLinks = task.tenantLinks;
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(CLUSTER_NAME_CUSTOM_PROP,
                task.getCustomProperty(PKS_CLUSTER_NAME_PROP_NAME));
        computeState.customProperties.put(CONTAINER_HOST_TYPE_PROP_NAME,
                ClusterService.ClusterType.KUBERNETES.name());

        computeState.customProperties.put(HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());
        computeState.customProperties.put(PKS_ENDPOINT_PROP_NAME, task.endpointLink);
        computeState.customProperties.put(PKS_CLUSTER_UUID_PROP_NAME, pksCluster.uuid);
        computeState.customProperties.put(PKS_PLAN_NAME_FIELD,
                task.getCustomProperty(PKS_PLAN_NAME_FIELD));
        computeState.customProperties.put(ClusterService.CREATE_EMPTY_CLUSTER_PROP, "true");
        computeState.customProperties.put(ClusterService.ENFORCED_CLUSTER_STATUS_PROP,
                ClusterService.ClusterStatus.PROVISIONING.name());

        clusterSpec.hostState = computeState;

        Operation.createPost(getHost(), ClusterService.SELF_LINK)
                .setBodyNoCloning(clusterSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Error creating cluster state: %s", Utils.toString(e));
                        failTask("Error creating cluster state", e);
                        return;
                    }
                    ClusterService.ClusterDto c = o.getBody(ClusterService.ClusterDto.class);
                    proceedTo(PROCESSING, t -> {
                        t.resourceLinks = new HashSet<>();
                        t.resourceLinks.add(c.documentSelfLink);
                    });
                }).sendWith(this);
    }

    private void getClusterAdapterRequest(PKSProvisioningTaskState task) {
        AdapterRequest adapterRequest = createAdapterRequest(task, PKSOperationType.GET_CLUSTER);

        Operation.createPatch(getHost(), ManagementUriParts.ADAPTER_PKS)
                .setBodyNoCloning(adapterRequest)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("failed getting PKS cluster %s from %s",
                                task.getCustomProperty(PKS_CLUSTER_NAME_PROP_NAME),
                                task.endpointLink);
                        if (task.failureCounter++ >= MAX_POLL_FAILURES) {
                            LocalizableValidationException le = new LocalizableValidationException(
                                    "max failures reached connecting to " + task.endpointLink,
                                    "compute.add.host.connection.error", "PKS", e.getMessage());
                            failTask("PKS adapter request failed for: " + task.endpointLink
                                    + " cluster: ", le);
                            return;
                        }
                        proceedTo(PROCESSING, t -> t.failureCounter = task.failureCounter);
                        return;
                    }
                    PKSCluster pksCluster = o.getBody(PKSCluster.class);
                    if (pksCluster != null) {
                        checkProvisioningStatus(task, pksCluster);
                        if (task.failureCounter > 0) {
                            proceedTo(PROCESSING, t -> t.failureCounter = 0);
                        }
                    }
                })
                .sendWith(this);
    }

    private AdapterRequest createAdapterRequest(PKSProvisioningTaskState task,
            PKSOperationType operation) {
        AdapterRequest adapterRequest = new AdapterRequest();

        adapterRequest.operationTypeId = operation.id;
        adapterRequest.resourceReference = URI.create(task.endpointLink);
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        adapterRequest.customProperties = task.customProperties;
        return adapterRequest;
    }

    private void checkProvisioningStatus(PKSProvisioningTaskState task, PKSCluster cluster) {
        // get pks cluster , verify status and proceed to COMPLETED or FAILED if cluster is
        // provisioned or failed. If it is still provisioning do nothing

        if (cluster == null) {
            getClusterAdapterRequest(task);
            return;
        }

        if (PKS_LAST_ACTION_CREATE.equals(cluster.lastAction)) {
            if (PKS_LAST_ACTION_STATE_SUCCEEDED.equals(cluster.lastActionState)) {
                updateProvisionedCluster(task, cluster);
            } else if (PKS_LAST_ACTION_STATE_FAILED.equals(cluster.lastActionState)) {
                failTask("PKS cluster create failed", new Exception("Create PKS cluster failed: " +
                        cluster.lastActionDescription));
            }
        } else {
            String message = "Unexpected last action returned: " + cluster.lastAction;
            failTask(message, new Exception(message));
        }
    }

    private void updateProvisionedCluster(PKSProvisioningTaskState task, PKSCluster cluster) {
        logInfo("Cluster %s is provisioned on endpoint %s, registering it", cluster.name,
                task.endpointLink);
        String clusterSelfLink = task.resourceLinks.iterator().next();

        PKSClusterConfigService.AddClusterRequest request =
                new PKSClusterConfigService.AddClusterRequest();
        request.existingClusterLink = clusterSelfLink;
        request.endpointLink = task.endpointLink;
        request.cluster = cluster;
        request.preferMasterIP = task.preferMasterIP;
        request.tenantLinks = task.tenantLinks;

        Operation
                .createPost(this, PKSClusterConfigService.SELF_LINK)
                .setBodyNoCloning(request)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        // in case of connection exception do nothing, will try to contact the
                        // cluster later, else fail the provision request
                        if (!isConnectionException(e) && !isLocalizableValidationException(e)) {
                            failTask("Failed to add PKS cluster: " + e.getMessage(), e);
                        }
                        markClusterUnreachable(task);
                        return;
                    }
                    proceedTo(COMPLETED);
                })
                .sendWith(this);
    }

    private void markClusterUnreachable(PKSProvisioningTaskState task) {
        String clusterLink = task.resourceLinks.iterator().next();

        ClusterDto patch = new ClusterDto();
        patch.status = ClusterStatus.UNREACHABLE;

        Operation.createPatch(this, clusterLink)
        .setBody(patch)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error setting cluster status for [%s] to [%s]: %s",
                                clusterLink, ClusterStatus.UNREACHABLE.toString(),
                                Utils.toString(e));
                    }
                }).sendWith(this);
    }

    /**
     * Check if exception is about unable to connect to remote server - e.g. UnknownHostException,
     * ConnectException, NoRouteToHostException, etc.
     *
     * @param e exception
     */
    private boolean isConnectionException(Throwable e) {
        return e instanceof IOException || e.getCause() instanceof IOException;
    }

    /**
     * Check if exception is about failure to import ssl certificate and not about
     * core xenon failures
     *
     * @param e exception
     */
    private boolean isLocalizableValidationException(Throwable e) {
        return e instanceof LocalizableValidationException
                || e.getCause() instanceof LocalizableValidationException;
    }
}
