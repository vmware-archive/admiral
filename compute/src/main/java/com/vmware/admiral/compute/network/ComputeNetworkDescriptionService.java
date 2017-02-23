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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.content.ConstraintConverter;
import com.vmware.admiral.compute.content.StringEncodedConstraint;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.ResourceUtils;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

/**
 * The purpose of the ComputeNetworkDescription is to hold network information that later in the
 * allocation will be merged with the NetworkInterfaceDescription
 */
@JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
@JsonIgnoreProperties(ignoreUnknown = true, value = { "customProperties" })
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

        /**
         * Composite Template use only. If set to true, specifies that this network exists outside
         * of the Composite Template.
         */
        @JsonInclude(value = Include.NON_EMPTY)
        @Documentation(description = "Composite Template use only. If set to true, specifies that "
                + "this network exists outside of the Composite Template.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Boolean external = Boolean.FALSE;


        @JsonInclude(value = Include.NON_EMPTY)
        @Documentation(description = "Composite Template use only. Specifies the network type e.g public or isolated")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public NetworkType networkType;

        /**
         * Constraints of compute network to the network profile and subnet profile. If not
         * specified a default subnet will be calculated based on other components and placement
         * logic.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @JsonIgnore
        public Map<String, Constraint> constraints;

        @Documentation(description = "Security groups to apply to all instances connected to this network")
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
                PropertyUsageOption.OPTIONAL })
        public Set<String> securityGroupLinks;


        @JsonAnyGetter
        private Map<String, String> getProperties() {
            return customProperties;
        }

        @JsonAnySetter
        private void putProperty(String key, String value) {
            if (customProperties == null) {
                customProperties = new HashMap<>();
            }
            customProperties.put(key, value);
        }

        @JsonProperty("constraints")
        public List<StringEncodedConstraint> getPlacementConstraints() {
            Constraint constraint = this.constraints != null
                    ? this.constraints.get(ComputeConstants.COMPUTE_PLACEMENT_CONSTRAINT_KEY)
                    : null;
            if (constraint == null) {
                return null;
            }

            List<StringEncodedConstraint> stringConstraints = constraint.conditions.stream()
                    .map(ConstraintConverter::encodeCondition)
                    .filter(r -> r != null)
                    .collect(Collectors.toList());
            return stringConstraints;
        }

        public void setPlacementConstraints(List<StringEncodedConstraint> stringConstraints) {
            if (stringConstraints == null) {
                return;
            }

            Constraint constraint = new Constraint();
            constraint.conditions = stringConstraints.stream()
                    .map(ConstraintConverter::decodeCondition)
                    .filter(c -> c != null)
                    .collect(Collectors.toList());

            if (this.constraints == null) {
                this.constraints = new HashMap<>();
            }
            this.constraints.put(ComputeConstants.COMPUTE_PLACEMENT_CONSTRAINT_KEY, constraint);
        }
    }

    public enum NetworkType {
        @JsonProperty("public")
        PUBLIC,
        @JsonProperty("isolated")
        ISOLATED
    }

    @Override
    public void handleCreate(Operation post) {
        try {
            validateState(post.getBody(ComputeNetworkDescription.class));
            post.complete();
        } catch (Throwable e) {
            post.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ComputeNetworkDescription currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                ComputeNetworkDescription.class, null);
    }

    private void validateState(ComputeNetworkDescription desc) {
        AssertUtil.assertNotEmpty(desc.name, "name");
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ComputeNetworkDescription nd = (ComputeNetworkDescription) super.getDocumentTemplate();
        nd.name = "My Network";
        nd.networkType = NetworkType.PUBLIC;
        nd.securityGroupLinks = new HashSet<>();
        nd.constraints = new HashMap<>();
        nd.securityGroupLinks.add(SecurityGroupService.FACTORY_LINK + "/my-sec-group");
        return nd;
    }
}
