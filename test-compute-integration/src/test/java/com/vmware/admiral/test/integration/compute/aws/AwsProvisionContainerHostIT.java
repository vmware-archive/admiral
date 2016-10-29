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

package com.vmware.admiral.test.integration.compute.aws;

import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.request.compute.ProvisionContainerHostsTaskService;
import com.vmware.admiral.request.compute.ProvisionContainerHostsTaskService.DockerHostDescription;
import com.vmware.admiral.request.compute.ProvisionContainerHostsTaskService.ProvisionContainerHostsTaskState;
import com.vmware.admiral.test.integration.BaseIntegrationSupportIT;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;

public class AwsProvisionContainerHostIT extends BaseIntegrationSupportIT {

    private static final String ACCESS_KEY_PROP = "test.aws.access.key";
    private static final String ACCESS_SECRET_PROP = "test.aws.secret.key";
    private static final String REGION_ID_PROP = "test.aws.region.id";

    private EndpointType endpointType;
    private EndpointState endpoint;

    @Before
    public void setUp() throws Throwable {

        endpointType = getEndpointType();
        endpoint = createEndpoint(endpointType, TestDocumentLifeCycle.NO_DELETE);
    }

    @Override
    @After
    public void baseTearDown() throws Exception {
        // super.baseTearDown();

        // delete(UriUtils.buildUriPath(EndpointAdapterService.SELF_LINK,
        // endpoint.documentSelfLink));
    }

    @Override
    protected EndpointType getEndpointType() {
        return EndpointType.aws;
    }

    @Override
    protected void extendEndpoint(EndpointState endpoint) {
        endpoint.endpointProperties.put("privateKeyId", getTestRequiredProp(ACCESS_KEY_PROP));
        endpoint.endpointProperties.put("privateKey", getTestRequiredProp(ACCESS_SECRET_PROP));
        endpoint.endpointProperties.put("regionId", getTestProp(REGION_ID_PROP, "us-east-1"));
    };

    @Test
    public void testProvision() throws Throwable {
        DockerHostDescription hostDescription = new DockerHostDescription();
        hostDescription.name = "belvm" + String.valueOf(System.currentTimeMillis() / 1000);
        hostDescription.instanceType = "t2.micro";
        hostDescription.imageType = "coreos";

        ProvisionContainerHostsTaskState state = new ProvisionContainerHostsTaskState();
        state.endpointLink = endpoint.documentSelfLink;
        state.resourceCount = 1;
        state.hostDescription = hostDescription;
        state.tenantLinks = getTenantLinks();
        ProvisionContainerHostsTaskState request = postDocument(
                ProvisionContainerHostsTaskService.FACTORY_LINK,
                state);

        waitForTaskToComplete(request.documentSelfLink);

        ProvisionContainerHostsTaskState provisionRequest = getDocument(request.documentSelfLink,
                ProvisionContainerHostsTaskState.class);
        assertNotNull(provisionRequest);
        assertNotNull(provisionRequest.resourceLinks);
    }
}
