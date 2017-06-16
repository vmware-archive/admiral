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

package com.vmware.admiral.compute.container.loadbalancer;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Container load balancer frontend, represents an endpoint which will redirect to its backings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContainerLoadBalancerFrontendDescription {

    public int port;

    @JsonProperty("health_config")
    public ContainerLoadBalancerHealthConfig healthConfig;

    public List<ContainerLoadBalancerBackendDescription> backends;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ContainerLoadBalancerFrontendDescription that = (ContainerLoadBalancerFrontendDescription) o;

        return new EqualsBuilder()
                .append(port, that.port)
                .append(backends, that.backends)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(port)
                .append(backends)
                .toHashCode();
    }
}
