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

package com.vmware.photon.controller.model.tasks;

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_DISPLAY_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourceDescriptionService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService;

/**
 * Resource allocation task service.
 */
public class ResourceAllocationTaskService
        extends TaskService<ResourceAllocationTaskService.ResourceAllocationTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/resource-allocation-tasks";

    public static final String ID_DELIMITER_CHAR = "-";

    private static final String DEFAULT_NAME_PREFIX = "vm";

    /**
     * SubStage.
     */
    public enum SubStage {
        QUERYING_AVAILABLE_COMPUTE_RESOURCES, PROVISIONING_PHYSICAL, PROVISIONING_VM_GUESTS, PROVISIONING_CONTAINERS, FINISHED, FAILED
    }

    /**
     * Resource allocation task state.
     */
    public static class ResourceAllocationTaskState extends TaskService.TaskServiceState {

        /**
         * Mock requests are used for testing. If set, the alloc task will set the isMockRequest
         * bool in the provision task.
         */
        public boolean isMockRequest = false;

        /**
         * Specifies the allowed percentage (between 0 and 1.0) of resources requested to fail
         * before setting the task status to FAILED.
         */
        public double errorThreshold = 0;

        /**
         * Number of compute resources to provision.
         */
        public long resourceCount;

        /**
         * Specifies the resource pool that will be associated with all allocated resources.
         */
        public String resourcePoolLink;

        /**
         * Type of compute to create. Used to find Computes which can create this child.
         */
        public String computeType;

        /**
         * The compute description that defines the resource instances.
         */
        public String computeDescriptionLink;

        /**
         * Custom properties passes in for the resources to be provisioned.
         */
        public Map<String, String> customProperties;

        /**
         * Link to the resource description which overrides the public fields above. This can be
         * used to instantiate the alloc task with a template resource description.
         */
        public String resourceDescriptionLink;

        /**
         * Tracks the task's substage.
         */
        public SubStage taskSubStage;

        /**
         * List of eligible compute hosts for resource creation requests. Set by the run-time.
         */
        public List<String> parentLinks;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;
    }

    public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES
            .toMicros(10);

    public ResourceAllocationTaskService() {
        super(ResourceAllocationTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation start) {
        if (!start.hasBody()) {
            start.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ResourceAllocationTaskState state = getBody(start);

        // if we're passed a resource description link, we'll need to merge
        // state first.
        if (state.resourceDescriptionLink != null) {
            copyResourceDescription(start);
            return;
        }

        validateAndCompleteStart(start, state);
    }

    private void validateAndCompleteStart(Operation start,
            ResourceAllocationTaskState state) {
        try {
            validateState(state);
        } catch (Exception e) {
            start.fail(e);
            return;
        }

        start.complete();
        startAllocation(state);
    }

    private void startAllocation(ResourceAllocationTaskState state) {
        if (state.taskInfo.stage == TaskStage.CREATED) {
            // start task state machine:
            state.taskInfo.stage = TaskStage.STARTED;
            sendSelfPatch(TaskStage.STARTED, state.taskSubStage, null);
        } else if (state.taskInfo.stage == TaskStage.STARTED) {
            // restart from where the service was interrupted
            logWarning(() -> "restarting task...");
            handleStagePatch(state, null);
        } else {
            logFine(() -> "Service restarted");
        }
    }

    public void copyResourceDescription(Operation start) {
        ResourceAllocationTaskState state = getBody(start);
        if (state.computeType != null || state.computeDescriptionLink != null
                || state.customProperties != null) {
            start.fail(new IllegalArgumentException(
                    "ResourceDescription overrides ResourceAllocationTaskState"));
            return;
        }

        sendRequest(Operation
                .createGet(this, state.resourceDescriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                sendFailureSelfPatch(e);
                                return;
                            }
                            ResourceDescriptionService.ResourceDescription resourceDesc = o
                                    .getBody(ResourceDescriptionService.ResourceDescription.class);

                            state.computeType = resourceDesc.computeType;
                            state.computeDescriptionLink = resourceDesc.computeDescriptionLink;
                            state.customProperties = resourceDesc.customProperties;

                            validateAndCompleteStart(start, state);
                        }));
    }

    @Override
    public void handlePatch(Operation patch) {
        ResourceAllocationTaskState body = getBody(patch);
        ResourceAllocationTaskState currentState = getState(patch);

        if (validateTransitionAndUpdateState(patch, body, currentState)) {
            return;
        }

        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            handleStagePatch(currentState, null);
            break;
        case FINISHED:
            logInfo(() -> "Task is complete");
            break;
        case FAILED:
        case CANCELLED:
            break;
        default:
            break;
        }
    }

    private void handleStagePatch(ResourceAllocationTaskState currentState,
            ComputeDescriptionService.ComputeDescription desc) {
        if (desc == null) {
            getComputeDescription(currentState);
            return;
        }

        adjustStat(currentState.taskSubStage.toString(), 1);

        switch (currentState.taskSubStage) {
        case QUERYING_AVAILABLE_COMPUTE_RESOURCES:
            doQueryComputeResources(desc, currentState, null);
            break;
        case PROVISIONING_PHYSICAL:
            break;
        case PROVISIONING_VM_GUESTS:
            // intentional fall through
        case PROVISIONING_CONTAINERS:
            doComputeResourceProvisioning(currentState, desc);
            break;
        case FAILED:
            break;
        case FINISHED:
            break;
        default:
            break;
        }
    }

    /**
     * Perform a two stage query: Find all compute descriptions that meet our criteria for parent
     * hosts, then find all compute hosts that use any of those descriptions.
     *
     * @param desc
     * @param currentState
     * @param computeDescriptionLinks
     */
    private void doQueryComputeResources(
            ComputeDescriptionService.ComputeDescription desc,
            ResourceAllocationTaskState currentState,
            Collection<String> computeDescriptionLinks) {

        if (currentState.computeType == null) {
            throw new IllegalArgumentException("computeType not set");
        }

        String kind = computeDescriptionLinks == null ? Utils
                .buildKind(ComputeDescriptionService.ComputeDescription.class)
                : Utils.buildKind(ComputeService.ComputeState.class);

        QueryTask.Query kindClause = new QueryTask.Query().setTermPropertyName(
                ServiceDocument.FIELD_NAME_KIND).setTermMatchValue(kind);

        QueryTask q = new QueryTask();
        q.querySpec = new QueryTask.QuerySpecification();
        q.querySpec.query.addBooleanClause(kindClause);
        q.tenantLinks = currentState.tenantLinks;

        if (computeDescriptionLinks == null) {
            q.taskInfo.isDirect = true;
            QueryTask.Query hostTypeClause = new QueryTask.Query()
                    .setTermPropertyName(
                            QueryTask.QuerySpecification
                                    .buildCollectionItemName(
                                            ComputeDescriptionService.ComputeDescription.FIELD_NAME_SUPPORTED_CHILDREN))
                    .setTermMatchValue(currentState.computeType);
            q.querySpec.query.addBooleanClause(hostTypeClause);

            if (desc.zoneId != null && !desc.zoneId.isEmpty()) {
                // we want to make sure the computes we want to alloc are in the
                // same zone as the
                // parent
                QueryTask.Query zoneIdClause = new QueryTask.Query()
                        .setTermPropertyName(
                                ComputeDescriptionService.ComputeDescription.FIELD_NAME_ZONE_ID)
                        .setTermMatchValue(desc.zoneId);
                q.querySpec.query.addBooleanClause(zoneIdClause);
            }
        } else {
            // we do not want the host query task to be auto deleted too soon
            // and it does
            // not need to complete in-line (the POST can return before the
            // query is done)
            q.taskInfo.isDirect = false;
            q.documentExpirationTimeMicros = currentState.documentExpirationTimeMicros;

            // we want compute resources only in the same resource pool as this
            // task
            QueryTask.Query resourcePoolClause = new QueryTask.Query()
                    .setTermPropertyName(
                            ComputeService.ComputeState.FIELD_NAME_RESOURCE_POOL_LINK)
                    .setTermMatchValue(currentState.resourcePoolLink);
            q.querySpec.query.addBooleanClause(resourcePoolClause);

            // when querying the compute hosts, we want one that has one of the
            // descriptions during
            // the first query
            QueryTask.Query descriptionLinkClause = new QueryTask.Query();

            for (String cdLink : computeDescriptionLinks) {
                QueryTask.Query cdClause = new QueryTask.Query()
                        .setTermPropertyName(
                                ComputeService.ComputeState.FIELD_NAME_DESCRIPTION_LINK)
                        .setTermMatchValue(cdLink);

                cdClause.occurance = Occurance.SHOULD_OCCUR;
                descriptionLinkClause.addBooleanClause(cdClause);
                if (computeDescriptionLinks.size() == 1) {
                    // if we only have one compute host description, then all
                    // compute hosts must be
                    // using it
                    descriptionLinkClause = cdClause;
                    descriptionLinkClause.occurance = Occurance.MUST_OCCUR;
                }
            }

            q.querySpec.query.addBooleanClause(descriptionLinkClause);
        }

        Operation.CompletionHandler c = (o, ex) -> {
            if (ex != null) {
                sendFailureSelfPatch(ex);
                return;
            }
            this.processQueryResponse(o, currentState, desc,
                    computeDescriptionLinks);
        };

        Operation postQuery = Operation
                .createPost(this, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS).setBody(q)
                .setCompletion(c);

        sendRequest(postQuery);
    }

    private void processQueryResponse(Operation op,
            ResourceAllocationTaskState currentState,
            ComputeDescriptionService.ComputeDescription desc,
            Collection<String> computeDescriptionLinks) {

        QueryTask rsp = op.getBody(QueryTask.class);
        String queryLink = rsp.documentSelfLink;
        long elapsed = Utils.getNowMicrosUtc()
                - currentState.documentUpdateTimeMicros;
        if (TaskState.isFailed(rsp.taskInfo)) {
            logWarning(() -> String.format("Query failed: %s",
                    Utils.toJsonHtml(rsp.taskInfo.failure)));
            sendFailureSelfPatch(new IllegalStateException("Query task failed:"
                    + rsp.taskInfo.failure.message));
            return;
        }

        if (!TaskState.isFinished(rsp.taskInfo)) {
            logFine(() -> "Query not complete yet, retrying");
            getHost().schedule(
                    () -> {
                        getQueryResults(currentState, desc, queryLink,
                                computeDescriptionLinks);
                    }, getHost().getMaintenanceIntervalMicros(), TimeUnit.MICROSECONDS);
            return;
        }

        if (!rsp.results.documentLinks.isEmpty()) {
            if (computeDescriptionLinks == null) {
                // now do the second stage query
                doQueryComputeResources(desc, currentState,
                        rsp.results.documentLinks);
                return;
            } else {
                SubStage nextStage = determineStageFromHostType(ComputeType
                        .valueOf(currentState.computeType));
                currentState.taskSubStage = nextStage;
                currentState.parentLinks = rsp.results.documentLinks;
                sendSelfPatch(currentState);
                return;
            }
        }

        if (elapsed > getHost().getOperationTimeoutMicros()
                && rsp.results.documentLinks.isEmpty()) {
            sendFailureSelfPatch(new IllegalStateException("No compute resources available with"
                    + " poolId:" + currentState.resourcePoolLink));
            return;
        }

        logInfo(() -> "Reissuing query since no compute hosts were found");
        // Retry query. Compute hosts might be created in a different node and
        // might have not replicated yet.Redo first stage query and find all
        // compute descriptions.
        getHost().schedule(
                () -> {
                    doQueryComputeResources(desc, currentState,
                            null);
                }, getHost().getMaintenanceIntervalMicros(), TimeUnit.MICROSECONDS);
        return;
    }

    private void getQueryResults(ResourceAllocationTaskState currentState,
            ComputeDescriptionService.ComputeDescription desc,
            String queryLink, Collection<String> computeDescriptionLinks) {
        sendRequest(Operation.createGet(this, queryLink).setCompletion(
                (o, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Failure retrieving query results: %s",
                                e.toString()));
                        sendFailureSelfPatch(e);
                        return;
                    }
                    processQueryResponse(o, currentState, desc,
                            computeDescriptionLinks);
                }));
    }

    private void doComputeResourceProvisioning(
            ResourceAllocationTaskState currentState,
            ComputeDescriptionService.ComputeDescription desc) {
        Collection<String> parentLinks = currentState.parentLinks;

        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback
                .create(UriUtils.buildPublicUri(getHost(), getSelfLink()));
        callback.onSuccessFinishTask();

        // for human debugging reasons only, prefix the compute host resource id
        // with the allocation
        // task id
        String taskId = getSelfId();

        // we need to create the compute host instances to represent the
        // resources we want to
        // provision and a provision task per resource.
        // Round robin through the parent hosts and assign a parent per child
        // resource
        Iterator<String> parentIterator = null;

        logFine(() -> String.format("Creating %d provision tasks, reporting through sub task %s",
                currentState.resourceCount, callback.serviceURI));
        String name;
        if (currentState.customProperties != null
                && currentState.customProperties.get(CUSTOM_DISPLAY_NAME) != null) {
            name = currentState.customProperties.get(CUSTOM_DISPLAY_NAME);
        } else {
            name = DEFAULT_NAME_PREFIX + String.valueOf(System.currentTimeMillis());
        }

        for (int i = 0; i < currentState.resourceCount; i++) {
            if (parentIterator == null || !parentIterator.hasNext()) {
                parentIterator = parentLinks.iterator();
            }

            String computeResourceId = taskId + ID_DELIMITER_CHAR + i;

            // We pass null for disk description links and/or network
            // description links if we have
            // any to instantiate. The underlying create calls will do the
            // appropriate GET/POST to
            // create new documents with the same content but unique SELF_LINKs.
            // The last completion
            // to finish will call the create call with the arrays filled in. If
            // there are none, an
            // empty array is passed to jump out of the create calls.
            String computeName = currentState.resourceCount > 1 ? name + i : name;

            createComputeResource(currentState, desc, parentIterator.next(), computeResourceId,
                    computeName, null, null);

            // as long as you can predict the document self link of a service,
            // you can start services that depend on each other in parallel!

            String computeResourceLink = UriUtils.buildUriPath(
                    ComputeService.FACTORY_LINK, computeResourceId);
            ProvisionComputeTaskService.ProvisionComputeTaskState provisionTaskState = new ProvisionComputeTaskService.ProvisionComputeTaskState();
            provisionTaskState.computeLink = computeResourceLink;

            // supply the sub task, which keeps count of completions, to the
            // provision tasks.
            // When all provision tasks have PATCHed the sub task to FINISHED,
            // it will issue a
            // single PATCH to us, with stage = FINISHED
            provisionTaskState.serviceTaskCallback = callback;
            provisionTaskState.isMockRequest = currentState.isMockRequest;
            provisionTaskState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;
            provisionTaskState.tenantLinks = currentState.tenantLinks;
            sendRequest(Operation
                    .createPost(this, ProvisionComputeTaskService.FACTORY_LINK)
                    .setBody(provisionTaskState)
                    .setCompletion((o, e) -> {
                        if (e == null) {
                            // task will patch us when done
                            return;
                        }
                        logSevere(() -> String.format("Failure creating provisioning task: %s",
                                Utils.toString(e)));
                        // we fail on first task failure, we could
                        // in theory keep going ...
                        sendFailureSelfPatch(e);
                        return;
                    }));
        }
    }

    // Create all the dependencies, then create the compute document. createDisk
    // and createNetwork
    // will create their documents, then recurse back here with the appropriate
    // links set.
    private void createComputeResource(
            ResourceAllocationTaskState currentState, ComputeDescription cd, String parentLink,
            String computeResourceId, String name, List<String> diskLinks,
            List<String> networkLinks) {
        if (diskLinks == null) {
            createDiskResources(currentState, cd, parentLink, computeResourceId, name,
                    networkLinks);
            return;
        }

        if (networkLinks == null) {
            createNetworkResources(currentState, cd, parentLink, computeResourceId, name,
                    diskLinks);
            return;
        }

        createComputeState(currentState, parentLink, computeResourceId, name,
                diskLinks, networkLinks, cd);
    }

    private void createComputeState(ResourceAllocationTaskState currentState,
            String parentLink, String computeResourceId, String name,
            List<String> diskLinks, List<String> networkLinks, ComputeDescription cd) {
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.id = computeResourceId;
        computeState.name = name;
        computeState.parentLink = parentLink;
        computeState.type = ComputeType.VM_GUEST;
        computeState.environmentName = cd.environmentName;
        computeState.descriptionLink = currentState.computeDescriptionLink;
        computeState.resourcePoolLink = currentState.resourcePoolLink;
        computeState.diskLinks = diskLinks;
        computeState.networkInterfaceLinks = networkLinks;
        computeState.customProperties = currentState.customProperties;
        computeState.tenantLinks = currentState.tenantLinks;
        computeState.documentSelfLink = computeState.id;

        sendRequest(Operation
                .createPost(this, ComputeService.FACTORY_LINK)
                .setBody(computeState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logSevere(
                                        () -> String.format("Failure creating compute resource: %s",
                                                Utils.toString(e)));
                                sendFailureSelfPatch(e);
                                return;
                            }
                            // nothing to do
                        }));
    }

    /**
     * Create disks to attach to the compute resource. Use the disk description links to figure out
     * what type of disks to create.
     *
     * @param currentState
     * @param parentLink
     * @param computeResourceId
     */
    private void createDiskResources(ResourceAllocationTaskState currentState,
            ComputeDescription cd,
            String parentLink, String computeResourceId, String name,
            List<String> networkLinks) {

        if (cd.diskDescLinks == null || cd.diskDescLinks.isEmpty()) {
            createComputeResource(currentState, cd, parentLink, computeResourceId, name,
                    new ArrayList<>(), networkLinks);
            return;
        }

        DeferredResult<List<String>> result = DeferredResult.allOf(
                cd.diskDescLinks.stream()
                        .map(link -> {
                            Operation op = Operation.createGet(this, link);
                            return this.sendWithDeferredResult(op, DiskState.class);
                        })
                        .map(dr -> dr.thenCompose(d -> {
                            String link = d.documentSelfLink;
                            // create a new disk based off the template but use a
                            // unique ID
                            d.id = UUID.randomUUID().toString();
                            d.documentSelfLink = null;
                            d.tenantLinks = currentState.tenantLinks;
                            if (d.customProperties == null) {
                                d.customProperties = new HashMap<>();
                            }
                            d.descriptionLink = link;

                            return this.sendWithDeferredResult(
                                    Operation.createPost(this, DiskService.FACTORY_LINK).setBody(d),
                                    DiskState.class);
                        }))
                        .map(dsr -> dsr
                                .thenApply(ds -> ds.documentSelfLink))
                        .collect(Collectors.toList()));

        result.whenComplete((diskLinks, e) -> {
            if (e != null) {
                logWarning(() -> String.format("Failure creating disk: %s", e.toString()));
                this.sendFailureSelfPatch(e);
                return;
            }
            // we have created all the disks. Now create the compute host
            // resource
            createComputeResource(currentState, cd, parentLink, computeResourceId, name,
                    diskLinks, networkLinks);
        });
    }

    private void createNetworkResources(ResourceAllocationTaskState currentState,
            ComputeDescription cd, String parentLink,
            String computeResourceId, String name, List<String> diskLinks) {
        if (cd.networkInterfaceDescLinks == null
                || cd.networkInterfaceDescLinks.isEmpty()) {
            createComputeResource(currentState, cd, parentLink, computeResourceId, name,
                    diskLinks, new ArrayList<>());
            return;
        }

        // get all network descriptions first, then create new network interfaces using the
        // description/template
        List<DeferredResult<String>> drs = cd.networkInterfaceDescLinks.stream()
                .map(nicDescLink -> sendWithDeferredResult(Operation.createGet(this, nicDescLink),
                        NetworkInterfaceDescription.class)
                                .thenApply(nid -> newNicStateFromDescription(currentState, nid))
                                .thenCompose(nic -> sendWithDeferredResult(Operation
                                        .createPost(this, NetworkInterfaceService.FACTORY_LINK)
                                        .setBody(nic), NetworkInterfaceState.class))
                                .thenApply(nic -> nic.documentSelfLink))
                .collect(Collectors.toList());

        DeferredResult.allOf(drs).whenComplete((all, e) -> {
            if (e != null) {
                logWarning(() -> String.format("Failure creating network interfaces: %s",
                        e.toString()));
                this.sendFailureSelfPatch(e);
                return;
            }
            // we have created all the networks. Now create the compute host
            // resource
            createComputeResource(currentState, cd, parentLink, computeResourceId, name,
                    diskLinks, all);
        });

    }

    private NetworkInterfaceState newNicStateFromDescription(
            ResourceAllocationTaskState state,
            NetworkInterfaceDescription nid) {
        NetworkInterfaceState nic = new NetworkInterfaceState();
        nic.id = UUID.randomUUID().toString();
        nic.documentSelfLink = nic.id;
        nic.name = nid.name;
        nic.deviceIndex = nid.deviceIndex;
        nic.address = nid.address;
        nic.networkLink = nid.networkLink;
        nic.subnetLink = nid.subnetLink;
        nic.networkInterfaceDescriptionLink = nid.documentSelfLink;
        nic.securityGroupLinks = nid.securityGroupLinks;
        nic.groupLinks = nid.groupLinks;
        nic.tagLinks = nid.tagLinks;
        nic.tenantLinks = state.tenantLinks;
        nic.endpointLink = nid.endpointLink;
        nic.regionId = nid.regionId;
        nic.customProperties = nid.customProperties;

        return nic;
    }

    @Override
    public String getSelfId() {
        return getSelfLink().substring(
                getSelfLink().lastIndexOf(UriUtils.URI_PATH_CHAR) + 1);
    }

    private SubStage determineStageFromHostType(ComputeType resourceType) {
        switch (resourceType) {
        case DOCKER_CONTAINER:
            return SubStage.PROVISIONING_CONTAINERS;
        case OS_ON_PHYSICAL:
            return SubStage.PROVISIONING_PHYSICAL;
        case VM_GUEST:
            return SubStage.PROVISIONING_VM_GUESTS;
        case VM_HOST:
            return SubStage.PROVISIONING_PHYSICAL;
        default:
            logSevere(() -> String.format("Invalid host type %s, it can not be provisioned",
                    resourceType));
            // this should never happen, due to upstream logic
            return SubStage.FAILED;
        }
    }

    public void getComputeDescription(ResourceAllocationTaskState currentState) {
        sendRequest(Operation
                .createGet(this, currentState.computeDescriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                sendFailureSelfPatch(e);
                                return;
                            }
                            handleStagePatch(
                                    currentState,
                                    o.getBody(ComputeDescriptionService.ComputeDescription.class));
                        }));
    }

    private boolean validateTransitionAndUpdateState(Operation patch,
            ResourceAllocationTaskState body,
            ResourceAllocationTaskState currentState) {

        TaskStage currentStage = currentState.taskInfo.stage;
        SubStage currentSubStage = currentState.taskSubStage;
        boolean isUpdate = false;

        if (body.parentLinks != null) {
            currentState.parentLinks = body.parentLinks;
            isUpdate = true;
        }

        if (body.taskInfo == null || body.taskInfo.stage == null) {
            if (isUpdate) {
                patch.complete();
                return true;
            }
            patch.fail(new IllegalArgumentException(
                    "taskInfo and stage are required"));
            return true;
        }

        if (currentStage.ordinal() > body.taskInfo.stage.ordinal()) {
            patch.fail(new IllegalArgumentException(
                    "stage can not move backwards:" + body.taskInfo.stage));
            return true;
        }

        if (body.taskInfo.failure != null) {
            logWarning(() -> String.format("Referer %s is patching us to failure: %s",
                    patch.getReferer(), Utils.toJsonHtml(body.taskInfo.failure)));
            currentState.taskInfo.failure = body.taskInfo.failure;
            currentState.taskInfo.stage = body.taskInfo.stage;
            currentState.taskSubStage = SubStage.FAILED;
            return false;
        }

        if (TaskState.isFinished(body.taskInfo)) {
            currentState.taskInfo.stage = body.taskInfo.stage;
            currentState.taskSubStage = SubStage.FINISHED;
            return false;
        }

        if (currentSubStage.ordinal() > body.taskSubStage.ordinal()) {
            patch.fail(new IllegalArgumentException(
                    "subStage can not move backwards:" + body.taskSubStage));
            return true;
        }

        currentState.taskInfo.stage = body.taskInfo.stage;
        currentState.taskSubStage = body.taskSubStage;

        logFine(() -> String.format("Moving from %s(%s) to %s(%s)", currentSubStage, currentStage,
                body.taskSubStage, body.taskInfo.stage));

        return false;
    }

    private void sendFailureSelfPatch(Throwable e) {
        sendSelfPatch(TaskStage.FAILED, SubStage.FAILED, e);
    }

    private void sendSelfPatch(TaskStage stage, SubStage subStage, Throwable e) {
        ResourceAllocationTaskState body = new ResourceAllocationTaskState();
        body.taskInfo = new TaskState();
        if (e == null) {
            body.taskInfo.stage = stage;
            body.taskSubStage = subStage;
        } else {
            body.taskInfo.stage = TaskStage.FAILED;
            body.taskInfo.failure = Utils.toServiceErrorResponse(e);
            logWarning(() -> String.format("Patching to failed: %s", Utils.toString(e)));
        }

        sendSelfPatch(body);
    }

    public static void validateState(ResourceAllocationTaskState state) {
        if (state.resourcePoolLink == null) {
            throw new IllegalArgumentException("resourcePoolLink is required");
        }

        if (state.taskInfo == null || state.taskInfo.stage == null) {
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskStage.CREATED;
        }

        if (state.computeDescriptionLink == null) {
            throw new IllegalArgumentException("computeDescriptionLink is required");
        }

        if (state.resourceCount <= 0) {
            throw new IllegalArgumentException("resourceCount is required");
        }

        if (state.errorThreshold > 1.0 || state.errorThreshold < 0) {
            throw new IllegalArgumentException("errorThreshold can only be between 0 and 1.0");
        }

        if (state.taskSubStage == null) {
            state.taskSubStage = SubStage.QUERYING_AVAILABLE_COMPUTE_RESOURCES;
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + DEFAULT_TIMEOUT_MICROS;
        }
    }
}
