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

package com.vmware.admiral.compute.network;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.env.NetworkProfileService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

public class ComputeNetworkService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.COMPUTE_NETWORKS;

    public ComputeNetworkService() {
        super(ComputeNetwork.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
    public static class ComputeNetwork extends ResourceState {
        public String assignment;

        /**
         * Composite Template use only. If set to true, specifies that this network exists outside
         * of the Composite Template.
         */
        @JsonInclude(value = Include.NON_EMPTY)
        @Documentation(description = "Composite Template use only. If set to true, specifies that "
                + "this network exists outside of the Composite Template.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Boolean external = Boolean.TRUE;

        /** Defines the description of the network */
        @Documentation(description = "Defines the description of the network.")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String descriptionLink;

        /**
         * A tag or name of the Network Profile configuration to use. If not specified a default one
         * will be calculated based on other components and placement logic.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String connectivity;

        @Documentation(description = "Security groups to apply to all instances connected to this network")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> securityGroupLinks;

        @JsonIgnore
        @Documentation(description = "List Network profiles, calculated during allocation, applicable for this network.")
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.OPTIONAL })
        public Set<String> networkProfileLinks;
    }

    @Override
    public void handleCreate(Operation post) {
        if (!post.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        validateState(post.getBody(ComputeNetwork.class));

        post.complete();
    }

    private void validateState(ComputeNetwork desc) {
        Utils.validateState(getStateDescription(), desc);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ComputeNetwork nd = (ComputeNetwork) super.getDocumentTemplate();
        nd.name = "My Network";
        nd.securityGroupLinks = new HashSet<>();
        nd.securityGroupLinks.add(SecurityGroupService.FACTORY_LINK + "/my-sec-group");
        nd.networkProfileLinks = new HashSet<>();
        nd.networkProfileLinks.add(NetworkProfileService.FACTORY_LINK + "/my-net-profile");
        return nd;
    }
}
