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

package com.vmware.admiral.request.compute;

import static com.vmware.photon.controller.model.data.SchemaField.DATATYPE_STRING;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService.ComputeProvisionTaskState.SubStage;
import com.vmware.admiral.request.compute.enhancer.ComputeStateEnhancers;
import com.vmware.admiral.request.compute.enhancer.Enhancer.EnhanceContext;
import com.vmware.admiral.request.utils.EventTopicUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.EventTopicDeclarator;
import com.vmware.admiral.service.common.EventTopicService;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.data.SchemaBuilder;
import com.vmware.photon.controller.model.data.SchemaField.Type;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback;
import com.vmware.photon.controller.model.tasks.SubTaskService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Task implementing the provisioning of a compute resource.
 */
public class ComputeProvisionTaskService extends
        AbstractTaskStatefulService<ComputeProvisionTaskService.ComputeProvisionTaskState, ComputeProvisionTaskService.ComputeProvisionTaskState.SubStage>
        implements EventTopicDeclarator {

    private static final int WAIT_CONNECTION_RETRY_COUNT = 10;

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_PROVISION_TASKS;

    public static final String DISPLAY_NAME = "Compute Provision";

    //Pre-provision EventTopic constants
    public static final String COMPUTE_PROVISION_TOPIC_TASK_SELF_LINK =
            "compute-provision";
    public static final String COMPUTE_PROVISION_TOPIC_ID = "com.vmware.compute.provision.pre";
    public static final String COMPUTE_PROVISION_TOPIC_NAME = "Compute Provision";
    public static final String COMPUTE_PROVISION_TOPIC_TASK_DESCRIPTION = "Fired when a compute "
            + "resource is being provisioned.";
    public static final String COMPUTE_PROVISION_TOPIC_FIELD_RESOURCE_NAMES = "resourceNames";
    public static final String COMPUTE_PROVISION_TOPIC_FIELD_RESOURCE_NAMES_LABEL = "Resource names.";
    public static final String COMPUTE_PROVISION_TOPIC_FIELD_RESOURCE_NAMES_DESCRIPTION =
            "Array of the resources names.";

    public static final String COMPUTE_PROVISION_TOPIC_FIELD_ADDRESSES = "addresses";
    public static final String COMPUTE_PROVISION_TOPIC_FIELD_ADDRESSES_LABEL = "Static ip "
            + "addresses.";
    public static final String COMPUTE_PROVISION_TOPIC_FIELD_ADDRESSES_DESCRIPTION =
            "Array of static ip addresses.";

    public static final String COMPUTE_PROVISION_TOPIC_FIELD_SUBNET = "subnetName";
    public static final String COMPUTE_PROVISION_TOPIC_FIELD_SUBNET_LABEL = "Name of subnetwork.";
    public static final String COMPUTE_PROVISION_TOPIC_FIELD_SUBNET_DESCRIPTION =
            "Subnetwork where resource will be connected.";

    public static class ComputeProvisionTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeProvisionTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            CUSTOMIZING_COMPUTE,
            PROVISIONING_COMPUTE,
            PROVISIONING_COMPUTE_COMPLETED,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(PROVISIONING_COMPUTE));
            static final Set<SubStage> SUBSCRIPTION_SUB_STAGES = new HashSet<>(
                    Arrays.asList(CUSTOMIZING_COMPUTE));
        }

        /**
         * (Required) Links to already allocated resources that are going to be provisioned.
         */
        @Documentation(description = "Links to already allocated resources that are going to be provisioned.")
        @PropertyOptions(indexing = STORE_ONLY, usage = { REQUIRED, SINGLE_ASSIGNMENT })
        public Set<String> resourceLinks;

        /**
         * Normalized error threshold between 0 and 1.0.
         */
        public double errorThreshold;

    }

    public ComputeProvisionTaskService() {
        super(ComputeProvisionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
        super.subscriptionSubStages = EnumSet.copyOf(SubStage.SUBSCRIPTION_SUB_STAGES);
    }

    @Override
    protected void handleStartedStagePatch(ComputeProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            customizeCompute(state);
            break;
        case CUSTOMIZING_COMPUTE:
            provisionResources(state, null);
            break;
        case PROVISIONING_COMPUTE:
            break;
        case PROVISIONING_COMPUTE_COMPLETED:
            queryForProvisionedResources(state);
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

    private void customizeCompute(ComputeProvisionTaskState state) {
        URI referer = UriUtils.buildUri(getHost().getPublicUri(), getSelfLink());
        List<DeferredResult<Operation>> results = state.resourceLinks.stream()
                .map(link -> Operation.createGet(this, link))
                .map(o -> sendWithDeferredResult(o, ComputeState.class))
                .map(dr -> {
                    return dr.thenCompose(cs -> {
                        EnhanceContext context = new EnhanceContext();
                        context.endpointType = cs.customProperties
                                .get(ComputeConstants.CUSTOM_PROP_ENDPOINT_TYPE_NAME);

                        return ComputeStateEnhancers.build(getHost(), referer).enhance(context, cs);
                    }).thenCompose(cs -> sendWithDeferredResult(
                            Operation.createPatch(this, cs.documentSelfLink).setBody(cs)));
                })
                .collect(Collectors.toList());

        DeferredResult.allOf(results).whenComplete((all, t) -> {
            if (t != null) {
                failTask("Error patching compute states", t);
                return;
            }
            proceedTo(SubStage.CUSTOMIZING_COMPUTE);
        });
    }

    @Override
    protected void validateStateOnStart(ComputeProvisionTaskState state)
            throws IllegalArgumentException {
        if (state.resourceLinks.isEmpty()) {
            throw new LocalizableValidationException("No compute instances to provision",
                    "request.compute.provision.empty");
        }
    }

    private void provisionResources(ComputeProvisionTaskState state, String subTaskLink) {
        try {
            Set<String> resourceLinks = state.resourceLinks;
            if (subTaskLink == null) {
                // recurse after creating a sub task
                createSubTaskForProvisionCallbacks(state);
                return;
            }
            boolean isMockRequest = DeploymentProfileConfig.getInstance().isTest();

            List<DeferredResult<Operation>> ops = resourceLinks.stream().map(
                    rl -> ComputeStateWithDescription.buildUri(UriUtils.buildUri(getHost(), rl)))
                    .map(uri -> sendWithDeferredResult(Operation.createGet(uri),
                            ComputeStateWithDescription.class))
                    .map(dr -> dr.thenCompose(c -> {
                        ComputeInstanceRequest cr = new ComputeInstanceRequest();
                        cr.resourceReference = UriUtils.buildUri(getHost(), c.documentSelfLink);
                        cr.requestType = InstanceRequestType.CREATE;
                        cr.taskReference = UriUtils.buildUri(getHost(), subTaskLink);
                        cr.isMockRequest = isMockRequest;
                        return sendWithDeferredResult(
                                Operation.createPatch(c.description.instanceAdapterReference)
                                        .setBody(cr))
                                .exceptionally(e -> {
                                    ResourceOperationResponse r = ResourceOperationResponse
                                            .fail(c.documentSelfLink, e);
                                    completeSubTask(subTaskLink, r);
                                    return null;
                                });
                    })).collect(Collectors.toList());

            DeferredResult.allOf(ops).whenComplete((all, e) -> {
                logInfo("Requested provisioning of %s compute resources.",
                        resourceLinks.size());
                proceedTo(SubStage.PROVISIONING_COMPUTE);
            });
        } catch (Throwable e) {
            failTask("System failure creating ContainerStates", e);
        }
    }

    private void createSubTaskForProvisionCallbacks(ComputeProvisionTaskState currentState) {

        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback.create(getUri());
        callback.onSuccessTo(SubStage.PROVISIONING_COMPUTE_COMPLETED);
        SubTaskService.SubTaskState<SubStage> subTaskInitState = new SubTaskService.SubTaskState<>();
        // tell the sub task with what to patch us, on completion
        subTaskInitState.serviceTaskCallback = callback;
        subTaskInitState.errorThreshold = currentState.errorThreshold;
        subTaskInitState.completionsRemaining = currentState.resourceLinks.size();
        subTaskInitState.tenantLinks = currentState.tenantLinks;
        Operation startPost = Operation
                .createPost(this, SubTaskService.FACTORY_LINK)
                .setBody(subTaskInitState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning("Failure creating sub task: %s",
                                        Utils.toString(e));
                                failTask("Failure creating sub task", e);
                                return;
                            }
                            SubTaskService.SubTaskState<?> body = o
                                    .getBody(SubTaskService.SubTaskState.class);
                            // continue, passing the sub task link
                            provisionResources(currentState, body.documentSelfLink);
                        });
        sendRequest(startPost);
    }

    private void completeSubTask(String subTaskLink, Object body) {
        Operation.createPatch(this, subTaskLink)
                .setBody(body)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logWarning("Unable to complete subtask: %s, reason: %s",
                                        subTaskLink, Utils.toString(ex));
                            }
                        })
                .sendWith(this);
    }

    private void queryForProvisionedResources(ComputeProvisionTaskState state) {
        Set<String> resourceLinks = state.resourceLinks;
        if (resourceLinks == null || resourceLinks.isEmpty()) {
            complete();
            return;
        }

        Builder queryBuilder = Query.Builder.create()
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, resourceLinks)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME, "true");
        QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(queryBuilder.build());

        sendRequest(Operation.createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTaskBuilder.build())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving provisioned resources: " + Utils.toString(e),
                                null);
                        return;
                    }

                    QueryTask task = o.getBody(QueryTask.class);
                    if (task.results.documents == null || task.results.documents.isEmpty()) {
                        complete();
                        return;
                    }
                    validateConnectionsAndRegisterContainerHost(state,
                            task.results.documents.values().stream()
                                    .map(json -> Utils.fromJson(json, ComputeState.class))
                                    .collect(Collectors.toList()));
                }));
    }

    private void validateConnectionsAndRegisterContainerHost(ComputeProvisionTaskState state,
            List<ComputeState> computes) {
        URI specValidateUri = UriUtils.buildUri(getHost(), ContainerHostService.SELF_LINK,
                ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);
        URI specUri = UriUtils.buildUri(getHost(), ContainerHostService.SELF_LINK);
        AtomicInteger remaining = new AtomicInteger(computes.size());
        AtomicReference<Throwable> error = new AtomicReference<>();
        for (ComputeState computeState : computes) {
            if (computeState.address == null) {
                if (DeploymentProfileConfig.getInstance().isTest()) {
                    computeState.address = "127.0.0.1";
                } else {
                    failTask("No IP address allocated on machine: " + computeState.name, null);
                    return;
                }
            }
            ContainerHostSpec spec = new ContainerHostSpec();
            spec.hostState = computeState;
            spec.acceptCertificate = true;
            waitUntilConnectionValid(specValidateUri, spec, 0, (ex) -> {
                if (error.get() != null) {
                    return;
                }

                if (ex != null) {
                    error.set(ex);
                    logWarning("Failed to validate container host connection: %s",
                            Utils.toString(ex));
                    failTask("Failed registering container host", error.get());
                    return;
                }

                spec.acceptHostAddress = true;
                registerContainerHost(state, specUri, remaining, spec, error);
            });
        }
    }

    private void registerContainerHost(ComputeProvisionTaskState state, URI specUri,
            AtomicInteger remaining, ContainerHostSpec spec, AtomicReference<Throwable> error) {

        Operation.createPut(specUri).setBody(spec).setCompletion((op, er) -> {
            if (error.get() != null) {
                return;
            }
            if (er != null) {
                error.set(er);
                failTask("Failed registering container host", er);
                return;
            }
            if (remaining.decrementAndGet() == 0) {
                complete();
            }
        }).sendWith(this);

    }

    private void waitUntilConnectionValid(URI specValidateUri, ContainerHostSpec spec,
            int retryCount, Consumer<Throwable> callback) {

        Operation.createPut(specValidateUri).setBody(spec).setCompletion((op, er) -> {
            if (er != null) {
                logSevere(er);
                if (retryCount > WAIT_CONNECTION_RETRY_COUNT) {
                    callback.accept(er);
                } else {
                    getHost().schedule(() -> waitUntilConnectionValid(specValidateUri, spec,
                            retryCount + 1, callback), 10, TimeUnit.SECONDS);
                }
            } else {
                callback.accept(null);
            }
        }).sendWith(this);
    }

    public static class ExtensibilityCallbackResponse extends BaseExtensibilityCallbackResponse {

        /**
         * Name of the resources
         */
        public Set<String> resourceNames;

        /**
         * Static ip addresses
         */
        public Set<String> addresses;

        /**
         * Name of the subnetwork
         */
        public String subnetName;
    }

    @Override
    protected void enhanceNotificationPayload(ComputeProvisionTaskState state,
            BaseExtensibilityCallbackResponse notificationPayload, Runnable callback) {
        List<DeferredResult<ComputeState>> results = state.resourceLinks.stream()
                .map(link -> Operation.createGet(this, link))
                .map(o -> sendWithDeferredResult(o, ComputeState.class))
                .collect(Collectors.toList());

        DeferredResult.allOf(results).whenComplete((states, err) -> {
            if (err == null) {
                ExtensibilityCallbackResponse payload = (ExtensibilityCallbackResponse)
                        notificationPayload;
                payload.resourceNames = states.stream().map(s -> s.name)
                        .collect(Collectors.toSet());

                //squash-merge all properties until we fire an event per resource
                payload.customProperties = states.stream().flatMap(s -> s.customProperties
                        .entrySet().stream())
                        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
            } else {
                failTask("Failed retreiving custom properties.", err);
            }
            callback.run();
        });
    }

    @Override
    protected void enhanceExtensibilityResponse(ComputeProvisionTaskState state,
            ServiceTaskCallbackResponse replyPayload,
            Runnable callback) {

        patchCustomProperties(state, replyPayload, () -> {
            setIpAddress(state, replyPayload, callback);
        });
    }

    private void patchCustomProperties(ComputeProvisionTaskState state,
            ServiceTaskCallbackResponse replyPayload, Runnable callback) {
        List<DeferredResult<Operation>> results = state.resourceLinks.stream()
                .map(link -> Operation.createGet(this, link))
                .map(o -> sendWithDeferredResult(o, ComputeState.class))
                .map(dr -> dr.thenCompose(cs -> {
                    cs.customProperties.putAll(replyPayload.customProperties);
                    return sendWithDeferredResult(
                            Operation.createPatch(this, cs.documentSelfLink).setBody(cs));
                }))
                .collect(Collectors.toList());

        DeferredResult.allOf(results).whenComplete((all, t) -> {
            if (t != null) {
                failTask("Error patching compute states", t);
                return;
            }

            callback.run();
        });
    }

    /**
     * <p>
     *     Assign static ip to machine that is scheduled for provisioning.
     *     Process has several stages.
     *     <ul>
     *         <li> Subnetwork validation </li>
     *         <li> Create NIC Description </li>
     *         <li> Create NIC State </li>
     *         <li> Bound NIC State(s) to Compute(s) </li>
     *     </ul>
     * </p>
     * @param state - task state
     * @param replyPayload - reply payload provided by service
     * @param callback - callback which will resume the task, once ip assignment finished
     */
    private void setIpAddress(ComputeProvisionTaskState state,
            ServiceTaskCallbackResponse replyPayload,
            Runnable callback) {

        ExtensibilityCallbackResponse extensibilityResponse = (ExtensibilityCallbackResponse) replyPayload;

        //Check if client patched address field
        if (state.taskSubStage.equals(SubStage.CUSTOMIZING_COMPUTE) && extensibilityResponse
                .addresses != null) {

            //If subnetwork is not provided this ip assignment is pointless.
            if (extensibilityResponse.subnetName == null) {
                String error = "Static ip is assigned, but sub network name is not provided.";
                failTask(error, new IllegalArgumentException(error));
                return;
            }

            retrieveSubnetwork(state, extensibilityResponse, callback);

        } else {
            //Host address is not provided by client => Resume task.
            callback.run();
        }

    }

    private void createNicDescription(ComputeProvisionTaskState state, Set<String> addresses,
            SubnetState subnet, Runnable callback) {

        Iterator<String> iterator = addresses.iterator();

        List<DeferredResult<NetworkInterfaceDescription>> results = state.resourceLinks.stream()
                .filter(compute -> iterator.hasNext())
                .map(compute -> createNicDescriptionOperation(state, iterator.next()))
                .map(o -> sendWithDeferredResult(o, NetworkInterfaceDescription.class))
                .collect(Collectors.toList());

        DeferredResult.allOf(results).whenComplete((nics, err) -> {
            if (err != null) {
                failTask("Failed creating NetworkInterfaceDescriptions.", err);
                return;
            }

            createNicState(state, nics, subnet, callback);

        });
    }

    private void createNicState(ComputeProvisionTaskState state,
            List<NetworkInterfaceDescription> nicDescs, SubnetState subnet, Runnable callback) {

        List<DeferredResult<NetworkInterfaceState>> results = nicDescs.stream()
                .map(nicDesc -> createNicStateOperation(state, nicDesc, subnet))
                .map(o -> sendWithDeferredResult(o, NetworkInterfaceState.class))
                .collect(Collectors.toList());

        DeferredResult.allOf(results).whenComplete((nics, err) -> {
            if (err != null) {
                failTask("Failed creating NetworkInterfaceState.", err);
                return;
            }

            //For every resource assign newly created NICs.
            Map<String, String> computeLinkToNick = nics.stream().collect(Collectors
                    .toMap(i -> state.resourceLinks.iterator().next(),
                            nic -> nic.documentSelfLink));

            boundNicToCompute(computeLinkToNick, callback);
        });
    }

    private void boundNicToCompute(Map<String, String> computeToNic, Runnable callback) {
        List<DeferredResult<ComputeState>> results = computeToNic.entrySet().stream()
                .map(entry -> patchComputeNic(entry.getKey(), entry.getValue()))
                .map(o -> sendWithDeferredResult(o, ComputeState.class))
                .collect(Collectors.toList());

        DeferredResult.allOf(results).whenComplete((states, err) -> {
            if (err != null) {
                failTask("Failed patching ComputeStates with addresses.", err);
                return;
            }

            //ComputeState(s) have been patched -> Resume the task service.
            callback.run();
        });
    }

    @Override
    protected BaseExtensibilityCallbackResponse notificationPayload() {
        return new ExtensibilityCallbackResponse();
    }

    private void computeProvisionEventTopic(ServiceHost host) {
        EventTopicService.TopicTaskInfo taskInfo = new EventTopicService.TopicTaskInfo();
        taskInfo.task = ComputeProvisionTaskState.class.getSimpleName();
        taskInfo.stage = TaskStage.STARTED.name();
        taskInfo.substage = SubStage.CUSTOMIZING_COMPUTE.name();

        EventTopicUtils.registerEventTopic(COMPUTE_PROVISION_TOPIC_ID, COMPUTE_PROVISION_TOPIC_NAME,
                COMPUTE_PROVISION_TOPIC_TASK_DESCRIPTION, COMPUTE_PROVISION_TOPIC_TASK_SELF_LINK,
                Boolean.TRUE, computeProvisionTopicSchema(), taskInfo, host);
    }

    private Operation createNicDescriptionOperation(ComputeProvisionTaskState state,
            String address) {

        NetworkInterfaceDescription nic = new NetworkInterfaceDescription();
        nic.assignment = IpAssignment.STATIC;
        nic.address = address;
        nic.tenantLinks = state.tenantLinks;

        return Operation.createPost(getHost(), NetworkInterfaceDescriptionService.FACTORY_LINK)
                .setReferer(getHost().getUri())
                .setBody(nic)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask(e.getMessage(), new Throwable(e));
                    }
                });
    }

    private Operation createNicStateOperation(ComputeProvisionTaskState state,
            NetworkInterfaceDescription nicDesc, SubnetState subnet) {

        NetworkInterfaceState nicState = new NetworkInterfaceState();
        nicState.networkInterfaceDescriptionLink = nicDesc.documentSelfLink;
        nicState.address = nicDesc.address;
        nicState.tenantLinks = state.tenantLinks;
        // nicState.networkLink = subnet.networkLink;
        nicState.subnetLink = subnet.documentSelfLink;

        return Operation.createPost(getHost(), NetworkInterfaceService.FACTORY_LINK)
                .setReferer(getHost().getUri())
                .setBody(nicState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask(e.getMessage(), new Throwable(e));
                        return;
                    }

                });
    }

    private Operation patchComputeNic(String computeLink, String nicSelfLink) {
        ComputeState state = new ComputeState();
        state.networkInterfaceLinks = Collections.singletonList(nicSelfLink);
        return (Operation.createPatch(getHost(), computeLink)
                .setBody(state)
                .setReferer(getHost().getUri())
                .setCompletion((oo, ee) -> {
                    if (ee != null) {
                        getHost().log(Level.SEVERE, ee.getMessage());
                    }
                }));
    }

    private SchemaBuilder computeProvisionTopicSchema() {
        return new SchemaBuilder()
                .addField(COMPUTE_PROVISION_TOPIC_FIELD_RESOURCE_NAMES)
                .withType(Type.LIST)
                .withDataType(DATATYPE_STRING)
                .withLabel(COMPUTE_PROVISION_TOPIC_FIELD_RESOURCE_NAMES_LABEL)
                .withDescription(COMPUTE_PROVISION_TOPIC_FIELD_RESOURCE_NAMES_DESCRIPTION)
                .done()
                .addField(COMPUTE_PROVISION_TOPIC_FIELD_ADDRESSES)
                .withType(Type.LIST)
                .withDataType(DATATYPE_STRING)
                .withLabel(COMPUTE_PROVISION_TOPIC_FIELD_ADDRESSES_LABEL)
                .withDescription(COMPUTE_PROVISION_TOPIC_FIELD_ADDRESSES_DESCRIPTION)
                .done()
                .addField(COMPUTE_PROVISION_TOPIC_FIELD_SUBNET)
                .withDataType(DATATYPE_STRING)
                .withLabel(COMPUTE_PROVISION_TOPIC_FIELD_SUBNET_LABEL)
                .withDescription(COMPUTE_PROVISION_TOPIC_FIELD_SUBNET_DESCRIPTION)
                .done();
    }

    @Override
    public void registerEventTopics(ServiceHost host) {
        computeProvisionEventTopic(host);
    }

    private void retrieveSubnetwork(ComputeProvisionTaskState state,
            ExtensibilityCallbackResponse extensibilityResponse, Runnable callback) {

        QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addFieldClause("name", extensibilityResponse.subnetName);

        QueryTask q = QueryTask.Builder.create().setQuery(queryBuilder.build()).build();
        q.querySpec.resultLimit = ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT;
        QueryUtil.addExpandOption(q);

        //It is expected one particular subnet provided by client.
        final SubnetState[] subnet = new SubnetState[1];

        new ServiceDocumentQuery<>(getHost(), SubnetState.class).query(q, (r) -> {
            if (r.hasException()) {
                getHost().log(Level.WARNING,
                        "Exception while quering SubnetState with name [%s]. Error: [%s]",
                        extensibilityResponse.subnetName,
                        r.getException().getMessage());
                failTask(r.getException().getMessage(), r.getException());
                return;
            } else if (r.hasResult()) {
                subnet[0] = r.getResult();
            } else {
                if (subnet.length == 0) {
                    failTask("No subnets with name [%s] found.", new Throwable());
                    return;
                }

                //Validate than continue with creation of Nic description & state.
                SubnetState subnetState = subnet[0];

                validateSubnetwork(subnetState);

                createNicDescription(state, extensibilityResponse.addresses, subnetState, callback);
            }
        });

    }

    private void validateSubnetwork(SubnetState subnetwork) {

        AssertUtil.assertNotEmpty(subnetwork.dnsSearchDomains, "dnsSearchDomains");

        AssertUtil.assertNotNullOrEmpty(subnetwork.gatewayAddress, "gatewayAddress");

        AssertUtil.assertNotEmpty(subnetwork.dnsServerAddresses, "dnsSearchDomains");

        AssertUtil.assertNotNullOrEmpty(subnetwork.domain, "domain");

        AssertUtil.assertNotNullOrEmpty(subnetwork.subnetCIDR, "subnetCIDR");
    }
}
