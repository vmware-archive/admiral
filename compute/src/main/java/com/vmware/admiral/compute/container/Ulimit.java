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

package com.vmware.admiral.compute.container;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ulimits for a container
 */
public class Ulimit {

    @JsonProperty("name")
    public String name;

    @JsonProperty("soft")
    public Integer soft;

    @JsonProperty("hard")
    public Integer hard;

    public Ulimit() {
    }

    public Ulimit(String name, Integer soft, Integer hard) {
        this.name = name;
        this.soft = soft;
        this.hard = hard;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Ulimit ulimit = (Ulimit) o;

        if (name != null ? !name.equals(ulimit.name) : ulimit.name != null) {
            return false;
        }
        if (soft != null ? !soft.equals(ulimit.soft) : ulimit.soft != null) {
            return false;
        }

        return hard != null ? hard.equals(ulimit.hard) : ulimit.hard == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (soft != null ? soft.hashCode() : 0);
        result = 31 * result + (hard != null ? hard.hashCode() : 0);
        return result;
    }

    @Override public String toString() {
        return "Ulimit {name='" + name + "\', soft='" + soft + '\'' + ", hard='" + hard + "\'}";
    }
}
