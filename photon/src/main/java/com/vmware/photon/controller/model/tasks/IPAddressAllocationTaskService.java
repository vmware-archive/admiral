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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.IPAddressService;
import com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState;
import com.vmware.photon.controller.model.resources.ResourceUtils;
import com.vmware.photon.controller.model.resources.SubnetRangeService.SubnetRangeState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.support.IPVersion;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.util.IpHelper;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task implementing the allocation of an IP address.
 */
public class IPAddressAllocationTaskService extends
        TaskService<IPAddressAllocationTaskService.IPAddressAllocationTaskState> {

    public static final String FACTORY_LINK = UriPaths.TASKS + "/ip-address-allocation-tasks";
    public static final String ID_SEPARATOR = "_";

    /**
     * Service context that is created for passing subnet and ip range information between async
     * calls. Used only during allocation.
     */
    public static class IPAddressAllocationContext {
        // Subnet state document
        public SubnetState subnetState;

        // Enumeration of subnet range states
        public Iterator<SubnetRangeState> subnetRangeStatesIterator;

        // Resource requesting IP address
        public String connectedResourceLink;
    }

    /**
     * Class that represents the result of IP address allocation.
     */
    public static class IPAddressAllocationTaskResult
            extends ServiceTaskCallbackResponse<IPAddressAllocationTaskState.SubStage> {
        public IPAddressAllocationTaskResult(TaskState.TaskStage taskStage, Object taskSubStage,
                ServiceErrorResponse failure) {
            super(taskStage, taskSubStage, new HashMap<>(), failure);
        }

        // Resource link of the IP address being allocated.
        public List<String> ipAddressLinks;

        // IP addresses being allocated.
        public List<String> ipAddresses;

        // IP ranges from which IP address is allocated.
        public List<String> subnetRangeLinks;

        /**
         * Connected resource that is associated with IP address(es)
         */
        public String connectedResourceLink;
    }

    /**
     * Represents the state of IP allocation task.
     */
    public static class IPAddressAllocationTaskState extends TaskService.TaskServiceState {

        /**
         * SubStage.
         */
        public enum SubStage {
            CREATED, ALLOCATE_IP_ADDRESS, DEALLOCATE_IP_ADDRESS, FINISHED, FAILED
        }

        public enum RequestType {
            ALLOCATE, DEALLOCATE
        }

        /**
         * (Internal) Describes task sub-stage.
         */
        @ServiceDocument.Documentation(description = "Describes task sub-stage.")
        @PropertyOptions(usage = { SERVICE_USE }, indexing = STORE_ONLY)
        public SubStage taskSubStage;

        /**
         * The type of the request (allocate or deallocate).
         */
        @ServiceDocument.Documentation(description = "Request type -whether to allocate or de-allocate.")
        @ServiceDocument.PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.REQUIRED,
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT })
        public RequestType requestType;

        /**
         * In case of allocation request: id of the subnet for which IP address will be allocated.
         * Not used for de-allocation.
         */
        @ServiceDocument.Documentation(description = "The subnet from which IP address needs to be allocated.")
        @ServiceDocument.PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String subnetLink;

        /**
         * For allocation, set by the task with the resource links of IP address being allocated.
         * Required for de-allocation.
         */
        @ServiceDocument.Documentation(description = "Resource link of the IP address being allocated.")
        @ServiceDocument.PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public List<String> ipAddressLinks;

        /**
         * Connected resource that is associated with IP address(es)
         */
        @ServiceDocument.Documentation(description = "The connected resource associated with IP address(es).")
        @ServiceDocument.PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String connectedResourceLink;

        /**
         * For allocation, set by the task with the IP addresses being allocated. Not used for
         * de-allocation.
         */
        @ServiceDocument.Documentation(description = "The IP address being allocated.")
        @ServiceDocument.PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public List<String> ipAddresses;

        /**
         * For allocation, number of IP addresses to allocate. Not used for de-allocation.
         */
        public int allocationCount;

        /**
         * For allocation, set by the task with the ip range from which IP address is allocated. Not
         * used for de-allocation.
         */
        @ServiceDocument.Documentation(description = "Resource link of the ip range from which IP address is being allocated.")
        @ServiceDocument.PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public List<String> subnetRangeLinks;

        /**
         * A callback to the initiating task.
         */
        @ServiceDocument.Documentation(description = "A callback to the initiating task.")
        @ServiceDocument.PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT })
        public ServiceTaskCallback<?> serviceTaskCallback;

        public IPAddressAllocationTaskState() {
            this.ipAddresses = new ArrayList<>();
            this.ipAddressLinks = new ArrayList<>();
            this.subnetRangeLinks = new ArrayList<>();
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("request type: ").append(this.requestType);
            sb.append(", subnet link: ").append(this.subnetLink);

            sb.append(", subnet range links: ");
            for (int i = 0; i < this.subnetRangeLinks.size(); i++) {
                sb.append(this.subnetRangeLinks.get(i) + ", ");
            }

            sb.append(", IP address links: ");
            for (int i = 0; i < this.ipAddressLinks.size(); i++) {
                sb.append(this.ipAddressLinks.get(i) + ", ");
            }

            sb.append("IP address: ");
            for (int i = 0; i < this.ipAddresses.size() - 1; i++) {
                sb.append(this.ipAddresses.get(i) + ", ");
            }

            sb.append(this.ipAddresses.get(this.ipAddresses.size() - 1));
            return sb.toString();
        }
    }

    public IPAddressAllocationTaskService() {
        super(IPAddressAllocationTaskState.class);
        super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
        super.toggleOption(Service.ServiceOption.REPLICATION, true);
        super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handlePatch(Operation patch) {
        IPAddressAllocationTaskState body = getBody(patch);
        IPAddressAllocationTaskState currentState = getState(patch);

        if (validateTransitionAndUpdateState(patch, body, currentState)) {
            return;
        }

        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            handleStagePatch(currentState);
            break;
        case FINISHED:
            logInfo(() -> "Task is complete");
            sendCallbackResponse(currentState);
            break;
        case FAILED:
            logInfo(() -> "Task failed");
            sendCallbackResponse(currentState);
            break;
        default:
            break;
        }
    }

    protected void handleStagePatch(IPAddressAllocationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            if (state.requestType == IPAddressAllocationTaskState.RequestType.ALLOCATE) {
                proceedTo(IPAddressAllocationTaskState.SubStage.ALLOCATE_IP_ADDRESS,
                        s -> s.subnetLink = state.subnetLink);
            } else {
                proceedTo(IPAddressAllocationTaskState.SubStage.DEALLOCATE_IP_ADDRESS,
                        s -> s.ipAddressLinks = state.ipAddressLinks);
            }
            break;

        case ALLOCATE_IP_ADDRESS:
            allocateIpAddress(state);
            break;

        case DEALLOCATE_IP_ADDRESS:
            deallocateIpAddress(state);
            break;

        case FAILED:
            logWarning(() -> String.format("Task failed with %s",
                    Utils.toJsonHtml(state.taskInfo.failure)));
            sendSelfPatch(state, TaskState.TaskStage.FAILED, null);
            break;

        case FINISHED:
            sendSelfPatch(state, TaskState.TaskStage.FINISHED, null);
            break;

        default:
            break;
        }
    }

    @Override
    public void handleStart(Operation startOp) {
        try {
            IPAddressAllocationTaskState taskState = validateStartPost(startOp);

            initializeState(taskState, startOp);

            // Send completion to the caller (with a CREATED state)
            startOp.setBody(taskState).complete();

            // And then start internal state machine
            sendSelfPatch(taskState, TaskState.TaskStage.STARTED, null);
        } catch (Throwable e) {
            logSevere(e);
            startOp.fail(e);
        }
    }

    /**
     * Customize the validation logic that's part of initial {@code POST} creating the task service.
     *
     * @see #handleStart(Operation)
     */
    @Override
    protected IPAddressAllocationTaskState validateStartPost(Operation startOp) {
        try {
            IPAddressAllocationTaskState state = startOp
                    .getBody(IPAddressAllocationTaskState.class);
            if (state == null) {
                throw new IllegalArgumentException("IPAddressAllocationTaskState is required");
            }

            if (state.requestType == null) {
                throw new IllegalArgumentException("state.requestType is required");
            }

            if (state.requestType == IPAddressAllocationTaskState.RequestType.ALLOCATE) {
                if (state.subnetLink == null) {
                    throw new IllegalArgumentException("state.subnetLink is required");
                }
            } else {
                if (state.ipAddressLinks == null || state.ipAddressLinks.isEmpty()) {
                    throw new IllegalArgumentException("state.ipAddressLink is required");
                }
            }

            return state;
        } catch (Throwable e) {
            logSevere(e);
            startOp.fail(e);
            return null;
        }
    }

    /**
     * Customize the initialization logic (set the task with default values) that's part of initial
     * {@code POST} creating the task service.
     *
     * @see #handleStart(Operation)
     */
    @Override
    protected void initializeState(IPAddressAllocationTaskState startState, Operation startOp) {
        if (startState.taskInfo == null || startState.taskInfo.stage == null) {
            startState.taskInfo = new TaskState();
            startState.taskInfo.stage = TaskState.TaskStage.CREATED;
        }

        startState.taskSubStage = IPAddressAllocationTaskState.SubStage.CREATED;
    }

    /**
     * De-allocates an IP address, based on IP address resource link.
     *
     * @param state
     *            IP Address allocation task state.
     */
    private void deallocateIpAddress(IPAddressAllocationTaskState state) {
        IPAddressState addressState = new IPAddressState();
        addressState.ipAddressStatus = IPAddressState.IPAddressStatus.RELEASED;
        addressState.connectedResourceLink = ResourceUtils.NULL_LINK_VALUE;

        List<DeferredResult<Operation>> deferredResults = new ArrayList<>();
        for (int i = 0; i < state.ipAddressLinks.size(); i++) {
            String ipAddressResourceLink = state.ipAddressLinks.get(i);
            Operation patchOp = Operation.createPatch(this, ipAddressResourceLink)
                    .setBody(addressState)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask(e,
                                    "Failed to de-allocate IP address resource %s due to failure %s",
                                    ipAddressResourceLink, e.getMessage());
                            return;
                        }
                    });
            deferredResults.add(this.sendWithDeferredResult(patchOp));
        }

        DeferredResult.allOf(deferredResults).thenAccept(
                dr -> proceedTo(IPAddressAllocationTaskState.SubStage.FINISHED, null));
    }

    /**
     * Allocates IP Address for a subnet
     *
     * @param state
     *            IP Address allocation task state.
     */
    private void allocateIpAddress(IPAddressAllocationTaskState state) {
        IPAddressAllocationContext context = new IPAddressAllocationContext();
        context.connectedResourceLink = state.connectedResourceLink;
        this.retrieveSubnet(state.subnetLink, context)
                .thenCompose(this::retrieveIpRanges)
                .thenAccept(ctxt -> {
                    if (!ctxt.subnetRangeStatesIterator.hasNext()) {
                        logWarning(() -> String.format("No IP address ranges are available for "
                                + "subnet %s", context.subnetState.documentSelfLink));
                        // ignore this particular error for now and complete task
                        // TODO: this task would eventually need to delegate IP address allocation
                        // to an
                        // adapter, which will take the appropriate action based on the adapter type
                        proceedTo(IPAddressAllocationTaskState.SubStage.FINISHED, null);
                        return;
                    }
                    allocateIpAddressForSubnet(ctxt);
                });
    }

    /**
     * Retrieves ip range documents, that parent to a specific subnet link.
     *
     * @param context
     *            Allocation context information.
     * @return Sets list of subnet range states for the subnet resource link and returns the
     *         context.
     */
    private DeferredResult<IPAddressAllocationContext> retrieveIpRanges(
            IPAddressAllocationContext context) {
        Builder builder = Builder.create()
                .addKindFieldClause(SubnetRangeState.class)
                .addFieldClause(SubnetRangeState.FIELD_NAME_SUBNET_LINK,
                        context.subnetState.documentSelfLink);
        QueryUtils.QueryByPages<SubnetRangeState> query = new QueryUtils.QueryByPages<>(
                this.getHost(),
                builder.build(), SubnetRangeState.class, null);

        return query.collectDocuments(Collectors.toList())
                .thenApply(subnetRangeStates -> {
                    context.subnetRangeStatesIterator = subnetRangeStates.iterator();
                    return context;
                });
    }

    /**
     * Retrieves the subnet document that a resource link is pointing to.
     *
     * @param subnetResourceLink
     *            Resource link of the subnet document to be retrieved.
     * @return Subnet range document.
     */
    private DeferredResult<IPAddressAllocationContext> retrieveSubnet(String subnetResourceLink,
            IPAddressAllocationContext context) {
        return this.sendWithDeferredResult(Operation.createGet(this, subnetResourceLink))
                .thenApply(o -> {
                    context.subnetState = o.getBody(SubnetState.class);
                    return context;
                });
    }

    /**
     * Allocates IP address for a subnet, by recursively checking all the IP address subnet ranges
     * associated to the subnet.
     *
     * @param context
     *            Allocation context information.
     */
    private void allocateIpAddressForSubnet(IPAddressAllocationContext context) {
        if (!context.subnetRangeStatesIterator.hasNext()) {
            String message = String.format("No IP addresses are available for subnet %s",
                    context.subnetState.documentSelfLink);
            failTask(new Exception(message), message);
            return;
        }

        SubnetRangeState rangeState = context.subnetRangeStatesIterator.next();
        retrieveExistingIpAddressesFromRange(rangeState)
                .thenAccept((existingIpAddresses) -> {
                    if (!allocateIpAddressFromRange(context.subnetState, rangeState,
                            existingIpAddresses, context.connectedResourceLink)) {
                        allocateIpAddressForSubnet(context);
                    }
                });
    }

    /**
     * Retrieves existing IP address resources created with different status within a specific range
     *
     * @param rangeState
     *            Subnet range information.
     * @return List of IP Addresses created within that range.
     */
    private DeferredResult<List<IPAddressState>> retrieveExistingIpAddressesFromRange(
            SubnetRangeState rangeState) {
        QueryTask.Query getIpAddressQuery = QueryTask.Query.Builder.create()
                .addFieldClause(IPAddressState.FIELD_NAME_SUBNET_RANGE_LINK,
                        rangeState.documentSelfLink)
                .build();

        QueryUtils.QueryByPages<IPAddressState> queryByPages = new QueryUtils.QueryByPages<>(
                this.getHost(),
                getIpAddressQuery,
                IPAddressState.class, null);

        return queryByPages.collectDocuments(Collectors.toList());
    }

    /**
     * Allocates an IP address from an existing IP address range.
     *
     * @param existingIpAddressStates
     *            List of existing IP address resources that are created within that range.
     * @param subnetRangeState
     *            Subnet range state information.
     * @return True if IP address is allocated from within the range. False otherwise.
     */
    private boolean allocateIpAddressFromRange(SubnetState subnetState,
            SubnetRangeState subnetRangeState,
            List<IPAddressState> existingIpAddressStates, String resourceLink) {
        if (!IPVersion.IPv4.equals(subnetRangeState.ipVersion)) {
            logWarning(() -> String.format("Not allocating from IP address range %s. Currently, "
                    + "only IPv4 is supported", subnetRangeState.documentSelfLink));
            return false;
        }

        HashSet<Long> unavailableIpAddresses = new HashSet<>();
        unavailableIpAddresses.add(IpHelper.ipStringToLong(subnetState.gatewayAddress));

        // Allocate an IP address, if there is an existing IP address document that is available.
        for (IPAddressState addressState : existingIpAddressStates) {
            if (addressState.ipAddressStatus == IPAddressState.IPAddressStatus.AVAILABLE) {
                addressState.ipAddressStatus = IPAddressState.IPAddressStatus.ALLOCATED;
                addressState.connectedResourceLink = resourceLink;
                updateExistingIpAddressResource(addressState, subnetRangeState.documentSelfLink);
                return true;
            }

            unavailableIpAddresses.add(IpHelper.ipStringToLong(addressState.ipAddress));
        }

        // Allocate IP address document and create the document.
        long beginAddress = IpHelper.ipStringToLong(subnetRangeState.startIPAddress);
        long endAddress = IpHelper.ipStringToLong(subnetRangeState.endIPAddress);

        for (long address = beginAddress; address <= endAddress; address++) {
            if (!unavailableIpAddresses.contains(address)) {
                String ipAddress = IpHelper.longToIpString(address);
                createNewIpAddressResource(ipAddress, subnetRangeState.documentSelfLink,
                        resourceLink);
                return true;
            }
        }

        return false;
    }

    /**
     * Creates new IP address resource with the specified IP address and moves the task state to
     * completed, once it is done. Then it creates IP Address resource, it first creates it with
     * AVAILABLE state. It then changes the state to ALLOCATED. This is done in two steps, due to
     * concurrency issues. When multiple allocation requests are invoked at the same time, they end
     * up creating single IP Address resource. Only one of them succeeds in their PATCH and the
     * other one will retry the allocation operation.
     *
     * @param ipAddress
     *            IP address to use for the new IP address resource.
     * @param subnetRangeResourceLink
     *            Subnet range resource link to use for the new IP address resource.
     * @param connectedResourceLink
     *            Link to the resource this IP is assigned to.
     */
    private void createNewIpAddressResource(String ipAddress, String subnetRangeResourceLink,
            String connectedResourceLink) {
        IPAddressState ipAddressState = new IPAddressState();
        ipAddressState.ipAddressStatus = IPAddressState.IPAddressStatus.AVAILABLE;
        ipAddressState.ipAddress = ipAddress;
        ipAddressState.subnetRangeLink = subnetRangeResourceLink;
        ipAddressState.documentSelfLink = generateIPAddressDocumentSelfLink(subnetRangeResourceLink,
                ipAddress);

        sendRequest(Operation.createPost(this, IPAddressService.FACTORY_LINK)
                .setBody(ipAddressState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format(
                                "Failed to create new IP address resource %s for subnet %s for allocation due to error %s. "
                                        +
                                        "Will re-attempt allocation with a different IP Address.",
                                ipAddress, subnetRangeResourceLink, e.getMessage()));
                        proceedTo(IPAddressAllocationTaskState.SubStage.ALLOCATE_IP_ADDRESS, null);
                    } else {
                        IPAddressState availableIPAddress = o.getBody(IPAddressState.class);
                        availableIPAddress.ipAddressStatus = IPAddressState.IPAddressStatus.ALLOCATED;
                        availableIPAddress.connectedResourceLink = connectedResourceLink;
                        updateExistingIpAddressResource(availableIPAddress,
                                subnetRangeResourceLink);
                    }
                }));
    }

    /**
     * Updates an existing IP address resource and moves the task state to completed, once it is
     * done.
     *
     * @param addressState
     *            New IP address state.
     * @param subnetRangeResourceLink
     *            Resource link of the subnet range on which IP address is parented to.
     */
    private void updateExistingIpAddressResource(IPAddressState addressState,
            String subnetRangeResourceLink) {
        sendRequest(Operation.createPatch(this, addressState.documentSelfLink)
                .setBody(addressState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask(e, "Failed to update existing IP address resource %s",
                                addressState.documentSelfLink);
                        return;
                    }

                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_MODIFIED) {
                        // Another concurrent request obtained the IP Address. So, try to request IP
                        // address once again.
                        logWarning(() -> String.format(
                                "IP Address %s is already allocated. Will re-attempt allocation with a different IP Address.",
                                addressState.ipAddress));
                        proceedTo(IPAddressAllocationTaskState.SubStage.ALLOCATE_IP_ADDRESS, null);
                    } else {
                        proceedTo(IPAddressAllocationTaskState.SubStage.FINISHED, s -> {
                            s.ipAddresses.add(addressState.ipAddress);
                            s.ipAddressLinks.add(addressState.documentSelfLink);
                            s.subnetRangeLinks.add(subnetRangeResourceLink);
                        });
                    }
                }));
    }

    /**
     * Validates patch transition and updates it to the requested state
     *
     * @param patch
     *            Patch operation
     * @param body
     *            Body of the patch request
     * @param currentState
     *            Current state of patch request
     * @return True if transition is invalid. False otherwise.
     */
    private boolean validateTransitionAndUpdateState(Operation patch,
            IPAddressAllocationTaskState body,
            IPAddressAllocationTaskState currentState) {

        TaskState.TaskStage currentStage = currentState.taskInfo.stage;
        IPAddressAllocationTaskState.SubStage currentSubStage = currentState.taskSubStage;
        boolean isUpdate = false;

        if (body.ipAddressLinks != null) {
            currentState.ipAddressLinks = body.ipAddressLinks;
            isUpdate = true;
        }

        if (body.ipAddresses != null) {
            currentState.ipAddresses = body.ipAddresses;
            isUpdate = true;
        }

        if (body.subnetRangeLinks != null) {
            currentState.subnetRangeLinks = body.subnetRangeLinks;
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
            logWarning(() -> String.format("Referrer %s is patching us to failure: %s",
                    patch.getReferer(), Utils.toJsonHtml(body.taskInfo.failure)));
            currentState.taskInfo.failure = body.taskInfo.failure;
            currentState.taskInfo.stage = body.taskInfo.stage;
            currentState.taskSubStage = IPAddressAllocationTaskState.SubStage.FAILED;
            return false;
        }

        if (TaskState.isFinished(body.taskInfo)) {
            currentState.taskInfo.stage = body.taskInfo.stage;
            currentState.taskSubStage = IPAddressAllocationTaskState.SubStage.FINISHED;
            return false;
        }

        if (currentSubStage != null && body.taskSubStage != null
                && currentSubStage.ordinal() > body.taskSubStage.ordinal()) {
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

    private void failTask(Throwable e, String messageFormat, Object... args) {
        String message = String.format(messageFormat, args);
        logWarning(() -> message);

        IPAddressAllocationTaskState body = new IPAddressAllocationTaskState();
        body.taskInfo = new TaskState();
        body.taskInfo.stage = TaskState.TaskStage.FAILED;
        body.taskInfo.failure = Utils.toServiceErrorResponse(e);

        sendSelfPatch(body);
    }

    private void proceedTo(IPAddressAllocationTaskState.SubStage nextSubstage,
            Consumer<IPAddressAllocationTaskState> patchBodyConfigurator) {
        IPAddressAllocationTaskState state = new IPAddressAllocationTaskState();
        state.taskInfo = new TaskState();
        state.taskSubStage = nextSubstage;
        sendSelfPatch(state, TaskState.TaskStage.STARTED, patchBodyConfigurator);
    }

    private static String generateIPAddressDocumentSelfLink(String subnetRangeSelfLink,
            String ipAddress) {
        if (subnetRangeSelfLink == null || subnetRangeSelfLink.isEmpty()) {
            return ipAddress;
        }

        return UriUtils.getLastPathSegment(subnetRangeSelfLink) + ID_SEPARATOR + ipAddress;
    }

    private void sendCallbackResponse(IPAddressAllocationTaskState state) {
        IPAddressAllocationTaskResult result;
        if (state.taskInfo.stage == TaskState.TaskStage.FAILED) {
            result = new IPAddressAllocationTaskResult(
                    state.serviceTaskCallback
                            .getFailedResponse(state.taskInfo.failure).taskInfo.stage,
                    state.serviceTaskCallback
                            .getFailedResponse(state.taskInfo.failure).taskSubStage,
                    state.taskInfo.failure);
        } else {
            result = new IPAddressAllocationTaskResult(
                    state.serviceTaskCallback.getFinishedResponse().taskInfo.stage,
                    state.serviceTaskCallback.getFinishedResponse().taskSubStage, null);
        }

        result.ipAddresses = state.ipAddresses;
        result.ipAddressLinks = state.ipAddressLinks;
        result.subnetRangeLinks = state.subnetRangeLinks;
        result.connectedResourceLink = state.connectedResourceLink;
        if (state.ipAddresses != null && state.ipAddresses.size() > 0) {
            result.customProperties.put(state.connectedResourceLink, state.ipAddresses.get(0));
        }

        logInfo(String.format("Allocated IP addresses [%s] for resource [%s]",
                result.ipAddresses != null ? String.join(",", result.ipAddresses) : "",
                result.connectedResourceLink));

        sendRequest(Operation.createPatch(state.serviceTaskCallback.serviceURI).setBody(result));
    }
}

