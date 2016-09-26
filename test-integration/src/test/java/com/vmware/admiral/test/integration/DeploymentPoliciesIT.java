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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.DeploymentPolicyService;
import com.vmware.admiral.compute.container.DeploymentPolicyService.DeploymentPolicy;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState.SubStage;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.xenon.common.Utils;

public class DeploymentPoliciesIT extends BaseProvisioningOnCoreOsIT {

    @Before
    public void setUp() throws Exception {
        setupCoreOsHost(DockerAdapterType.API);
    }

    @After
    public void tearDown() throws Exception {
        GroupResourcePlacementState placement = getDocument(
                GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK,
                GroupResourcePlacementState.class);
        placement.deploymentPolicyLink = null;
        sendRequest(HttpMethod.PUT, placement.documentSelfLink, Utils.toJson(placement));
    }

    @Test
    public void testDeploymentPolicyOnPolicy() throws Exception {
        DeploymentPolicy deploymentPolicy = createDeploymentPolicy();

        ContainerDescription containerDescription = createContainerDescription(deploymentPolicy);

        RequestBrokerState request = requestContainer(containerDescription.documentSelfLink);

        validateContainerRequestSuccess(request);

        GroupResourcePlacementState placement = getDocument(
                GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK,
                GroupResourcePlacementState.class);
        placement.deploymentPolicyLink = deploymentPolicy.documentSelfLink;
        sendRequest(HttpMethod.PUT, placement.documentSelfLink, Utils.toJson(placement));

        request = requestContainer(containerDescription.documentSelfLink);

        validateContainerRequestSuccess(request);
    }

    @Test
    public void testDeploymentPolicyOnHost() throws Exception {
        DeploymentPolicy deploymentPolicy = createDeploymentPolicy();

        ContainerDescription containerDescription = createContainerDescription(deploymentPolicy);

        RequestBrokerState request = requestContainer(containerDescription.documentSelfLink);

        validateContainerRequestSuccess(request);

        dockerHostCompute.customProperties.put(
                ContainerHostService.CUSTOM_PROPERTY_DEPLOYMENT_POLICY,
                deploymentPolicy.documentSelfLink);
        sendRequest(HttpMethod.PUT, dockerHostCompute.documentSelfLink,
                Utils.toJson(dockerHostCompute));

        request = requestContainer(containerDescription.documentSelfLink);

        validateContainerRequestSuccess(request);
    }

    private void validateContainerRequestSuccess(RequestBrokerState request) throws Exception {
        waitForStateChange(
                request.documentSelfLink,
                (body) -> {
                    RequestBrokerState state = Utils.fromJson(body, RequestBrokerState.class);
                    if (state.taskSubStage.equals(SubStage.COMPLETED)) {
                        return true;
                    } else if (state.taskSubStage.equals(SubStage.ERROR)) {
                        fail("Provisioning should succeed, but was request state was error.");
                        return true;
                    } else {
                        return false;
                    }
                });
    }

    private ContainerDescription createContainerDescription(DeploymentPolicy deploymentPolicy)
            throws Exception {
        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.image = "httpd";
        containerDesc.name = "test-deployment-policies";
        containerDesc.portBindings = new PortBinding[] { PortBinding
                .fromDockerPortMapping(DockerPortMapping.fromString("8080::80")) };
        containerDesc.deploymentPolicyId = extractId(deploymentPolicy.documentSelfLink);

        containerDesc = postDocument(ContainerDescriptionService.FACTORY_LINK, containerDesc);
        documentsForDeletion.add(containerDesc);

        return containerDesc;
    }

    private DeploymentPolicy createDeploymentPolicy() throws Exception {
        DeploymentPolicy policy = new DeploymentPolicy();
        policy.name = DeploymentPoliciesIT.class.getSimpleName();
        policy.description = "test " + policy.name + " policy descrition";
        policy = postDocument(DeploymentPolicyService.FACTORY_LINK, policy);
        documentsForDeletion.add(policy);
        return policy;
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage, RegistryType registryType)
            throws Exception {
        return null;
    }

}
