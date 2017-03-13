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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.NetworkType;
import com.vmware.admiral.compute.profile.ProfileService;
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

        @JsonInclude(value = Include.NON_EMPTY)
        @Documentation(description = "Specifies the network type e.g. public or isolated")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public NetworkType networkType;

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
        @Documentation(description = "List of profiles, calculated during allocation, applicable for this network.")
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.OPTIONAL })
        public List<String> profileLinks;

        @Documentation(description = "Link to a new subnet state to provision.")
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
                PropertyUsageOption.OPTIONAL })
        public String subnetLink;

        @Documentation(description = "Link to a profile where the network will be provisioned.")
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
                PropertyUsageOption.OPTIONAL })
        public String provisionProfileLink;
    }

    @Override
    public void handleCreate(Operation post) {
        if (!post.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        validateState(post.getBody(ComputeNetwork.class));

        post.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ComputeNetwork newState = patch.getBody(ComputeNetwork.class);
        validateState(newState);

        ComputeNetwork currentState = getState(patch);

        updateState(currentState, newState);

        patch.setBody(currentState).complete();
    }

    private void validateState(ComputeNetwork desc) {
        Utils.validateState(getStateDescription(), desc);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ComputeNetwork nd = (ComputeNetwork) super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(nd);
        nd.name = "My Network";
        nd.networkType = NetworkType.PUBLIC;
        nd.securityGroupLinks = new HashSet<>();
        nd.securityGroupLinks.add(SecurityGroupService.FACTORY_LINK + "/my-sec-group");
        nd.profileLinks = new ArrayList<>();
        nd.profileLinks.add(ProfileService.FACTORY_LINK + "/my-profile");
        return nd;
    }

    private void updateState(ComputeNetwork currentState, ComputeNetwork newState) {
        currentState.provisionProfileLink = newState.provisionProfileLink;
    }
}
