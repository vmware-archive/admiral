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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.request.RequestBrokerService;
import com.vmware.admiral.test.integration.SimpleHttpsClient;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.xenon.common.Operation;

public class AwsWordpressProvisionIT extends BaseComputeProvisionIT {

    private static final String SECURITY_GROUP_PROP = "test.aws.security.group";
    private static final String ACCESS_KEY_PROP = "test.aws.access.key";
    private static final String ACCESS_SECRET_PROP = "test.aws.secret.key";
    private static final String REGION_ID_PROP = "test.aws.region.id";
    private static final String WP_PATH = "mywordpresssite";
    private static final int STATUS_CODE_WAIT_POLLING_RETRY_COUNT = 300; //5 min

    protected String importTemplate(String filePath) throws Exception {
        String template = CommonTestStateFactory.getFileContent(filePath);

        URI uri = URI.create(getBaseUrl()
                + buildServiceUri(CompositeDescriptionContentService.SELF_LINK));

        Map<String, String> headers = Collections
                .singletonMap(Operation.CONTENT_TYPE_HEADER,
                        UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML);

        SimpleHttpsClient.HttpResponse httpResponse = SimpleHttpsClient
                .execute(SimpleHttpsClient.HttpMethod.POST, uri.toString(), template, headers,
                        null);
        String location = httpResponse.headers.get(Operation.LOCATION_HEADER).get(0);
        assertNotNull("Missing location header", location);
        return URI.create(location).getPath();
    }

    @Override
    protected RequestBrokerService.RequestBrokerState allocateAndProvision(
            String resourceDescriptionLink) throws Exception {
        RequestBrokerService.RequestBrokerState allocateRequest = requestCompute(
                resourceDescriptionLink, true, null);

        allocateRequest = getDocument(allocateRequest.documentSelfLink,
                RequestBrokerService.RequestBrokerState.class);

        assertNotNull(allocateRequest.resourceLinks);
        System.out.println(allocateRequest.resourceLinks);
        for (String link : allocateRequest.resourceLinks) {
            ComputeState computeState = getDocument(link,
                    ComputeState.class);
            assertNotNull(computeState);
        }

        return allocateRequest;
    }

    @Override
    protected void doWithResources(List<String> resourceLinks) throws Throwable {
        CompositeComponent compositeComponent = getDocument(resourceLinks.get(0),
                CompositeComponent.class);
        ComputeState wordPress = null;
        for (String link : compositeComponent.componentLinks) {
            ComputeState computeState = getDocument(link, ComputeState.class);

            if (computeState.name.contains("wordpress")) {
                wordPress = computeState;
                break;
            }
        }

        if (wordPress == null) {
            fail("Unable to find the ComputeState corresponding to the Wordpress node");
        }

        String address = wordPress.address;
        URI uri = URI.create(String.format("http://%s/%s", address, WP_PATH));

        try {
            waitForStatusCode(uri, Operation.STATUS_CODE_OK, STATUS_CODE_WAIT_POLLING_RETRY_COUNT);
        } catch (Exception eInner) {
            logger.error("Failed to verify wordpress connection: %s", eInner.getMessage());
            fail();
        }
    }

    @Override
    protected EndpointType getEndpointType() {
        return EndpointType.aws;
    }

    @Override
    protected void extendEndpoint(EndpointService.EndpointState endpoint) {
        endpoint.endpointProperties.put("privateKeyId", getTestRequiredProp(ACCESS_KEY_PROP));
        endpoint.endpointProperties.put("privateKey", getTestRequiredProp(ACCESS_SECRET_PROP));
        endpoint.endpointProperties.put("regionId", getTestProp(REGION_ID_PROP, "us-east-1"));
    }

    @Override
    protected String getResourceDescriptionLink() throws Exception {
        return importTemplate("WordPress_with_MySQL_compute.yaml");
    }
}
