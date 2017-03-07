/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import com.google.common.collect.Sets;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.test.integration.compute.aws.AwsComputeProvisionIT;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;

@RunWith(Parameterized.class)
public class WordpressProvisionNetworkIT extends BaseWordpressComputeProvisionIT {

    private static Consumer<EndpointState> awsEndpointExtender = endpointState -> new AwsComputeProvisionIT()
            .extendEndpoint(endpointState);

    private static Runnable awsSetUp = () -> {
    };

    private final String templateFilename;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { EndpointType.aws, awsEndpointExtender, awsSetUp, "WordPress_with_MySQL_compute_network.yaml" },
                { EndpointType.aws, awsEndpointExtender, awsSetUp, "WordPress_with_MySQL_compute_public_network.yaml" }
                //TODO uncomment once the vsphere issues are resolved
                //{ EndpointType.vsphere, vSphereEndpointExtender, vSphereSetUp }
        });
    }

    private final EndpointType endpointType;
    private final Consumer<EndpointState> endpointExtender;
    private final Runnable setUp;

    public WordpressProvisionNetworkIT(EndpointType endpointType,
            Consumer<EndpointState> endpointExtender, Runnable setUp, String templateFilename) {
        this.endpointType = endpointType;
        this.endpointExtender = endpointExtender;
        this.setUp = setUp;
        this.templateFilename = templateFilename;
    }

    @Override
    protected void doSetUp() throws Throwable {
        setUp.run();

        createEnvironment(loadComputeProfile(), createNetworkProfile(AWS_SECONDARY_SUBNET_ID, null),
                new StorageProfile());

        createEnvironment(loadComputeProfile(), createNetworkProfile(AWS_DEFAULT_SUBNET_ID,
                Sets.newHashSet(createTag("location", "dmz"))), new StorageProfile());
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
        return importTemplate(templateFilename);
    }
}
