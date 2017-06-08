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

package com.vmware.admiral.test.integration.compute.aws;

import static org.junit.Assert.fail;

import static com.vmware.admiral.test.integration.compute.aws.AwsComputeProvisionIT.ACCESS_KEY_PROP;
import static com.vmware.admiral.test.integration.compute.aws.AwsComputeProvisionIT.ACCESS_SECRET_PROP;
import static com.vmware.admiral.test.integration.compute.aws.AwsComputeProvisionIT.AWS_TAG_NAME;
import static com.vmware.admiral.test.integration.compute.aws.AwsComputeProvisionIT.REGION_ID_PROP;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateVpcRequest;
import com.amazonaws.services.ec2.model.CreateVpcResult;
import com.amazonaws.services.ec2.model.DeleteVpcRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import com.google.common.collect.Sets;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.test.integration.compute.BaseWordpressComputeProvisionIT;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.ServiceDocument;

@RunWith(Parameterized.class)
public class AwsWordpressProvisionNetworkIT extends BaseWordpressComputeProvisionIT {

    private static final int CIDR_PREFIX = 28;

    private final String templateFilename;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"WordPress_with_MySQL_compute_network.yaml", null },
                {"WordPress_with_MySQL_compute_public_network.yaml", null },
                {"WordPress_with_MySQL_compute_isolated_network.yaml",
                        (BiConsumer<Set<ServiceDocument>, String>) BaseWordpressComputeProvisionIT
                                ::validateIsolatedNic },
                {"WordPress_with_MySQL_compute_with_load_balancer.yaml", null }
        });
    }

    private final BiConsumer<Set<ServiceDocument>, String> validator;

    public AwsWordpressProvisionNetworkIT(String templateFilename,
            BiConsumer<Set<ServiceDocument>, String> validator) {
        this.templateFilename = templateFilename;
        this.validator = validator;
    }

    @Override
    protected void doSetUp() throws Throwable {
        createVpcIfNeeded();

        createProfile(loadComputeProfile(getEndpointType()), createNetworkProfile(
                AWS_SECONDARY_SUBNET_NAME, null, null),
                new StorageProfile());

        createProfile(loadComputeProfile(getEndpointType()), createNetworkProfile(
                AWS_DEFAULT_SUBNET_NAME, null,
                Sets.newHashSet(createTag("location", "dmz"))), new StorageProfile());

        createProfile(loadComputeProfile(getEndpointType()), createIsolatedNetworkProfile(AWS_ISOLATED_VPC_NAME,
                CIDR_PREFIX),
                new StorageProfile());
    }

    @Override
    protected String getEndpointType() {
        return EndpointType.aws.name();
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
            validator.accept(resources, AWS_ISOLATED_VPC_NAME);
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

    private void createVpcIfNeeded() throws Exception {
        // check if the vpc that we use for isolation network tests exist and create it if it doesn't
        BasicAWSCredentials cred = new BasicAWSCredentials(getTestRequiredProp(ACCESS_KEY_PROP), getTestRequiredProp(ACCESS_SECRET_PROP));

        AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(cred))
                .withRegion(getTestProp(REGION_ID_PROP, "us-east-1")).build();

        Optional<Vpc> isolatedVpc = ec2.describeVpcs().getVpcs()
                .stream()
                .filter(v -> {
                    String name = v.getTags()
                            .stream()
                            .filter(t -> AWS_TAG_NAME.equals(t.getKey()))
                            .findFirst()
                            .map(Tag::getValue)
                            .orElse("");

                    return AWS_ISOLATED_VPC_NAME.equals(name);
                }).findFirst();

        if (!isolatedVpc.isPresent()) {
            CreateVpcRequest request = new CreateVpcRequest();
            request.setCidrBlock("192.168.0.0/16");
            CreateVpcResult vpc = ec2.createVpc(request);
            String vpcId = vpc.getVpc().getVpcId();

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
