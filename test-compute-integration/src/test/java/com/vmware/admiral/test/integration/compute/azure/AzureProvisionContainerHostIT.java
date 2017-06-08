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

package com.vmware.admiral.test.integration.compute.azure;

import static org.junit.Assert.assertNotNull;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.endpoint.EndpointAdapterService;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.compute.ProvisionContainerHostsTaskService;
import com.vmware.admiral.test.integration.BaseIntegrationSupportIT;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AzureProvisionContainerHostIT extends BaseIntegrationSupportIT {

    private static final String SUBSCRIPTION_PROP = "test.azure.subscription.id";
    private static final String TENANT_ID_PROP = "test.azure.tenant.id";
    private static final String ACCESS_KEY_PROP = "test.azure.access.key";
    private static final String ACCESS_SECRET_PROP = "test.azure.secret.key";

    private static final String VM_ADMIN_USERNAME = "test.azure.vm.admin.username";
    private static final String VM_ADMIN_PASSWORD = "test.azure.vm.admin.password";

    private static final String REGION_ID_PROP = "test.azure.region.id";

    private String endpointType;
    private EndpointState endpoint;
    private Set<String> resourceLinks;

    @Before
    public void setUp() throws Throwable {

        endpointType = getEndpointType();
        endpoint = createEndpoint(endpointType, TestDocumentLifeCycle.NO_DELETE);
        triggerAndWaitForEndpointEnumeration(endpoint);
    }

    @Override
    @After
    public void baseTearDown() throws Exception {
        deleteDockerHosts();
        super.baseTearDown();
        delete(UriUtils.buildUriPath(EndpointAdapterService.SELF_LINK, endpoint.documentSelfLink));
    }

    private void deleteDockerHosts() throws Exception {
        if (this.resourceLinks == null) {
            return;
        }
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_HOST_TYPE.getName();
        request.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
        request.resourceLinks = this.resourceLinks;
        request.tenantLinks = endpoint.tenantLinks;

        RequestBrokerState state = postDocument(RequestBrokerFactoryService.SELF_LINK, request);
        waitForTaskToComplete(state.documentSelfLink);
    }

    @Override
    protected String getEndpointType() {
        return EndpointType.azure.name();
    }

    @Override
    protected void extendEndpoint(EndpointState endpoint) {
        endpoint.endpointProperties.put("privateKeyId", getTestRequiredProp(ACCESS_KEY_PROP));
        endpoint.endpointProperties.put("privateKey", getTestRequiredProp(ACCESS_SECRET_PROP));
        endpoint.endpointProperties.put("userLink", getTestRequiredProp(SUBSCRIPTION_PROP));
        endpoint.endpointProperties.put("regionId", getTestProp(REGION_ID_PROP, "westus"));
        endpoint.endpointProperties.put(AZURE_TENANT_ID, getTestRequiredProp(TENANT_ID_PROP));
    }

    @Test
    public void testProvision() throws Throwable {
        ComputeDescription cd = createComputeDescription();

        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_HOST_TYPE.getName();
        request.operation = ProvisionContainerHostsTaskService.PROVISION_CONTAINER_HOSTS_OPERATION;
        request.resourceDescriptionLink = cd.documentSelfLink;
        request.tenantLinks = endpoint.tenantLinks;
        request.resourceCount = 1;

        RequestBrokerState result = postDocument(RequestBrokerFactoryService.SELF_LINK, request);

        waitForTaskToComplete(result.documentSelfLink);

        RequestBrokerState provisionRequest = getDocument(result.documentSelfLink,
                RequestBrokerState.class);
        assertNotNull(provisionRequest);
        assertNotNull(provisionRequest.resourceLinks);
        this.resourceLinks = provisionRequest.resourceLinks;
    }

    protected ComputeDescription createComputeDescription()
            throws Exception {

        ComputeDescription computeDesc = prepareComputeDescription();

        ComputeDescription computeDescription = postDocument(ComputeDescriptionService.FACTORY_LINK,
                computeDesc, TestDocumentLifeCycle.FOR_DELETE);

        return computeDescription;
    }

    protected ComputeDescription prepareComputeDescription() throws Exception {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.userEmail = getTestRequiredProp(VM_ADMIN_USERNAME);
        auth.privateKey = getTestRequiredProp(VM_ADMIN_PASSWORD);
        auth.documentSelfLink = UUID.randomUUID().toString();
        auth = postDocument(AuthCredentialsService.FACTORY_LINK, auth,
                TestDocumentLifeCycle.FOR_DELETE);

        String id = name(getEndpointType(), "test", UUID.randomUUID().toString());
        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc.id = id;
        computeDesc.name = "dvm" + String.valueOf(System.currentTimeMillis() / 1000);
        computeDesc.instanceType = "small";
        computeDesc.tenantLinks = endpoint.tenantLinks;
        computeDesc.customProperties = new HashMap<>();
        computeDesc.customProperties
                .put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, "coreos");
        computeDesc.customProperties.put(ComputeProperties.ENDPOINT_LINK_PROP_NAME,
                endpoint.documentSelfLink);

        computeDesc.authCredentialsLink = auth.documentSelfLink;

        computeDesc.customProperties.put(ComputeProperties.RESOURCE_GROUP_NAME,
                "testResourceGroup" + String.valueOf(System.currentTimeMillis()));
        return computeDesc;
    }
}
