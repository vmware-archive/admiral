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

package com.vmware.admiral.compute.endpoint;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.vmware.admiral.compute.ComputeConstants.AdapterType;
import com.vmware.admiral.compute.endpoint.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.UriUtils;

public enum EndpointType {
    aws(ComputeDescription.ENVIRONMENT_NAME_AWS, Arrays.asList(AdapterType.STATS_ADAPTER)) {
        @Override
        public URI getAdapterManagementUri(EndpointState state) {
            StringBuffer reg = new StringBuffer("https://ec2.");
            reg.append(state.regionId);
            reg.append(".amazonaws.com");
            return UriUtils.buildUri(reg.toString());
        }
    },
    azure(ComputeDescription.ENVIRONMENT_NAME_AZURE, Arrays.asList(AdapterType.STATS_ADAPTER)) {
        @Override
        public URI getAdapterManagementUri(EndpointState state) {
            return UriUtils.buildUri("https://management.azure.com");
        }
    },
    @SuppressWarnings("deprecation")
    gpc(ComputeDescription.ENVIRONMENT_NAME_GCE) {
        @Override
        public URI getAdapterManagementUri(EndpointState state) {
            return UriUtils.buildUri("http://not-configured");
        }
    },
    vsphere(ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE,
            Arrays.asList(AdapterType.POWER_ADAPTER)) {
        @Override
        public URI getAdapterManagementUri(EndpointState state) {
            StringBuffer vcUrl = new StringBuffer("https://");
            vcUrl.append(state.endpointHost);
            vcUrl.append("/sdk");
            return UriUtils.buildUri(vcUrl.toString());
        }
    };

    private final String description;
    private final List<AdapterType> adapterTypes = new ArrayList<>();

    private EndpointType(String description) {
        this(description, Collections.emptyList());
    }

    private EndpointType(String description, List<AdapterType> adapterTypes) {
        this.description = description;
        this.adapterTypes.addAll(Arrays.asList(
                AdapterType.INSTANCE_ADAPTER,
                AdapterType.ENUMERATION_ADAPTER));
        this.adapterTypes.addAll(adapterTypes);
    }

    public String getDescription() {
        return description;
    }

    public abstract URI getAdapterManagementUri(EndpointState state);

    public boolean supports(AdapterType adapterType) {
        return adapterTypes.contains(adapterType);
    }
}
