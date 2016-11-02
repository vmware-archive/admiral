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

package com.vmware.admiral.cd;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;
import static com.vmware.admiral.compute.container.SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_ID;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.vmware.admiral.cd.SelfProvisioningTaskService.SelfProvisioningTaskState.SubStage;
import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.compute.ProvisionContainerHostsTaskService;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.QueryTaskClientHelper;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;

public class SelfProvisioningTaskService extends
        AbstractTaskStatefulService<SelfProvisioningTaskService.SelfProvisioningTaskState,
        SelfProvisioningTaskService.SelfProvisioningTaskState.SubStage> {
    public static final String FACTORY_LINK = ManagementUriParts.SELF_PROVISIONING;
    public static final String DISPLAY_NAME = "Self Provisioning";

    private static final String RESOURCE_POOL_ID = "hosts-resource-pool";
    private static final String ENDPOINT_AUTH_CREDENTIALS_ID = "auth-credentials";
    private static final String CONTAINER_TEMPLATE_FILE = "/templates/admiral-cluster.yaml";

    // constants for waiting the machine to get provision and start up
    private static final int RETRY_COUNT = Integer.parseInt(System.getProperty(
            "dcp.management.container.self.provision.wait.retry", "30"));

    private static final int RETRY_DELAY_SECONDS = Integer.parseInt(System.getProperty(
            "dcp.management.container.self.provision.wait.delay.seconds", "20"));

    public static class SelfProvisioningTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<SelfProvisioningTaskState.SubStage> {

        public static enum EndpointType {
            AWS, Azure, GCP;
        }

        public static enum SubStage {
            CREATED,
            RESOURCE_POOL_CREATED,
            QUERY_FOR_CONTAINER_HOSTS,
            AUTH_CREDENTIALS_CREATED,
            COMPUTE_DESC_CREATED,
            COMPUTE_PROVISIONING_STARTED,
            COMPUTE_PROVISIONING_COMPLETED,
            TEMPLATE_LOADED,
            CLEANUP_CONTAINERS_STARTED,
            CLEANUP_CONTAINERS_COMPLETED,
            CONTAINER_PROVISIONING_STARTED,
            CONTAINER_PROVISIONING_COMPLETED,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(COMPUTE_PROVISIONING_STARTED, CONTAINER_PROVISIONING_STARTED));
        }

        /** (Required) The endpoint authentication key (aws, azure...) */
        @Documentation(description = "The endpoint authentication key (aws, azure...)", exampleString = "AKIAI7RVXXKQK52V2B6Q")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.REQUIRED }, indexing = { PropertyIndexingOption.STORE_ONLY })
        public String endpointAuthKey;

        /** (Required) The endpoint authentication private key (aws, azure...) */
        @Documentation(description = "The endpoint authentication private key (aws, azure...)", exampleString = "akEHvnvz/k5TVWdnqmp4IwKpQDkHtKuZ/TRmce5u")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.REQUIRED }, indexing = { PropertyIndexingOption.STORE_ONLY })
        public String endpointAuthPrivateKey;

        /** (Required) The endpoint availability zone (aws, azure...) */
        @Documentation(description = " The endpoint availability zone (aws, azure...)", exampleString = "us-east-1a")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.REQUIRED }, indexing = { PropertyIndexingOption.STORE_ONLY })
        public String availabilityZoneId;

        /** (Required) Endpoint securityGroup used for networking definition*/
        @Documentation(description = "Endpoint compute instance type  (micro, small, large ...)")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.REQUIRED }, indexing = { PropertyIndexingOption.STORE_ONLY })
        public String securityGroup;

        /** (Required) The Endpoint Type (AWS, Azure, GCP) */
        @Documentation(description = "The Endpoint Type (AWS, Azure, GCP)")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.REQUIRED }, indexing = { PropertyIndexingOption.STORE_ONLY })
        public EndpointType endpointType;

        /** (Required) Endpoint compute instance type  (micro, small, large ...)*/
        @Documentation(description = "Endpoint compute instance type  (micro, small, large ...)", exampleString = "t2.micro")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.REQUIRED }, indexing = { PropertyIndexingOption.STORE_ONLY })
        public String computeInstanceType;

        /** (Optional- default 1) Number of container hosts to provision. */
        @Documentation(description = "Number of container hosts to provision.")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT },
                indexing = { PropertyIndexingOption.STORE_ONLY })
        public long clusterSize;

        /** (Set by a Task) The container state resource links of the provisioned container instances */
        @Documentation(description = "The container state resource links of the provisioned container instances.")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL }, indexing = {
                        PropertyIndexingOption.STORE_ONLY })
        public Set<String> containerResourceLinks;

        /** (Set by a Task) The compute resource links (VMs) provisioned based on the endpoint. */
        @Documentation(description = "The compute resource links (VMs) provisioned based on the endpoint.")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL },
                indexing = { PropertyIndexingOption.STORE_ONLY })
        public Set<String> computeResourceLinks;

        /** (Set by a Task) The compute or container resource links returned by the subtasks. */
        @Documentation(description = "The compute or container resource links returned by the subtasks.")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL },
                indexing = { PropertyIndexingOption.STORE_ONLY })
        public Set<String> resourceLinks;

        /** (Set by a Task) The compute description to define the container host cluster */
        @Documentation(description = "The compute description to define the container host cluster.")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL },
                indexing = { PropertyIndexingOption.STORE_ONLY })
        public String computeDescriptionLink;

        /** (Set by a Task) The composite container description created by importing the cluster template */
        @Documentation(description = "The composite container description created by importing the cluster template.")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL },
                indexing = { PropertyIndexingOption.STORE_ONLY })
        public String compositeDescriptionLink;
    }

    public SelfProvisioningTaskService() {
        super(SelfProvisioningTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            SelfProvisioningTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.containerResourceLinks;
        if (state.containerResourceLinks == null || state.containerResourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for self provisioned container states.");
        }
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    @Override
    protected void validateStateOnStart(SelfProvisioningTaskState state)
            throws IllegalArgumentException {
        if (state.clusterSize <= 0) {
            state.clusterSize = 1;
        }
    }

    @Override
    protected void handleStartedStagePatch(SelfProvisioningTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            createIfNotExists(state, ResourcePoolState.class, getResourcePoolLink(state),
                    SubStage.RESOURCE_POOL_CREATED,
                    (nextStage) -> createResourcePool(state, nextStage));
            break;
        case RESOURCE_POOL_CREATED:
            queryForContainerHosts(state, SubStage.QUERY_FOR_CONTAINER_HOSTS);
            break;
        case QUERY_FOR_CONTAINER_HOSTS:
            createIfNotExists(state, AuthCredentialsServiceState.class,
                    getAuthCredentialLink(state),
                    SubStage.AUTH_CREDENTIALS_CREATED,
                    (nextStage) -> createAuthCredentials(state, nextStage));
            break;
        case AUTH_CREDENTIALS_CREATED:
            createComputeDescription(state, SubStage.COMPUTE_DESC_CREATED);
            break;
        case COMPUTE_DESC_CREATED:
            createComputeProvisioningTask(state, SubStage.COMPUTE_PROVISIONING_STARTED);
            break;
        case COMPUTE_PROVISIONING_STARTED:
            break;//transient
        case COMPUTE_PROVISIONING_COMPLETED:
            logInfo("Provisioned compute resources: %s", state.resourceLinks);
            loadTemplate(state, SubStage.TEMPLATE_LOADED);
            break;
        case TEMPLATE_LOADED:
            waitComputeToBecomeOperational(state, RETRY_COUNT, SubStage.CLEANUP_CONTAINERS_STARTED);
            break;
        case CLEANUP_CONTAINERS_STARTED:
            removeContainers(state, SubStage.CLEANUP_CONTAINERS_COMPLETED);
            break;
        case CLEANUP_CONTAINERS_COMPLETED:
            provisionSelfContainerClusterTemplate(
                    state,
                    SubStage.CONTAINER_PROVISIONING_STARTED);
            break;
        case CONTAINER_PROVISIONING_STARTED:
            break;//transient
        case CONTAINER_PROVISIONING_COMPLETED:
            logInfo("Provisioned container resources: %s", state.resourceLinks);
            SelfProvisioningTaskState body = createUpdateSubStageTask(state, SubStage.COMPLETED);
            body.containerResourceLinks = state.resourceLinks;
            sendSelfPatch(body);
            break;
        case COMPLETED:
            complete(state, SubStage.COMPLETED);
            break;
        case ERROR:
            completeWithError(state, SubStage.ERROR);
            break;
        default:
            break;
        }
    }

    private void loadTemplate(SelfProvisioningTaskState state, SubStage nextStage) {
        try {
            String template = loadResource(CONTAINER_TEMPLATE_FILE);
            sendRequest(Operation
                    .createPost(UriUtils.buildUri(getHost(),
                            CompositeDescriptionContentService.SELF_LINK))
                    .setContentType(MEDIA_TYPE_APPLICATION_YAML)
                    .setBody(template)
                    .setCompletion(
                            (o, e) -> {
                                if (e != null) {
                                    failTask("Failure importing template.", e);
                                    return;
                                }

                                String compositeDescUrl = o
                                        .getResponseHeader(Operation.LOCATION_HEADER);
                                SelfProvisioningTaskState body = createUpdateSubStageTask(state,
                                        nextStage);
                                body.computeResourceLinks = state.resourceLinks;

                                sendRequest(Operation
                                        .createGet(URI.create(compositeDescUrl))
                                        .setCompletion(
                                                (op, ex) -> {
                                                    if (ex != null) {
                                                        failTask("Failure getting comp desc.", ex);
                                                        return;
                                                    }
                                                    CompositeDescription compDesc = op
                                                            .getBody(CompositeDescription.class);
                                                    body.compositeDescriptionLink = compDesc.documentSelfLink;
                                                    sendSelfPatch(body);
                                                }));
                            }));
        } catch (Throwable e) {
            failTask("Exception during loading template.", e);
        }
    }

    private void waitComputeToBecomeOperational(
            SelfProvisioningTaskState state, int retriesLeft, SubStage nextStage) {

        AtomicInteger computeCount = new AtomicInteger(state.computeResourceLinks.size());
        AtomicBoolean failedAlready = new AtomicBoolean();

        for (String computeLink : state.computeResourceLinks) {
            String hostId = Service.getId(computeLink);
            String systemContainerLink = SystemContainerDescriptions
                    .getSystemContainerSelfLink(
                            SystemContainerDescriptions.AGENT_CONTAINER_NAME, hostId);

            getContainerWhenAvailable(state, systemContainerLink, retriesLeft, failedAlready,
                    () -> {
                        if (computeCount.decrementAndGet() == 0) {
                            sendSelfPatch(createUpdateSubStageTask(state, nextStage));
                        }
                    }
            );
        }
    }

    private void getContainerWhenAvailable(
            SelfProvisioningTaskState state, String containerLink, int retryCount,
            AtomicBoolean failedAlready, Runnable callback) {

        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e == null && o.hasBody()) {
                ContainerState containerState =
                        o.getBody(ContainerState.class);
                if (ContainerState.PowerState.RUNNING.equals(containerState.powerState)) {
                    logInfo("System container %s is running", containerState.documentSelfLink);
                    callback.run();
                    return;
                }
            }

            if (retryCount > 0) {
                int retriesRemaining = retryCount - 1;
                logInfo("Retrying to retrieve system container %s. Retries left %d",
                        containerLink, retriesRemaining);
                getHost().schedule(() -> {
                    getContainerWhenAvailable(state, containerLink, retriesRemaining,
                            failedAlready, callback);
                }, RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
            } else {
                // log the error
                logSevere("Failed to get system container %s", containerLink);

                // but patch with error only once
                if (!failedAlready.getAndSet(true)) {
                    SelfProvisioningTaskState body = createUpdateSubStageTask(state, SubStage.ERROR);
                    sendSelfPatch(body);
                }
            }
        };

        sendRequest(Operation
                .createGet(this, containerLink)
                .setCompletion(completionHandler)
        );
    }

    private void removeContainers(SelfProvisioningTaskState state, SubStage nextStage) {

        QueryTask.Query query = QueryTask.Query.Builder.create()
                .addFieldClause(ServiceDocument.FIELD_NAME_KIND,
                        Utils.buildKind(ContainerState.class))
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .build();

        QueryUtil.addListValueClause(queryTask,
                ContainerState.FIELD_NAME_PARENT_LINK,
                state.computeResourceLinks);

        final Set<String> containerLinks = new HashSet<>();
        QueryTaskClientHelper.create(ComputeState.class)
                .setQueryTask(queryTask)
                .setResultHandler((r, e) -> {
                    if (e != null) {
                        failTask("Error while querying for existing ContainerSates", e);
                    } else if (r.hasResult()) {
                        String link = r.getDocumentSelfLink();
                        if (!link.contains(AGENT_CONTAINER_DESCRIPTION_ID)) {
                            logInfo("ContainerState %s found.", link);
                            containerLinks.add(link);
                        }
                    } else {
                        if (containerLinks.size() > 0) {
                            // send delete request and move to the next stage
                            RequestBrokerState request = new RequestBrokerState();
                            request.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
                            request.resourceType = ResourceType.CONTAINER_TYPE.getName();
                            request.resourceLinks = containerLinks;

                            createDocument(state, RequestBrokerFactoryService.SELF_LINK, request,
                                    nextStage);
                        } else {
                            sendSelfPatch(createUpdateSubStageTask(state, nextStage));
                        }
                    }
                }).sendWith(getHost());
    }

    private void provisionSelfContainerClusterTemplate(SelfProvisioningTaskState state,
            SubStage nextStage) {
        RequestBrokerState requestBrokerState = new RequestBrokerState();
        requestBrokerState.documentSelfLink = getSelfLink() + "-container";
        requestBrokerState.resourceType = ResourceType.CONTAINER_TYPE.getName();
        requestBrokerState.resourceCount = state.clusterSize;
        requestBrokerState.resourceDescriptionLink = state.compositeDescriptionLink;
        requestBrokerState.customProperties = state.customProperties;

        requestBrokerState.serviceTaskCallback = ServiceTaskCallback
                .create(state.documentSelfLink,
                        TaskStage.STARTED, SubStage.CONTAINER_PROVISIONING_COMPLETED,
                        TaskStage.STARTED, SubStage.ERROR);

        createDocument(state, RequestBrokerFactoryService.SELF_LINK, requestBrokerState, nextStage);
    }

    private void createComputeProvisioningTask(SelfProvisioningTaskState state, SubStage nextStage) {
        RequestBrokerState requestBrokerState = new RequestBrokerState();
        requestBrokerState.documentSelfLink = getSelfLink() + "-compute";
        requestBrokerState.resourceType = ResourceType.CONTAINER_HOST_TYPE.getName();
        requestBrokerState.operation = ProvisionContainerHostsTaskService.PROVISION_CONTAINER_HOSTS_OPERATITON;
        requestBrokerState.resourceCount = state.clusterSize;
        requestBrokerState.resourceDescriptionLink = state.computeDescriptionLink;
        requestBrokerState.customProperties = state.customProperties;

        requestBrokerState.serviceTaskCallback = ServiceTaskCallback
                .create(state.documentSelfLink,
                        TaskStage.STARTED, SubStage.COMPUTE_PROVISIONING_COMPLETED,
                        TaskStage.STARTED, SubStage.ERROR);

        createDocument(state, RequestBrokerFactoryService.SELF_LINK, requestBrokerState, nextStage);
    }

    private void createComputeDescription(SelfProvisioningTaskState state, SubStage nextStage) {
        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc.id = String.format("%s-%s-%s", state.endpointType.name(),
                state.availabilityZoneId, UUID.randomUUID().toString());
        computeDesc.name = state.computeInstanceType;
        computeDesc.zoneId = state.availabilityZoneId;
        computeDesc.instanceAdapterReference = UriUtils.buildUri(getHost(),
                AWSUriPaths.AWS_INSTANCE_ADAPTER);
        computeDesc.supportedChildren = new ArrayList<>(
                Arrays.asList(ComputeType.DOCKER_CONTAINER.toString()));
        computeDesc.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
        computeDesc.customProperties = new HashMap<>();
        computeDesc.customProperties.put(AWSConstants.AWS_SECURITY_GROUP, state.securityGroup);

        //This resourcePool link will be provided as parameter to ResourceAllocationTaskState.resourcePoolLink
        // computeDesc.customProperties.put(
        // ProvisionContainerHostsTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK,
        // getResourcePoolLink(state));

        //This aws authCredential link will be assigned to aws parent compute description authCredentialLink
        // computeDesc.customProperties.put(
        // ProvisionContainerHostsTaskState.FIELD_NAME_CUSTOM_PROP_AUTH_CRED_LINK,
        // getAuthCredentialLink(state));

        sendRequest(Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(computeDesc)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("ComputeDesc can't be created: " + computeDesc.id, e);
                        return;
                    }
                    ComputeDescription cd = o.getBody(ComputeDescription.class);
                    logInfo("ComputeDesc created: %s", cd.documentSelfLink);

                    SelfProvisioningTaskState body = createUpdateSubStageTask(state, nextStage);
                    body.computeDescriptionLink = cd.documentSelfLink;
                    sendSelfPatch(body);
                }));
    }

    private void queryForContainerHosts(SelfProvisioningTaskState state, SubStage nextStage) {
        QueryTask q = QueryUtil.buildPropertyQuery(ComputeState.class,
                ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, getResourcePoolLink(state));
        q = QueryUtil.addExpandOption(q);

        final Set<String> computeLinks = new HashSet<>();
        QueryTaskClientHelper.create(ComputeState.class)
                .setQueryTask(q)
                .setResultHandler((r, e) -> {
                    if (e != null) {
                        failTask("Error while querying for ComputeSates with resourcePool: "
                                + getResourcePoolLink(state), e);
                    } else if (r.hasResult()) {
                        if (r.getResult().adapterManagementReference != null) {
                            // skip parent aws compute state
                            return;
                        }
                        logInfo("ComputeState %s with resourcePool %s found.",
                                r.getDocumentSelfLink(), getResourcePoolLink(state));
                        computeLinks.add(r.getDocumentSelfLink());
                    } else if (!computeLinks.isEmpty()) {
                        SelfProvisioningTaskState body = createUpdateSubStageTask(state,
                                SubStage.COMPUTE_PROVISIONING_COMPLETED);
                        body.computeResourceLinks = computeLinks;
                        sendSelfPatch(body);
                    } else {
                        // move to the next stage to provision VMs
                        sendSelfPatch(createUpdateSubStageTask(state, nextStage));
                    }
                }).sendWith(getHost());

    }

    private void createResourcePool(SelfProvisioningTaskState state, SubStage nextStage) {
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.documentSelfLink = getResourcePoolLink(state);
        poolState.name = String.format("%s-%s-%s", RESOURCE_POOL_ID, state.endpointType.name(),
                state.availabilityZoneId);
        poolState.id = poolState.name;
        poolState.projectName = state.endpointType.name();

        GroupResourcePlacementState placementState = new GroupResourcePlacementState();
        placementState.maxNumberInstances = 10;
        placementState.resourcePoolLink = poolState.documentSelfLink;
        placementState.name = String.format("%s-%s-%s", "placement", state.endpointType.name(),
                state.availabilityZoneId);
        placementState.documentSelfLink = placementState.name;
        placementState.priority = 1;

        OperationJoin.create(
                Operation.createPost(this, ResourcePoolService.FACTORY_LINK)
                        .setBody(poolState),
                Operation.createPost(this, GroupResourcePlacementService.FACTORY_LINK)
                        .setBody(placementState))
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        failTask("ResourcePool or GroupPlacement can't be created: "
                                + Utils.toString(exs), exs.values().iterator().next());
                        return;
                    }
                    sendSelfPatch(createUpdateSubStageTask(state, nextStage));
                }).sendWith(this);
    }

    private void createAuthCredentials(SelfProvisioningTaskState state, SubStage nextStage) {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.documentSelfLink = getAuthCredentialLink(state);
        auth.type = AuthCredentialsType.PublicKey.name();
        auth.userEmail = ENDPOINT_AUTH_CREDENTIALS_ID;
        auth.privateKeyId = state.endpointAuthKey;
        auth.privateKey = state.endpointAuthPrivateKey;

        createDocument(state, AuthCredentialsService.FACTORY_LINK, auth, nextStage);
    }

    private String getResourcePoolLink(SelfProvisioningTaskState state) {
        return UriUtils.buildUriPath(
                ResourcePoolService.FACTORY_LINK,
                String.format("%s-%s-%s", RESOURCE_POOL_ID, state.endpointType.name(),
                        state.availabilityZoneId));
    }

    private String getAuthCredentialLink(SelfProvisioningTaskState state) {
        return UriUtils.buildUriPath(
                AuthCredentialsService.FACTORY_LINK,
                String.format("%s-%s-%s", ENDPOINT_AUTH_CREDENTIALS_ID, state.endpointType.name(),
                        state.endpointAuthKey));
    }

    private void createIfNotExists(SelfProvisioningTaskState state,
            Class<? extends ServiceDocument> type, String selfLink, SubStage nextStage,
            Consumer<SubStage> callback) {
        AtomicBoolean found = new AtomicBoolean();
        QueryTaskClientHelper.create(type)
                .setDocumentLink(selfLink)
                .setResultHandler((r, e) -> {
                    if (e != null) {
                        failTask("Error retrieving " + selfLink, e);
                        return;
                    } else if (r.hasResult()) {
                        found.set(true);
                    } else if (found.get()) {
                        logInfo("Service document found: %s", selfLink);
                        sendSelfPatch(createUpdateSubStageTask(state, nextStage));
                    } else {
                        callback.accept(nextStage);
                    }
                }).sendWith(getHost());
    }

    private void createDocument(SelfProvisioningTaskState state,
            String factoryLink, ServiceDocument body, SubStage nextStage) {
        sendRequest(Operation.createPost(this, factoryLink)
                .setBody(body)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Resource can't be created: " + state.documentSelfLink, e);
                        return;
                    }
                    logInfo("Resource created: %s", state.documentSelfLink);
                    sendSelfPatch(createUpdateSubStageTask(state, nextStage));
                }));
    }

    //TODO: extract this in util method
    private String loadResource(String file) throws Throwable {
        try (InputStream is = getClass().getResourceAsStream(file);
                InputStreamReader r = new InputStreamReader(is)) {
            char[] buf = new char[1024];
            final StringBuilder out = new StringBuilder();

            int length = r.read(buf);
            while (length > 0) {
                out.append(buf, 0, length);
                length = r.read(buf);
            }
            return out.toString();
        }
    }
}
