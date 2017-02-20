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
import java.util.Map;

/**
 * A label selector is a label query over a set of resources. The result of matchLabels and
 * matchExpressions are ANDed. An empty label selector matches all objects. A null
 * label selector matches no objects.
 */
public class LabelSelector {

    /**
     * matchLabels is a map of {key,value} pairs. A single {key,value} in the matchLabels
     * map is equivalent to an element of matchExpressions, whose key field is "key", the
     * operator is "In", and the values array contains only "value". The requirements are ANDed.
     */
    public Map<String, String> matchLabels;

    /**
     * matchExpressions is a list of label selector requirements. The requirements are ANDed.
     */
    public List<LabelSelectorRequirement> matchExpressions;
}
