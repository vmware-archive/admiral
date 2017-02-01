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

package com.vmware.admiral.closures.drivers;

import com.google.gson.JsonElement;

/**
 * Represents execution container configuration properties.
 *
 */
public class ContainerConfiguration {

    public String name;

    public String[] envVars;

    public Integer memoryMB;

    public Integer cpuShares;

    public String dependencies;

    public String sourceURL;

    public JsonElement logConfiguration;

    public String placementLink;

    public ContainerConfiguration() {
    }

    public ContainerConfiguration(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "ContainerConfiguration{" +
                "name='" + name + '\'' +
                ", memoryMB=" + memoryMB +
                ", cpuShares=" + cpuShares +
                ", dependencies='" + dependencies + '\'' +
                ", sourceURL='" + sourceURL + '\'' +
                ", logConfiguration=" + logConfiguration +
                ", placementLink='" + placementLink + '\'' +
                '}';
    }
}
