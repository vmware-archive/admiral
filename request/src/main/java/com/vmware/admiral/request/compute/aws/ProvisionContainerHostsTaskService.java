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

package com.vmware.admiral.request.compute.aws;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.AssertUtil.assertState;
import static com.vmware.admiral.common.util.PropertyUtils.mergeLists;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.security.EncryptionUtils;
import com.vmware.admiral.common.util.KeyUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.SubscriptionUtils;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.request.compute.aws.ProvisionContainerHostsTaskService.ProvisionContainerHostsTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.tasks.ResourceAllocationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceAllocationTaskService.ResourceAllocationTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.QueryTaskClientHelper;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

/** Task for provisioning a number of Docker Host VMs on AWS */
public class ProvisionContainerHostsTaskService
        extends
        AbstractTaskStatefulService<ProvisionContainerHostsTaskService.ProvisionContainerHostsTaskState, ProvisionContainerHostsTaskService.ProvisionContainerHostsTaskState.SubStage> {
    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_CONTAINER_HOSTS;
    public static final String DISPLAY_NAME = "AWS Container Hosts";
    public static final String PROVISION_CONTAINER_HOSTS_OPERATITON = "PROVISION_CONTAINER_HOSTS";

    public static final String AWS_PARENT_COMPUTE_DESC_ID = "aws-parent-compute-desc";
    public static final String AWS_PARENT_COMPUTE_DESC_LINK = UriUtils.buildUriPath(
            ComputeDescriptionService.FACTORY_LINK, AWS_PARENT_COMPUTE_DESC_ID);

    public static final String AWS_PARENT_COMPUTE_ID = "aws-parent-compute";
    public static final String AWS_PARENT_COMPUTE_LINK = UriUtils.buildUriPath(
            ComputeService.FACTORY_LINK, AWS_PARENT_COMPUTE_ID);

    public static final String AWS_DISK_STATE_ID = "aws-disk-state";
    public static final String AWS_DISK_STATE_LINK = UriUtils.buildUriPath(
            DiskService.FACTORY_LINK, AWS_DISK_STATE_ID);

    protected static final String AWS_ROOT_DISK_NAME = "aws-root-disk";
    protected static final String AWS_CLOUD_CONFIG_PATH = "user-data";
    protected static final String AWS_CLOUD_CONFIG_LABEL = "ci-data";
    private static final String AWS_CLOUD_CONFIG_FILE = "/aws-content/cloud_config_coreos.yml";
    private static final String SSH_KEY_PLACEHOLDER = "\\{\\{sshAuthorizedKey\\}\\}";
    private static final String SSH_AUTHORIZED_KEY_PROP = "sshAuthorizedKey";

    // TODO: verify where and how to get this value?
    protected static final String AWS_COREOS_IMAGE_ID = "ami-c35354a9";

    // TODO: This should be mapped to the availability zone (zoneId) of ComputeDesc.
    // Probably, we should query for ComputeStates with such reference and create one per endpoint.
    // Hardcoded for now.
    protected static final String AWS_ENDPOINT_REFERENCE = "http://ec2.us-east-1.amazonaws.com";
    public static final String HOST_PROVISIONING_PROP_NAME = "host.provisioning";

    // cached compute description
    private volatile ComputeDescription computeDescription;

    public static class ProvisionContainerHostsTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ProvisionContainerHostsTaskState.SubStage> {
        public static final String FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK = "__resourcePoolLink";
        public static final String FIELD_NAME_CUSTOM_PROP_AUTH_CRED_LINK = "__authCredentialsLink";
        public static final String FIELD_NAME_CUSTOM_PROP_CONTEXT_ID = "__contextId";

        public static enum SubStage {
            CREATED,
            PARENT_DESCRIPTION_CREATED,
            GENERATE_CERTIFICATE_CREATED,
            PARENT_COMPUTE_STATE_CREATED,
            DISK_STATE_CREATED,
            ALLOCATION_TASK_STARTED,
            ALLOCATION_COMPLETED,
            CONFIGURATION_COMPLETED,
            COMPLETED,
            ERROR;
        }

        /** (Required) The AWS compute description link. */
        @Documentation(description = "The description that defines the requested resource.")
        @PropertyOptions(usage = { PropertyUsageOption.LINK,
                PropertyUsageOption.SINGLE_ASSIGNMENT }, indexing = {
                        PropertyIndexingOption.STORE_ONLY })
        public String computeDescriptionLink;

        /** (Optional- default 1) Number of resources to provision. */
        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT }, indexing = {
                PropertyIndexingOption.STORE_ONLY })
        public long resourceCount;

        /** (Set by a Task) The compute resource links of the provisioned Docker Host VMs */
        @Documentation(description = "The compute resource links of the provisioned Docker Host VMs.")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT }, indexing = {
                PropertyIndexingOption.STORE_ONLY })
        public List<String> resourceLinks;
    }

    public ProvisionContainerHostsTaskService() {
        super(ProvisionContainerHostsTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleCreate(Operation post) {
        sendRequest(Operation
                .createGet(getHost(), UriUtils.buildUriPath(ManagementUriParts.CONFIG_PROPS,
                        ProvisionContainerHostsTaskService.HOST_PROVISIONING_PROP_NAME))
                .setCompletion((res, ex) -> {
                    boolean isHostProvisioningEnabled = false;
                    if (ex != null) {
                        logWarning("Cannot get %s configuration property: %s",
                                ProvisionContainerHostsTaskService.HOST_PROVISIONING_PROP_NAME,
                                ex.getMessage());
                    } else {
                        isHostProvisioningEnabled = Boolean
                                .valueOf(res.getBody(ConfigurationState.class).value);
                    }

                    if (!isHostProvisioningEnabled) {
                        throw new IllegalStateException("Host provisioning is not enabled.");
                    }

                    super.handleCreate(post);
                }));
    }

    @Override
    protected void validateStateOnStart(ProvisionContainerHostsTaskState state)
            throws IllegalArgumentException {
        assertNotEmpty(state.computeDescriptionLink, "resourceDescriptionLink");
        assertState(state.resourceCount > 0, "'resourceCount' must be greather than zero.");
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ProvisionContainerHostsTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated compute resources.");
        }
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        List<String> resourceLinks;
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            ProvisionContainerHostsTaskState patchBody,
            ProvisionContainerHostsTaskState currentState) {
        currentState.resourceLinks = mergeLists(
                currentState.resourceLinks, patchBody.resourceLinks);
        return false;
    }

    @Override
    protected void handleStartedStagePatch(ProvisionContainerHostsTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            createIfNotExists(state, ComputeDescription.class, AWS_PARENT_COMPUTE_DESC_LINK,
                    SubStage.PARENT_DESCRIPTION_CREATED,
                    (nextStage) -> createDefaultAwsParentComputeDescription(state, nextStage,
                            this.computeDescription));
            break;
        case PARENT_DESCRIPTION_CREATED:
            configureComputeDescription(state, null);
            break;
        case GENERATE_CERTIFICATE_CREATED:
            createIfNotExists(state, ComputeState.class, AWS_PARENT_COMPUTE_LINK,
                    SubStage.PARENT_COMPUTE_STATE_CREATED,
                    (nextStage) -> createDefaultAwsParentComputeState(state, nextStage,
                            this.computeDescription));
            break;
        case PARENT_COMPUTE_STATE_CREATED:
            createIfNotExists(state, DiskState.class, AWS_DISK_STATE_LINK,
                    SubStage.DISK_STATE_CREATED,
                    (nextStage) -> createDefaultAwsCoreOsDiskState(state, nextStage));
            break;
        case DISK_STATE_CREATED:
            createAllocationTask(state, null);
            break;
        case ALLOCATION_TASK_STARTED:
            subscribeToResourceAllocationTask(state);
            break;
        case ALLOCATION_COMPLETED:
            queryForProvisionedResources(state);
            break;
        case CONFIGURATION_COMPLETED:
            reconfigureProvisionedResources(state);
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

    private void configureComputeDescription(ProvisionContainerHostsTaskState state,
            ComputeDescription computeDesc) {

        if (computeDesc == null) {
            getComputeDescription(state,
                    (compDesc) -> configureComputeDescription(state, compDesc));
            return;
        }

        KeyPair keyPair = KeyUtil.generateRSAKeyPair();
        storeKeys(state, keyPair, (authCredentialsLink) -> {
            computeDesc.supportedChildren = new ArrayList<>(
                    Arrays.asList(ComputeType.DOCKER_CONTAINER.toString()));
            computeDesc.authCredentialsLink = authCredentialsLink;

            computeDesc.instanceAdapterReference = UriUtils.buildUri(getHost(),
                    AWSUriPaths.AWS_INSTANCE_ADAPTER);

            Operation.createPut(this, state.computeDescriptionLink)
                    .setBody(computeDesc)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failed patching compute description : " + Utils.toString(e),
                                    null);
                            return;
                        }
                        sendSelfPatch(createUpdateSubStageTask(
                                state, SubStage.GENERATE_CERTIFICATE_CREATED));
                    })
                    .sendWith(this);
        });
    }

    private void reconfigureProvisionedResources(
            ProvisionContainerHostsTaskState state) {

        List<Operation> operations = new ArrayList<>();

        ComputeState patchBody = new ComputeState();
        patchBody.customProperties = new HashMap<>();
        patchBody.customProperties.put(
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());
        patchBody.customProperties.put(
                ContainerHostService.DOCKER_HOST_SCHEME_PROP_NAME,
                UriUtils.HTTP_SCHEME);
        patchBody.customProperties.put(
                ContainerHostService.DOCKER_HOST_PORT_PROP_NAME,
                String.valueOf(UriUtils.HTTPS_DEFAULT_PORT));
        patchBody.customProperties.put(ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME, "true");
        patchBody.customProperties.put(ComputeConstants.COMPUTE_HOST_PROP_NAME, "true");

        for (String link : state.resourceLinks) {
            Operation op = Operation.createPatch(this, link)
                    .setBody(patchBody);
            operations.add(op);
        }

        OperationJoin.create(operations)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        failTask("Failed patching compute states : " + Utils.toString(exs), null);
                        return;
                    }
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
                })
                .sendWith(this);
    }

    private void createDefaultAwsParentComputeDescription(ProvisionContainerHostsTaskState state,
            SubStage nextStage, ComputeDescription computeDesc) {
        if (computeDesc == null) {
            getComputeDescription(state,
                    (compDesc) -> createDefaultAwsParentComputeDescription(state, nextStage,
                            compDesc));
            return;
        }

        logInfo("Creating parent compute description: %s", AWS_PARENT_COMPUTE_DESC_LINK);
        ComputeDescription parentComputeDesc = new ComputeDescription();
        parentComputeDesc.documentSelfLink = AWS_PARENT_COMPUTE_DESC_LINK;
        parentComputeDesc.id = AWS_PARENT_COMPUTE_DESC_ID;
        parentComputeDesc.supportedChildren = new ArrayList<>(
                Arrays.asList(ComputeType.VM_GUEST.name()));
        parentComputeDesc.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
        parentComputeDesc.instanceAdapterReference = UriUtils.buildUri(getHost(),
                AWSUriPaths.AWS_INSTANCE_ADAPTER);
        parentComputeDesc.zoneId = computeDesc.zoneId;
        parentComputeDesc.regionId = computeDesc.zoneId;

        if (computeDesc.customProperties != null) {
            parentComputeDesc.authCredentialsLink = computeDesc.customProperties
                    .get(ProvisionContainerHostsTaskState.FIELD_NAME_CUSTOM_PROP_AUTH_CRED_LINK);
        }

        createDocument(state, ComputeDescriptionService.FACTORY_LINK, parentComputeDesc,
                nextStage);
    }

    private void createDefaultAwsParentComputeState(ProvisionContainerHostsTaskState state,
            SubStage nextStage, ComputeDescription computeDesc) {
        if (computeDesc == null) {
            getComputeDescription(state,
                    (compDesc) -> createDefaultAwsParentComputeState(state, nextStage, compDesc));
            return;
        }

        ComputeState awsComputeHost = new ComputeState();
        awsComputeHost.documentSelfLink = AWS_PARENT_COMPUTE_LINK;
        awsComputeHost.id = AWS_PARENT_COMPUTE_ID;
        awsComputeHost.descriptionLink = AWS_PARENT_COMPUTE_DESC_LINK;
        if (computeDesc.customProperties != null) {
            awsComputeHost.resourcePoolLink = computeDesc.customProperties
                    .get(ProvisionContainerHostsTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK);
        }

        createDocument(state, ComputeService.FACTORY_LINK, awsComputeHost, nextStage);
    }

    private void createDefaultAwsCoreOsDiskState(ProvisionContainerHostsTaskState state,
            SubStage nextStage) {
        getSshAuthorizedKey(state, null, (sshAuthorizedKey) -> {
            try {
                DiskState rootDisk = new DiskState();
                rootDisk.documentSelfLink = AWS_DISK_STATE_LINK;
                rootDisk.id = AWS_DISK_STATE_ID;
                rootDisk.name = AWS_ROOT_DISK_NAME;
                rootDisk.type = DiskType.HDD;
                rootDisk.sourceImageReference = URI.create(AWS_COREOS_IMAGE_ID);
                rootDisk.bootConfig = new DiskState.BootConfig();
                rootDisk.bootConfig.label = AWS_CLOUD_CONFIG_LABEL;
                DiskState.BootConfig.FileEntry file = new DiskState.BootConfig.FileEntry();
                file.path = AWS_CLOUD_CONFIG_PATH;
                file.contents = loadResource(AWS_CLOUD_CONFIG_FILE, sshAuthorizedKey);
                rootDisk.bootConfig.files = new DiskState.BootConfig.FileEntry[] { file };

                createDocument(state, DiskService.FACTORY_LINK, rootDisk, nextStage);
            } catch (Throwable t) {
                failTask("Failure creating DiskState", t);
            }
        });
    }

    private void getSshAuthorizedKey(ProvisionContainerHostsTaskState state,
            ComputeDescription computeDesc, Consumer<String> callback) {
        if (computeDesc == null) {
            getComputeDescription(state, (desc) -> getSshAuthorizedKey(state, desc, callback));
            return;
        }

        String authCredentialsLink = computeDesc.authCredentialsLink;
        sendRequest(Operation.createGet(this, authCredentialsLink)
                .setCompletion(
                        (op, ex) -> {
                            if (ex != null) {
                                failTask("Failed retrieving credentials state", ex);
                                return;
                            }

                            AuthCredentialsServiceState credentialsState = op
                                    .getBody(AuthCredentialsServiceState.class);
                            callback.accept(credentialsState.customProperties
                                    .get(SSH_AUTHORIZED_KEY_PROP));
                        }));
    }

    private void createAllocationTask(ProvisionContainerHostsTaskState state,
            ComputeDescription computeDesc) {
        if (computeDesc == null) {
            getComputeDescription(state, (compDesc) -> createAllocationTask(state, compDesc));
            return;
        }

        ResourceAllocationTaskState allocationState = new ResourceAllocationTaskState();
        allocationState.documentSelfLink = getSelfId();
        allocationState.computeDescriptionLink = state.computeDescriptionLink;
        allocationState.diskDescriptionLinks = new ArrayList<>();
        allocationState.diskDescriptionLinks.add(AWS_DISK_STATE_LINK);
        allocationState.resourceCount = state.resourceCount;
        allocationState.computeType = ComputeType.VM_GUEST.name();
        if (computeDesc.customProperties != null) {
            allocationState.resourcePoolLink = computeDesc.customProperties
                    .get(ProvisionContainerHostsTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK);
        }

        if (state.customProperties != null) {
            allocationState.customProperties = state.customProperties;
        } else {
            allocationState.customProperties = new HashMap<>();
        }

        allocationState.customProperties.put(
                ProvisionContainerHostsTaskState.FIELD_NAME_CUSTOM_PROP_CONTEXT_ID, getSelfId());

        allocationState.isMockRequest = DeploymentProfileConfig.getInstance().isTest();

        createDocument(state, ResourceAllocationTaskService.FACTORY_LINK, allocationState,
                SubStage.ALLOCATION_TASK_STARTED);
    }

    private void queryForProvisionedResources(ProvisionContainerHostsTaskState state) {
        QueryTask q = QueryUtil.buildPropertyQuery(ComputeState.class,
                ComputeState.FIELD_NAME_DESCRIPTION_LINK, state.computeDescriptionLink,
                ComputeState.FIELD_NAME_PARENT_LINK, AWS_PARENT_COMPUTE_LINK,
                QuerySpecification.buildCompositeFieldName(
                        ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ProvisionContainerHostsTaskState.FIELD_NAME_CUSTOM_PROP_CONTEXT_ID),
                getSelfId());
        q.taskInfo.isDirect = false;

        List<String> computeResourceLinks = new ArrayList<>((int) state.resourceCount);
        QueryTaskClientHelper
                .create(ComputeState.class)
                .setQueryTask(q)
                .setResultHandler((r, e) -> {
                    if (e != null) {
                        failTask("Failed to query for provisioned resources", e);
                        return;
                    } else if (r.hasResult()) {
                        computeResourceLinks.add(r.getDocumentSelfLink());
                    } else {
                        ProvisionContainerHostsTaskState body = createUpdateSubStageTask(
                                state, SubStage.CONFIGURATION_COMPLETED);
                        body.resourceLinks = computeResourceLinks;
                        sendSelfPatch(body);
                    }

                }).sendWith(getHost());

    }

    private void subscribeToResourceAllocationTask(ProvisionContainerHostsTaskState state) {
        String allocationTaskLink = getCompleteTaskLink(ResourceAllocationTaskService.FACTORY_LINK,
                this);
        StatefulService provisioningService = this;

        Consumer<Operation> notificationConsumer = new Consumer<Operation>() {
            Set<String> finishedTaskLinks = new HashSet<>();

            @Override
            public void accept(Operation update) {

                if (!update.hasBody()) {
                    return;
                }
                ResourceAllocationTaskState resourceAllocState = update
                        .getBody(ResourceAllocationTaskState.class);

                SubscriptionUtils.handleSubscriptionNotifications(provisioningService, update,
                        resourceAllocState.documentSelfLink, resourceAllocState.taskInfo,
                        1, createUpdateSubStageTask(state, SubStage.ALLOCATION_COMPLETED),
                        finishedTaskLinks,
                        false);
            }
        };

        SubscriptionUtils.subscribeToNotifications(this, notificationConsumer, allocationTaskLink);
    }

    private void getComputeDescription(ProvisionContainerHostsTaskState state,
            Consumer<ComputeDescription> callbackFunction) {
        if (this.computeDescription != null) {
            callbackFunction.accept(this.computeDescription);
            return;
        }

        sendRequest(Operation.createGet(this, state.computeDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving compute description state", e);
                        return;
                    }

                    ComputeDescription desc = o.getBody(ComputeDescription.class);
                    this.computeDescription = desc;
                    callbackFunction.accept(this.computeDescription);
                }));
    }

    private void createIfNotExists(ProvisionContainerHostsTaskState state,
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

    private void createDocument(ProvisionContainerHostsTaskState state,
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

    private static String loadResource(String file, String sshAuthorizedKey) throws Throwable {
        try (InputStream is = ProvisionContainerHostsTaskService.class.getResourceAsStream(file);
                InputStreamReader r = new InputStreamReader(is)) {
            char[] buf = new char[1024];
            final StringBuilder out = new StringBuilder();

            int length = r.read(buf);
            while (length > 0) {
                out.append(buf, 0, length);
                length = r.read(buf);
            }
            return out.toString().replaceFirst(SSH_KEY_PLACEHOLDER, sshAuthorizedKey);
        }
    }

    private void storeKeys(ProvisionContainerHostsTaskState state,
            KeyPair keyPair, Consumer<String> callback) {
        AuthCredentialsServiceState credentialsState = new AuthCredentialsServiceState();
        credentialsState.type = AuthCredentialsType.PublicKey.name();
        credentialsState.userEmail = UUID.randomUUID().toString();
        credentialsState.publicKey = KeyUtil.toPEMFormat(keyPair.getPublic());
        credentialsState.privateKey = EncryptionUtils.encrypt(
                KeyUtil.toPEMFormat(keyPair.getPrivate()));

        String sshAuthorizedKey = KeyUtil.toPublicOpenSSHFormat((RSAPublicKey) keyPair.getPublic());
        credentialsState.customProperties = new HashMap<>();
        credentialsState.customProperties.put(SSH_AUTHORIZED_KEY_PROP, sshAuthorizedKey);

        sendRequest(Operation.createPost(this, AuthCredentialsService.FACTORY_LINK)
                .setBody(credentialsState)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to store credentials: %s", Utils.toString(ex));
                    }

                    callback.accept(o.getBody(AuthCredentialsServiceState.class).documentSelfLink);
                }));
    }

    /*
     * Helper method to get the complete task link for the specified factory and service self link
     */
    private static String getCompleteTaskLink(String factoryLink, StatefulService service) {
        StringBuffer allocTaskLink = new StringBuffer(factoryLink);
        allocTaskLink.append(UriUtils.URI_PATH_CHAR);
        allocTaskLink.append(Service.getId(service.getSelfLink()));
        return allocTaskLink.toString();
    }

}
