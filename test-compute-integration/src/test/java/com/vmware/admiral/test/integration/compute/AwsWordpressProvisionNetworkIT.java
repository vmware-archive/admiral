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

import static com.vmware.admiral.test.integration.compute.aws.AwsComputeProvisionIT.ACCESS_KEY_PROP;
import static com.vmware.admiral.test.integration.compute.aws.AwsComputeProvisionIT.ACCESS_SECRET_PROP;
import static com.vmware.admiral.test.integration.compute.aws.AwsComputeProvisionIT.AWS_TAG_NAME;
import static com.vmware.admiral.test.integration.compute.aws.AwsComputeProvisionIT.REGION_ID_PROP;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateVpcRequest;
import com.amazonaws.services.ec2.model.CreateVpcResult;
import com.amazonaws.services.ec2.model.DeleteVpcRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
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
public class AwsWordpressProvisionNetworkIT extends BaseWordpressComputeProvisionIT {

    public static final int CIDR_PREFIX = 28;

    private final String templateFilename;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"WordPress_with_MySQL_compute_network.yaml", null },
                {"WordPress_with_MySQL_compute_public_network.yaml", null },
                {"WordPress_with_MySQL_compute_isolated_network.yaml", isolatedNicValidator }
        });
    }

    private final Consumer<Set<ServiceDocument>> validator;

    public AwsWordpressProvisionNetworkIT(String templateFilename, Consumer<Set<ServiceDocument>> validator) {
        this.templateFilename = templateFilename;
        this.validator = validator;
    }

    @Override
    protected void doSetUp() throws Throwable {
        createVpcIfNeeded();

        createProfile(loadComputeProfile(), createNetworkProfile(AWS_SECONDARY_SUBNET_NAME, null),
                new StorageProfile());

        createProfile(loadComputeProfile(), createNetworkProfile(AWS_DEFAULT_SUBNET_NAME,
                Sets.newHashSet(createTag("location", "dmz"))), new StorageProfile());

        createProfile(loadComputeProfile(), createIsolatedNetworkProfile(AWS_ISOLATED_VPC_NAME,
                CIDR_PREFIX),
                new StorageProfile());
    }

    @Override
    protected EndpointType getEndpointType() {
        return EndpointType.aws;
    }

    @Override
    protected void extendEndpoint(EndpointState endpoint) {
        new AwsComputeProvisionIT().extendEndpoint(endpoint);
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
                            validateComputeNic((ComputeState) c, AWS_DEFAULT_SUBNET_NAME);
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

                assertEquals(networkState.name, AWS_ISOLATED_VPC_NAME);

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

    private void createVpcIfNeeded() throws Exception {
        // check if the vpc that we use for isolation network tests exist and create it if it doesn't
        BasicAWSCredentials cred = new BasicAWSCredentials(getTestRequiredProp(ACCESS_KEY_PROP), getTestRequiredProp(ACCESS_SECRET_PROP));
        AmazonEC2 ec2 = new AmazonEC2Client(cred);
        ec2.setRegion(Region.getRegion(Regions.fromName( getTestProp(REGION_ID_PROP, "us-east-1"))));

        Optional<Vpc> isolatedVpc = ec2.describeVpcs().getVpcs()
                .stream()
                .filter(v -> {
                    String name = v.getTags()
                            .stream()
                            .filter(t -> AWS_TAG_NAME.equals(t.getKey()))
                            .findFirst()
                            .map(t -> t.getValue())
                            .orElse("");

                    return AWS_ISOLATED_VPC_NAME.equals(name);
                }).findFirst();

        if (!isolatedVpc.isPresent()) {
            String vpcId = null;

            CreateVpcRequest request = new CreateVpcRequest();
            request.setCidrBlock("192.168.0.0/16");
            CreateVpcResult vpc = ec2.createVpc(request);
            vpcId = vpc.getVpc().getVpcId();

            try {
                ec2.createTags(new CreateTagsRequest().withResources(vpcId)
                        .withTags(new Tag(AWS_TAG_NAME, AWS_ISOLATED_VPC_NAME)));

            } catch (Throwable e) {
                // delete the VPC in case the tag request fails
                if (vpcId != null) {
                    ec2.deleteVpc(new DeleteVpcRequest().withVpcId(vpcId));
                }

                throw e;
            }

            triggerAndWaitForEndpointEnumeration(endpoint);
        }
    }
}
