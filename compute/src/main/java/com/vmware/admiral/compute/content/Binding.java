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

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.compute.BindingUtils;

@JsonIgnoreProperties({ "provisioningTimeBinding" })
public class Binding implements Serializable {
    private static final long serialVersionUID = 1L;

    public static class ComponentBinding implements Serializable {
        private static final long serialVersionUID = 1L;

        public String componentName;
        public List<Binding> bindings;

        public ComponentBinding() {
        }

        public ComponentBinding(String componentName, List<Binding> bindings) {
            this.componentName = componentName;
            this.bindings = bindings;
        }
    }

    public static class BindingPlaceholder implements Serializable {
        private static final long serialVersionUID = 1L;

        // TODO could be a list of fields, but not sure about the syntax yet
        public String bindingExpression;
        public String defaultValue;

        public BindingPlaceholder() {
        }

        public BindingPlaceholder(String bindingExpression) {
            this(bindingExpression, null);
        }

        public BindingPlaceholder(String bindingExpression, String defaultValue) {
            this.bindingExpression = bindingExpression;
            this.defaultValue = defaultValue;
        }
    }

    /**
     * Path of the field to be replaced e.g [healthConfig, urlPath]
     */
    public List<String> targetFieldPath;
    public String originalFieldExpression;
    public BindingPlaceholder placeholder;

    public Binding() {
    }

    public Binding(List<String> targetFieldPath,
            String originalFieldExpression, BindingPlaceholder placeholder) {
        this.targetFieldPath = targetFieldPath;
        this.originalFieldExpression = originalFieldExpression;
        this.placeholder = placeholder;
    }

    public boolean isProvisioningTimeBinding() {
        return (this.placeholder != null)
                && BindingUtils.isProvisioningTimeBinding(this.placeholder.bindingExpression);
    }
}
