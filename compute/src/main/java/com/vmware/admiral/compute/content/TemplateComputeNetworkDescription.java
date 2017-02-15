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

package com.vmware.admiral.compute.content;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.photon.controller.model.Constraint;

/**
 * This class exists for serialization/deserialization purposes only
 */
@JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
@JsonIgnoreProperties({ "customProperties" })
public class TemplateComputeNetworkDescription extends ComputeNetworkDescription {

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
                ? this.constraints.get(ComputeConstants.COMPUTE_PLACEMENT_CONSTRAINT_KEY) : null;
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
