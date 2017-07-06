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

package com.vmware.admiral.test.integration.compute.openstack;

import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import com.google.common.collect.Sets;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.test.integration.compute.BaseWordpressComputeProvisionIT;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.ServiceDocument;

@RunWith(Parameterized.class)
public class OpenstackWordpressProvisionIT extends BaseWordpressComputeProvisionIT {

    public static final String OPENSTACK_ENDPOINT_TYPE = "openstack";
    private static final String OPENSTACK_DOMAINNAME = "openstackDomain";
    private static final String OPENSTACK_PROJECT_ID = "openstackProjectId";
    private static final String OPENSTACK_PROJECT_NAME = "openstackProjectName";
    private static final String HOST_KEY = "host";
    private static final String PROXY_KEY = "proxy";

    // test properties
    private static final String HOST_PROPERTY_KEY = "test.openstack.host";
    private static final String PROXY_PROPERTY_KEY = "test.openstack.proxy";
    private static final String PROJECT_PROPERTY_KEY = "test.openstack.project.id";
    private static final String DOMAIN_PROPERTY_KEY = "test.openstack.domain";
    private static final String PRIVATE_KEY_ID_PROPERTY_KEY = "test.openstack.user";
    private static final String PRIVATE_KEY_PROPERTY_KEY = "test.openstack.password";
    private static final String FLAVOR_PROPERTY_KEY = "test.openstack.flavor.id";
    private static final String NETWORK_ID_PROPERTY_KEY = "test.openstack.network.id";
    private static final String AVAILABILITY_ZONE_PROPERTY_KEY = "test.openstack.availability.zone";

    private final String shortName;
    private final String templateFilename;

    @Parameterized.Parameters(name = "{index}:{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "existing", "WordPress_with_MySQL_compute_network_ubuntu14.yaml" },
                { "isolated", "WordPress_with_MySQL_compute_isolated_ubuntu14.yaml" },
                { "isolated", "WordPress_with_MySQL_compute_isolated_sg_ubuntu14.yaml" }
        });
    }

    public OpenstackWordpressProvisionIT(String shortName, String templateFilename) {
        this.shortName = shortName;
        this.templateFilename = templateFilename;
    }

    @Override
    protected void doSetUp() throws Throwable {
        logger.info("OpenstackWordpressProvisionIT.doSetUp()");
        createProfile(
                loadComputeProfile(getEndpointType()),
                createNetworkProfile(getTestRequiredProp("test.openstack.subnetwork.name"),
                        getTestRequiredProp("test.openstack.securitygroup.name"),
                        Sets.newHashSet(createTag("location", "dmz"))),
                new StorageProfile());

        NetworkProfile isolatedNetworkProfile = createIsolatedSubnetNetworkProfile(
                getTestRequiredProp("test.openstack.network.name"),
                Integer.parseInt(getTestRequiredProp("test.openstack.network.cidr_prefix")));
        isolatedNetworkProfile.isolationNetworkCIDR = getTestRequiredProp(
                "test.openstack.network.cidr");
        createProfile(
                loadComputeProfile(getEndpointType()),
                isolatedNetworkProfile,
                new StorageProfile());

        createProfile(loadComputeProfile(getEndpointType()), createIsolatedSecurityGroupNetworkProfile(
                getTestRequiredProp("test.openstack.subnetwork.name"),
                Sets.newHashSet(createTag("type", "sg"))), new StorageProfile());
    }

    @Override
    protected String getEndpointType() {
        return OPENSTACK_ENDPOINT_TYPE;
    }

    @Override
    protected void extendEndpoint(EndpointState endpoint) {
        endpoint.endpointProperties.put(HOST_KEY, getTestRequiredProp(HOST_PROPERTY_KEY));

        if (getTestProp(PROXY_PROPERTY_KEY, "").length() > 0) {
            endpoint.endpointProperties.put(PROXY_KEY, getTestProp(PROXY_PROPERTY_KEY));
        }
        endpoint.endpointProperties
                .put(OPENSTACK_PROJECT_ID, getTestRequiredProp(PROJECT_PROPERTY_KEY));
        endpoint.endpointProperties
                .put(OPENSTACK_PROJECT_NAME, getTestRequiredProp(PROJECT_PROPERTY_KEY));
        endpoint.endpointProperties
                .put(OPENSTACK_DOMAINNAME, getTestRequiredProp(DOMAIN_PROPERTY_KEY));
        endpoint.endpointProperties
                .put(PRIVATE_KEYID_KEY, getTestRequiredProp(PRIVATE_KEY_ID_PROPERTY_KEY));
        endpoint.endpointProperties
                .put(PRIVATE_KEY_KEY, getTestRequiredProp(PRIVATE_KEY_PROPERTY_KEY));
        endpoint.endpointProperties
                .put(REGION_KEY, getTestRequiredProp(PROJECT_PROPERTY_KEY));
    }

    @Override
    protected void extendComputeDescription(ComputeDescription computeDescription)
            throws Exception {
        computeDescription.customProperties.put(RESOURCE_GROUP_NAME, "osTest");
        computeDescription.customProperties
                .put("osFlavor", getTestRequiredProp(FLAVOR_PROPERTY_KEY));
        computeDescription.customProperties
                .put("osAvailabilityZone", getTestRequiredProp(AVAILABILITY_ZONE_PROPERTY_KEY));
    }

    @Override
    protected String getResourceDescriptionLink() throws Exception {
        return importTemplate(templateFilename);
    }

    @Override
    protected void doWithResources(Set<String> resourceLinks) throws Throwable {

        Set<ServiceDocument> resources = getResources(resourceLinks);
        if ("isolated".equals(shortName)) {
            validateIsolatedNic(resources, getTestRequiredProp("test.openstack.network.name"),
                    getTestRequiredProp("test.openstack.network.cidr"));
        } else {
            super.doWithResources(resourceLinks);
            resources.stream()
                    .filter(c -> c instanceof ComputeState)
                    .forEach(c -> {
                        try {
                            validateComputeNic((ComputeState) c,
                                    getTestRequiredProp("test.openstack.subnetwork.name"));
                        } catch (Exception e) {
                            fail();
                        }
                    });
        }
    }
}
