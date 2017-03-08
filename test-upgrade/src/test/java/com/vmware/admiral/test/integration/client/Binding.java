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

package com.vmware.admiral.test.integration.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Binding {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComponentBinding {
        public String componentName;
        public List<Binding> bindings;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BindingPlaceholder {

        public String bindingExpression;
        public String defaultValue;
    }

    public List<String> targetFieldPath;
    public String originalFieldExpression;
    public BindingPlaceholder placeholder;
}
