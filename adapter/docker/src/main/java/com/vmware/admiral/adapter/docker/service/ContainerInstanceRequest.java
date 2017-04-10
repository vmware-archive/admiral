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

package com.vmware.admiral.adapter.docker.service;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;

/**
 * Request a container operation (create/delete/etc.). The container reference provides detailed
 * information on the container to create and the host is referenced in its parentLink.
 */
public class ContainerInstanceRequest extends AdapterRequest {

    @Override
    public void validate() {
        super.validate();
        if (operationTypeId != null) {
            if (ContainerOperationType.instanceById(operationTypeId) == null) {
                throw new IllegalArgumentException("Not valid operationTypeId: " + operationTypeId);
            }
        }
    }

    public ContainerOperationType getOperationType() {
        return ContainerOperationType.instanceById(operationTypeId);
    }

}
