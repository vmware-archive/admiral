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

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.test.integration.compute.aws.AwsComputeProvisionIT;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;

@RunWith(Parameterized.class)
public class WordpressProvisionIT extends BaseWordpressComputeProvisionIT {

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
    protected void doSetUp() throws Throwable {
        setUp.run();

        createProfile(loadComputeProfile(), createNetworkProfile(AWS_DEFAULT_SUBNET_ID, null),
                new StorageProfileService.StorageProfile());
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
    protected String getResourceDescriptionLink() throws Exception {
        String compositeDescriptionLink = importTemplate(
                "WordPress_with_MySQL_compute.yaml");
        return compositeDescriptionLink;
    }
}
