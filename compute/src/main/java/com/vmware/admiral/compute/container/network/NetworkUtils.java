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

package com.vmware.admiral.compute.container.network;

import java.util.Map;
import java.util.function.BinaryOperator;

import com.vmware.admiral.common.util.PropertyUtils;

public class NetworkUtils {

    /**
     * Merge strategy that will overwrite the target if it is not a map and the source is non null.
     *
     * It will not recurse into merging individual fields of complex objects
     */
    public static final BinaryOperator<Object> SHALLOW_MERGE_SKIP_MAPS_STRATEGY = (copyTo,
            copyFrom) -> {
        if (copyTo instanceof Map) {
            return copyTo;
        }
        return PropertyUtils.mergeProperty(copyTo, copyFrom);
    };

    public static final String IPV4_VALIDATION_ERROR_FORMAT = "Specified input "
            + "is not a valid IPv4 address: %s";
    public static final String IPV4_CIDR_VALIDATION_ERROR_FORMAT = "Specified input "
            + "is not a valid IPv4 CIDR notation: %s";

    public static final String REGEXP_IPV4_ADDRESS = "((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}"
            + "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    public static final String REGEXP_IPV4_CIDR_NOTATION = REGEXP_IPV4_ADDRESS
            + "\\/(3[0-2]|[1-2]?[0-9])";

    public static void validateIpCidrNotation(String subnet) {
        if (subnet != null && !subnet.matches(REGEXP_IPV4_CIDR_NOTATION)) {
            String error = String.format(
                    IPV4_CIDR_VALIDATION_ERROR_FORMAT,
                    subnet);
            throw new IllegalArgumentException(error);
        }
    }

    public static void validateIpAddress(String gateway) {
        if (gateway != null && !gateway.matches(REGEXP_IPV4_ADDRESS)) {
            String error = String.format(IPV4_VALIDATION_ERROR_FORMAT,
                    gateway);
            throw new IllegalArgumentException(error);
        }
    }

    public static void validateNetworkName(String name) {
        // currently, it looks like there are no restrictions on the network name from docker side.
        // Numbers-only names and even space-delimited words are supported. We can add some
        // restrictions here.
    }

}
