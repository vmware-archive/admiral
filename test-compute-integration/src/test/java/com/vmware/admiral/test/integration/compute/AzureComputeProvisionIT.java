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

package com.vmware.admiral.test.integration.compute;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;

import java.util.HashMap;
import java.util.UUID;

import org.junit.Ignore;

import com.vmware.admiral.compute.endpoint.EndpointService.EndpointState;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

@Ignore("We have to modify first the CI jobs to pass the Azure accessKey,secret,subscriptionId and tenant")
public class AzureComputeProvisionIT extends BaseComputeProvisionIT {

    private static final String SUBSCRIPTION_PROP = "test.azure.subscription.id";
    private static final String TENANT_ID_PROP = "test.azure.tenant.id";
    private static final String ACCESS_KEY_PROP = "test.azure.access.key";
    private static final String ACCESS_SECRET_PROP = "test.azure.secret.key";

    private static final String VM_ADMIN_USERNAME = "test.azure.vm.admin.username";
    private static final String VM_ADMIN_PASSWORD = "test.azure.vm.admin.password";

    private static final String REGION_ID_PROP = "test.azure.region.id";

    @Override
    protected EndpointType getEndpointType() {
        return EndpointType.azure;
    }

    @Override
    protected void extendEndpoint(EndpointState endpoint) {
        endpoint.privateKeyId = getTestRequiredProp(ACCESS_KEY_PROP);
        endpoint.privateKey = getTestRequiredProp(ACCESS_SECRET_PROP);
        endpoint.userLink = getTestRequiredProp(SUBSCRIPTION_PROP);
        endpoint.regionId = getTestProp(REGION_ID_PROP, "westus");
        endpoint.customProperties = new HashMap<>();
        endpoint.customProperties.put(AZURE_TENANT_ID, getTestRequiredProp(TENANT_ID_PROP));
    }

    @Override
    protected void extendComputeDescription(ComputeDescription computeDescription)
            throws Exception {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.userEmail = getTestRequiredProp(VM_ADMIN_USERNAME);
        auth.privateKey = getTestRequiredProp(VM_ADMIN_PASSWORD);
        auth.documentSelfLink = UUID.randomUUID().toString();

        auth = postDocument(AuthCredentialsService.FACTORY_LINK, auth, documentLifeCycle);

        computeDescription.authCredentialsLink = auth.documentSelfLink;

        computeDescription.customProperties.put(ComputeProperties.RESOURCE_GROUP_NAME,
                "testResourceGroup" + String.valueOf(System.currentTimeMillis()));
    }

}
