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

package com.vmware.admiral.test.integration.compute.vsphere;

import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import com.google.common.collect.Sets;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.test.integration.compute.BaseWordpressComputeProvisionIT;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.ServiceDocument;

@RunWith(Parameterized.class)
@Ignore("https://jira-hzn.eng.vmware.com/browse/VCOM-1083")
public class VsphereWordpressProvisionNetworkIT extends BaseWordpressComputeProvisionIT {

    public static final String DEFAULT_SUBNET_NAME = "VM Network";
    public static final String SECONDARY_SUBNET_NAME = "good-portgroup";


    private final String templateFilename;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "WordPress_with_MySQL_compute_network.yaml" },
                { "WordPress_with_MySQL_compute_public_network.yaml" },
        });
    }

    public VsphereWordpressProvisionNetworkIT(String templateFilename) {
        this.templateFilename = templateFilename;
    }

    @Override
    protected void doSetUp() throws Throwable {

        // raise the support public ip address flag on the defaul subnet
        String defaultSubnetState = loadResource(SubnetState.class, DEFAULT_SUBNET_NAME);

        SubnetState patch = new SubnetState();
        patch.supportPublicIpAddress = true;
        patch.documentSelfLink = defaultSubnetState;
        patchDocument(patch);

        createProfile(loadComputeProfile(getEndpointType()),
                createNetworkProfile(SECONDARY_SUBNET_NAME, null, null),
                new StorageProfile());

        createProfile(loadComputeProfile(getEndpointType()),
                createNetworkProfile(
                        DEFAULT_SUBNET_NAME, null,
                        Sets.newHashSet(createTag("location", "dmz"))),
                new StorageProfile());
    }

    @Override
    protected String getEndpointType() {
        return EndpointType.vsphere.toString().toLowerCase();
    }

    @Override
    protected void extendEndpoint(EndpointState endpoint) {
        new VsphereComputeProvisionIT().extendEndpoint(endpoint);
        endpoint.endpointProperties.put("regionId", getTestRequiredProp(VsphereUtil.VC_DATACENTER_ID));
    }

    @Override
    protected String getResourceDescriptionLink() throws Exception {
        return importTemplate(templateFilename);
    }

    @Override
    protected void doWithResources(Set<String> resourceLinks) throws Throwable {

        Set<ServiceDocument> resources = getResources(resourceLinks);
        super.doWithResources(resourceLinks);
        resources.stream()
                .filter(c -> c instanceof ComputeService.ComputeState)
                .forEach(c -> {
                    try {
                        validateComputeNic((ComputeService.ComputeState) c, DEFAULT_SUBNET_NAME);
                    } catch (Exception e) {
                        fail();
                    }
                });
    }
}
