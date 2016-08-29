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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.host.HostInitServiceHelper;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.compute.aws.ProvisionContainerHostsTaskService.ProvisionContainerHostsTaskState;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.tasks.ResourceAllocationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceAllocationTaskService.ResourceAllocationTaskState;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class ProvisionContainerHostsTaskServiceTest extends RequestBaseTest {
    // Security group for AWS. Fixed for now
    // FIXME: This should be the current configured security group in AWS.
    private static final String SECURITY_GROUP = "admiral-security-group";

    // AWS instance type.
    private static final String T2_MICRO_INSTANCE_TYPE = "t2.micro";
    protected static final String T2_LARGE_INSTANCE_TYPE = "t2.large";

    // AWS east-1 zone ID
    public static final String EAST_1_ZONE_ID = "us-east-1";
    // AWS west-1 zone ID
    public static final String WEST_1_ZONE_ID = "us-west-1";

    // Core OS image for us-east-1
    protected static final String EAST_1_IMAGE_ID = "ami-cbfdb2a1";
    protected static final String WEST_1_IMAGE_ID = "ami-0eacc46e";

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        ConfigurationState config = new ConfigurationState();
        config.key = ProvisionContainerHostsTaskService.HOST_PROVISIONING_PROP_NAME;
        config.value = "true";
        doOperation(config,
                UriUtils.buildUri(host, UriUtils.buildUriPath(ManagementUriParts.CONFIG_PROPS, config.key)),
                false, Action.PUT);
        HostInitServiceHelper.startServiceFactories(host, ProvisionContainerHostsTaskService.class);
    }

    @Test
    public void testProvisionDockerHostVMsOnAWS() throws Throwable {
        AuthCredentialsServiceState coreOsUserAuthCredentials = doPost(
                createCoreOsUserAuthCredentials(), AuthCredentialsService.FACTORY_LINK);

        AuthCredentialsServiceState awsAuthCredentials = doPost(
                createAwsAuthCredentials(), AuthCredentialsService.FACTORY_LINK);

        ComputeDescription awsComputeDesc = doPost(createAwsCoreOsComputeDescription(
                coreOsUserAuthCredentials.documentSelfLink, awsAuthCredentials.documentSelfLink),
                ComputeDescriptionService.FACTORY_LINK);

        RequestBrokerState request = startRequest(
                createRequestState(awsComputeDesc.documentSelfLink));
        waitForRequestToComplete(request);
        request = getDocument(RequestBrokerState.class, request.documentSelfLink);

        // verify the parent compute description is created:
        ComputeDescription parentComputeDesc = getDocument(ComputeDescription.class,
                ProvisionContainerHostsTaskService.AWS_PARENT_COMPUTE_DESC_LINK);
        assertNotNull(parentComputeDesc);
        assertEquals(ProvisionContainerHostsTaskService.AWS_PARENT_COMPUTE_DESC_ID,
                parentComputeDesc.id);
        assertEquals(Arrays.asList(ComputeType.VM_GUEST.toString()),
                parentComputeDesc.supportedChildren);
        assertEquals(ComputeDescription.ENVIRONMENT_NAME_AWS, parentComputeDesc.environmentName);
        assertEquals(UriUtils.buildUri(host, AWSUriPaths.AWS_INSTANCE_ADAPTER),
                parentComputeDesc.instanceAdapterReference);

        // verify the parent aws compute state is created:
        ComputeState awsComputeHost = getDocument(ComputeState.class,
                ProvisionContainerHostsTaskService.AWS_PARENT_COMPUTE_LINK);
        assertNotNull(awsComputeHost);
        assertEquals(ProvisionContainerHostsTaskService.AWS_PARENT_COMPUTE_ID, awsComputeHost.id);
        assertEquals(ProvisionContainerHostsTaskService.AWS_PARENT_COMPUTE_DESC_LINK,
                awsComputeHost.descriptionLink);
        assertEquals(resourcePool.documentSelfLink, awsComputeHost.resourcePoolLink);

        // verify the coreOs diskState is created:
        DiskState diskState = getDocument(DiskState.class,
                ProvisionContainerHostsTaskService.AWS_DISK_STATE_LINK);
        assertNotNull(diskState);
        assertEquals(ProvisionContainerHostsTaskService.AWS_DISK_STATE_ID, diskState.id);
        assertEquals(DiskType.HDD, diskState.type);
        assertEquals(URI.create(ProvisionContainerHostsTaskService.AWS_COREOS_IMAGE_ID),
                diskState.sourceImageReference);
        assertEquals(ProvisionContainerHostsTaskService.AWS_CLOUD_CONFIG_PATH,
                diskState.bootConfig.files[0].path);
        assertNotNull(diskState.bootConfig.files[0].contents);

        ResourceAllocationTaskState allocationState = getDocument(
                ResourceAllocationTaskState.class,
                UriUtils.buildUriPath(ResourceAllocationTaskService.FACTORY_LINK,
                        extractId(request.documentSelfLink)));
        assertNotNull(allocationState);
        assertEquals(TaskStage.FINISHED, allocationState.taskInfo.stage);

        assertEquals(3, request.resourceLinks.size());
    }

    public AuthCredentialsServiceState createAwsAuthCredentials() {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.documentSelfLink = UUID.randomUUID().toString();
        authCredentials.type = AuthCredentialsType.PublicKey.name();
        // FIXME: add the right username
        authCredentials.userEmail = "core";
        // FIXME: add the right aws key
        authCredentials.privateKey = CommonTestStateFactory
                .getFileContent("docker-host-private-key.PEM");

        return authCredentials;
    }

    public AuthCredentialsServiceState createCoreOsUserAuthCredentials() {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.documentSelfLink = UUID.randomUUID().toString();
        authCredentials.type = AuthCredentialsType.PublicKey.name();
        // FIXME: add the right username
        authCredentials.userEmail = "core";
        // FIXME: add the right aws key
        authCredentials.privateKey = CommonTestStateFactory
                .getFileContent("docker-host-private-key.PEM");

        return authCredentials;
    }

    public ComputeDescription createAwsCoreOsComputeDescription(String userAuthCredentials,
            String awsAuthCredentials)
            throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc.name = T2_MICRO_INSTANCE_TYPE;// TODO: check if this really matter?
        computeDesc.supportedChildren = new ArrayList<>(
                Arrays.asList(ComputeType.DOCKER_CONTAINER.toString()));
        computeDesc.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
        computeDesc.id = UUID.randomUUID().toString();
        computeDesc.authCredentialsLink = userAuthCredentials;

        // TODO: This should be the host:port on the Go adapter service process:
        computeDesc.instanceAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_INSTANCE_ADAPTER);
        computeDesc.zoneId = EAST_1_ZONE_ID;

        computeDesc.customProperties = new HashMap<>();
        computeDesc.customProperties.put(AWSConstants.AWS_SECURITY_GROUP, SECURITY_GROUP);

        // This resourcePool link will be provided as parameter to
        // ResourceAllocationTaskState.resourcePoolLink
        computeDesc.customProperties.put(
                ProvisionContainerHostsTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK,
                createResourcePool().documentSelfLink);

        // This aws authCredential link will be assigned to aws parent compute description
        // authCredentialLink
        computeDesc.customProperties.put(
                ProvisionContainerHostsTaskState.FIELD_NAME_CUSTOM_PROP_AUTH_CRED_LINK,
                awsAuthCredentials);

        return computeDesc;
    }

    public static RequestBrokerState createRequestState(String resourceDescLink) {
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_HOST_TYPE.getName();
        request.operation = ProvisionContainerHostsTaskService.PROVISION_CONTAINER_HOSTS_OPERATITON;
        request.resourceDescriptionLink = resourceDescLink;
        request.resourceCount = 3;

        return request;
    }

    @Override
    protected RequestBrokerState startRequest(RequestBrokerState request) throws Throwable {
        RequestBrokerState requestState = doPost(request, RequestBrokerFactoryService.SELF_LINK);
        assertNotNull(requestState);
        return requestState;
    }
}
