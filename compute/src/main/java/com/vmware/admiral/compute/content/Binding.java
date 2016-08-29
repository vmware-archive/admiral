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

public class Binding implements Serializable {
    private static final long serialVersionUID = 1L;

    public static class ComponentBinding implements Serializable {
        private static final long serialVersionUID = 1L;

        public String componentName;
        public List<Binding> bindings;

        public ComponentBinding(String componentName,
                List<Binding> bindings) {
            this.componentName = componentName;
            this.bindings = bindings;
        }
    }

    /**
     * Path of the field to be replaced e.g [healthConfig, urlPath]
     */
    public List<String> targetFieldPath;
    public String bindingExpression; //TODO could be a list of fields, but not sure about the syntax yet
    public boolean isProvisioningTimeBinding;

    public Binding(List<String> targetFieldPath,
            String bindingExpression, boolean isProvisioningTimeBinding) {
        this.targetFieldPath = targetFieldPath;
        this.bindingExpression = bindingExpression;
        this.isProvisioningTimeBinding = isProvisioningTimeBinding;
    }
}
