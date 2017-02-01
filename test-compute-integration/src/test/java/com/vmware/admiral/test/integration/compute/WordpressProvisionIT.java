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

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.compute.env.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.compute.env.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.env.StorageProfileService.StorageProfile;
import com.vmware.admiral.request.RequestBrokerService;
import com.vmware.admiral.test.integration.SimpleHttpsClient;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.compute.aws.AwsComputeProvisionIT;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;

@RunWith(Parameterized.class)
@Ignore("There are changes to wordpress deps, so the scripts have to be updated.")
public class WordpressProvisionIT extends BaseComputeProvisionIT {

    private static final String WP_PATH = "mywordpresssite";
    private static final int STATUS_CODE_WAIT_POLLING_RETRY_COUNT = 300; //5 min

    private static Consumer<EndpointState> awsEndpointExtender = endpointState -> new AwsComputeProvisionIT()
            .extendEndpoint(endpointState);

    private static Runnable awsSetUp = () -> {
    };

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { EndpointType.aws, awsEndpointExtender, awsSetUp }
                //TODO uncomment once the vsphere issues are resolved
                //{ EndpointType.vsphere, vSphereEndpointExtender, vSphereSetUp }
        });
    }

    private final EndpointType endpointType;
    private final Consumer<EndpointState> endpointExtender;
    private final Runnable setUp;

    public WordpressProvisionIT(EndpointType endpointType,
            Consumer<EndpointState> endpointExtender, Runnable setUp) {
        this.endpointType = endpointType;
        this.endpointExtender = endpointExtender;
        this.setUp = setUp;
    }

    @Override
    protected void doSetUp() throws Exception {
        setUp.run();

        createEnvironment(loadComputeProfile(), createNetworkProfile(), new StorageProfile());
    }

    private NetworkProfile createNetworkProfile() throws Exception {
        Query query = QueryTask.Query.Builder.create()
                .addFieldClause(SubnetState.FIELD_NAME_ID, "subnet-ce01b5e4")
                .build();
        QueryTask qt = QueryTask.Builder.createDirectTask().setQuery(query).build();
        String responseJson = sendRequest(HttpMethod.POST, ServiceUriPaths.CORE_QUERY_TASKS,
                Utils.toJson(qt));
        QueryTask result = Utils.fromJson(responseJson, QueryTask.class);

        String subnetLink = result.results.documentLinks.get(0);
        NetworkProfile np = new NetworkProfile();
        np.subnetLinks = new ArrayList<>();
        np.subnetLinks.add(subnetLink);
        return np;
    }

    private ComputeProfile loadComputeProfile() {
        URL r = getClass().getClassLoader().getResource("test-aws-compute-profile.yaml");
        try (InputStream is = r.openStream()) {
            return YamlMapper.objectMapper().readValue(is, ComputeProfile.class);
        } catch (Exception e) {
            logger.error("Failure reading default environment: %s, reason: %s", r,
                    e.getMessage());
            return null;
        }
    }

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
    protected EndpointType getEndpointType() {
        return endpointType;
    }

    @Override
    protected void extendEndpoint(EndpointState endpoint) {
        endpointExtender.accept(endpoint);
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
    protected void doWithResources(Set<String> resourceLinks) throws Throwable {
        CompositeComponent compositeComponent = getDocument(resourceLinks.iterator().next(),
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
    protected String getResourceDescriptionLink() throws Exception {
        return importTemplate("WordPress_with_MySQL_compute_network.yaml");
    }
}
