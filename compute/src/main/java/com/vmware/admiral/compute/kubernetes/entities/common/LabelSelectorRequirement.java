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

package com.vmware.admiral.compute.kubernetes.entities.common;

import java.util.List;

/**
 * A label selector requirement is a selector that contains values, a key, and an operator that
 * relates the key and values.
 */
public class LabelSelectorRequirement {

    /**
     * key is the label key that the selector applies to.
     */
    public String key;

    /**
     * operator represents a key's relationship to a set of values.
     * Valid operators ard In, NotIn, Exists and DoesNotExist.
     */
    public String operator;

    /**
     * values is an array of string values. If the operator is In or NotIn,
     * the values array must be non-empty. If the operator is Exists or DoesNotExist,
     * the values array must be empty. This array is replaced during a strategic
     * merge patch.
     */
    public List<String> values;
}
