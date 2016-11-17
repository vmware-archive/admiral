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

package com.vmware.admiral.adapter.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Image operation types
 */
public enum ImageOperationType {
    SEARCH("Image.Search"),
    PING("Image.Ping"),
    LIST_TAGS("Image.Tags"),
    BUILD("Image.Build"),
    LOAD("Image.Load"),
    DELETE("Image.Delete"),
    INSPECT("Image.Inspect");

    ImageOperationType(String id) {
        this.id = id;
    }

    private static final Map<String, ImageOperationType> operationsById = new HashMap<>();

    static {
        for (ImageOperationType opr : values()) {
            operationsById.put(opr.id, opr);
        }
    }

    public final String id;

    public static ImageOperationType instanceById(String id) {
        if (id == null) {
            return null;
        }

        return operationsById.get(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
