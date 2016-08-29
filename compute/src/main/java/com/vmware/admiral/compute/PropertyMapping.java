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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PropertyMapping {

    @JsonProperty("mappings")
    public Map<String, String> mappings;

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PropertyMapping [mappings=");
        builder.append(mappings);
        builder.append("]");
        return builder.toString();
    }

}
