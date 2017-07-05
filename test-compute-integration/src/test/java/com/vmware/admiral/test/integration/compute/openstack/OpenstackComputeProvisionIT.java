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

import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_GROUP_NAME;

import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.test.integration.compute.BaseComputeProvisionIT;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.EndpointService;

public class OpenstackComputeProvisionIT extends BaseComputeProvisionIT {

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
    private static final String AVAILABILITY_ZONE_PROPERTY_KEY = "test.openstack.availability.zone";

    @Override
    protected String getEndpointType() {
        return OPENSTACK_ENDPOINT_TYPE;
    }

    @Override
    protected void doSetUp() throws Throwable {
        logger.info("OpenstackComputeProvisionIT.doSetUp()");
        createProfile(
                loadComputeProfile(getEndpointType()),
                new NetworkProfileService.NetworkProfile(),
                new StorageProfileService.StorageProfile());
    }

    @Override
    protected void extendEndpoint(EndpointService.EndpointState endpoint) {
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
                .put(EndpointConfigRequest.PRIVATE_KEYID_KEY, getTestRequiredProp(PRIVATE_KEY_ID_PROPERTY_KEY));
        endpoint.endpointProperties
                .put(EndpointConfigRequest.PRIVATE_KEY_KEY, getTestRequiredProp(PRIVATE_KEY_PROPERTY_KEY));
        endpoint.endpointProperties
                .put(EndpointConfigRequest.REGION_KEY, getTestRequiredProp(PROJECT_PROPERTY_KEY));
    }

    @Override
    protected void extendComputeDescription(ComputeDescriptionService.ComputeDescription computeDescription)
            throws Exception {
        computeDescription.customProperties.put(RESOURCE_GROUP_NAME, "osTest");
        computeDescription.customProperties
                .put("osFlavor", getTestRequiredProp(FLAVOR_PROPERTY_KEY));
        computeDescription.customProperties
                .put("osAvailabilityZone", getTestRequiredProp(AVAILABILITY_ZONE_PROPERTY_KEY));
    }

    @Override
    protected String getResourceDescriptionLink() throws Exception {
        return getResourceDescriptionLink(true, null);
    }

    @Override
    protected ComputeDescriptionService.ComputeDescription createComputeDescription(
            boolean withDisks, String imageId)
            throws Exception {
        if (imageId == null) {
            imageId = "ubuntu-1404";
        }

        return super.createComputeDescription(withDisks, imageId);
    }

}
