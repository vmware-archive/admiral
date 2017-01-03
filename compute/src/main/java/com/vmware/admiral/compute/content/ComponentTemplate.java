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

package com.vmware.admiral.compute.content;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Component in a CompositeTemplate
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = ComponentTemplateDeserializer.class)
public class ComponentTemplate<T> {
    public String type;
    public T data;
    public String[] dependsOn;

    public Map<String, NestedState> children;
}
