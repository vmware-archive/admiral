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

package com.vmware.admiral.compute.content.compose;

import java.util.List;

import com.fasterxml.jackson.databind.util.StdConverter;

/**
 * YAML converter to deserialize Docker compose entities that can be represented indistinctly with
 * a string or list. e.g.
 * <pre>
 * command: bundle exec thin -p 3000
 *
 * command: [bundle, exec, thin, -p, 3000]
 * </pre>
 * The result is a {@code String[]} representation of the item or items as expected by the
 * ContainerDescription.
 */
public class StringOrListToArrayConverter extends StdConverter<Object, String[]> {

    @SuppressWarnings("unchecked")
    @Override
    public String[] convert(Object entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof List) {
            return ((List<Object>) entity).stream().map(o -> o.toString()).toArray(String[]::new);
        }
        if (entity instanceof String) {
            return new String[] { (String) entity };
        }
        throw new IllegalArgumentException();
    }

}
