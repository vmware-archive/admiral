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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

import com.google.common.collect.Sets;
import org.apache.commons.net.util.SubnetUtils;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.test.integration.compute.aws.AwsComputeProvisionIT;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.xenon.common.ServiceDocument;

@RunWith(Parameterized.class)
public class WordpressProvisionNetworkIT extends BaseWordpressComputeProvisionIT {

    public static final int CIDR_PREFIX = 28;
    private static Consumer<EndpointState> awsEndpointExtender = endpointState -> new AwsComputeProvisionIT()
            .extendEndpoint(endpointState);

    private static Runnable awsSetUp = () -> {
    };

    private final String templateFilename;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                //{ EndpointType.aws, awsEndpointExtender, awsSetUp, "WordPress_with_MySQL_compute_network.yaml", null },
                { EndpointType.aws, awsEndpointExtender, awsSetUp, "WordPress_with_MySQL_compute_public_network.yaml", null },
//                { EndpointType.aws, awsEndpointExtender, awsSetUp, "WordPress_with_MySQL_compute_isolated_network.yaml", isolatedNicValidator }
                //TODO uncomment once the vsphere issues are resolved
                //{ EndpointType.vsphere, vSphereEndpointExtender, vSphereSetUp }
        });
    }

    private final EndpointType endpointType;
    private final Consumer<EndpointState> endpointExtender;
    private final Runnable setUp;
    private final Consumer<Set<ServiceDocument>> validator;

    public WordpressProvisionNetworkIT(EndpointType endpointType,
            Consumer<EndpointState> endpointExtender, Runnable setUp, String templateFilename, Consumer<Set<ServiceDocument>> validator) {
        this.endpointType = endpointType;
        this.endpointExtender = endpointExtender;
        this.setUp = setUp;
        this.templateFilename = templateFilename;
        this.validator = validator;
    }

    @Override
    protected void doSetUp() throws Throwable {
        setUp.run();

        createProfile(loadComputeProfile(), createNetworkProfile(AWS_SECONDARY_SUBNET_ID, null),
                new StorageProfile());

        createProfile(loadComputeProfile(), createNetworkProfile(AWS_DEFAULT_SUBNET_ID,
                Sets.newHashSet(createTag("location", "dmz"))), new StorageProfile());

        createProfile(loadComputeProfile(), createIsolatedNetworkProfile(AWS_ISOLATED_VPC_ID,
                CIDR_PREFIX),
                new StorageProfile());
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

    @Override
    protected void doWithResources(Set<String> resourceLinks) throws Throwable {

        Set<ServiceDocument> resources = getResources(resourceLinks);
        if (validator != null) {
            validator.accept(resources);
        } else {
            super.doWithResources(resourceLinks);
            resources.stream()
                    .filter(c -> c instanceof ComputeState)
                    .forEach(c -> {
                        try {
                            validateComputeNic((ComputeState) c, AWS_DEFAULT_SUBNET_ID);
                        } catch (Exception e) {
                            fail();
                        }
                    });
        }

    }

    private static Consumer<Set<ServiceDocument>> isolatedNicValidator = (serviceDocuments) -> {

        for (ServiceDocument serviceDocument : serviceDocuments) {
            if (!(serviceDocument instanceof ComputeState)) {
                continue;
            }

            ComputeState computeState = (ComputeState) serviceDocument;
            try {
                NetworkInterfaceState networkInterfaceState = getDocument(
                        computeState.networkInterfaceLinks.get(0), NetworkInterfaceState.class);

                SubnetService.SubnetState subnetState = getDocument(networkInterfaceState.subnetLink,
                        SubnetService.SubnetState.class);

                assertTrue(subnetState.name.contains("wpnet"));

                NetworkState networkState = getDocument(subnetState.networkLink,
                        NetworkState.class);

                assertEquals(networkState.id, AWS_ISOLATED_VPC_ID);

                //validate the cidr
                String lowSubnetAddress = new SubnetUtils(subnetState.subnetCIDR).getInfo()
                        .getLowAddress();

                assertTrue(new SubnetUtils(networkState.subnetCIDR).getInfo()
                        .isInRange(lowSubnetAddress));
            } catch (Exception e) {
                fail();
            }
        }
    };
}
