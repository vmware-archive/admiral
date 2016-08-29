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

package com.vmware.admiral.adapter.docker.util;

import java.util.function.UnaryOperator;

/**
 * map property names (CapitalizedLikeThis) to switch names (capitalized-like-this)
 */
public class PropertyToSwitchNameMapper implements UnaryOperator<String> {
    private final String CAPS_EXCEPT_FIRST = "(.)([A-Z])";

    @Override
    public String apply(String t) {
        return t.replaceAll(CAPS_EXCEPT_FIRST, "$1-$2").toLowerCase();
    }
}
