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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;

/**
 * For {@link LoadBalancerDescription} serialization/deserialization purposes.
 */
public class TemplateLoadBalancerDescription extends LoadBalancerDescription {
    @JsonProperty("instance")
    public String getInstance() {
        return this.computeDescriptionLink;
    }

    public void setInstance(String instance) {
        this.computeDescriptionLink = instance;
    }

    @JsonProperty("network")
    public String getNetwork() {
        return this.networkName;
    }

    public void setNetwork(String network) {
        this.networkName = network;
    }
}
