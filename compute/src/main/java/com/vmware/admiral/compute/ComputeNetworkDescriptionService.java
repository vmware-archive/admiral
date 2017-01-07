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

package com.vmware.admiral.compute;

import java.net.URI;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

/**
 * The purpose of the ComputeNetworkDescription is to hold network information that later in the
 * allocation will be merged with the NetworkInterfaceDescription
 */
public class ComputeNetworkDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.COMPUTE_NETWORK_DESC;

    public ComputeNetworkDescriptionService() {
        super(ComputeNetworkDescription.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
    public static class ComputeNetworkDescription extends ResourceState {
        public String assignment;

        @JsonProperty("public")
        public boolean isPublic;

        /**
         * Composite Template use only. If set to true, specifies that this network exists outside
         * of the Composite Template.
         */
        @JsonInclude(value = Include.NON_EMPTY)
        @Documentation(description = "Composite Template use only. If set to true, specifies that "
                + "this network exists outside of the Composite Template.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Boolean external = Boolean.TRUE;

        /**
         * URI reference to the adapter used to create an instance of this host.
         */
        @JsonIgnore
        @Documentation(description = "URI reference to the adapter used to create an instance of this host")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI instanceAdapterReference;

        /**
         * Region identifier of this description service instance.
         */
        @JsonIgnore
        @Documentation(description = "Region identifier of this description service instance")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String regionId;
    }

    @Override
    public void handleCreate(Operation post) {
        validateState(post.getBody(ComputeNetworkDescription.class));

        post.complete();
    }

    private void validateState(ComputeNetworkDescription desc) {
        AssertUtil.assertNotEmpty(desc.name, "name");
    }
}
