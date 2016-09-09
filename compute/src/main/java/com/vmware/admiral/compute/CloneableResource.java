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

package com.vmware.admiral.compute;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

/**
 * Resource providing createCloneOperation() implementation.
 */
public interface CloneableResource {
    public static final String PARENT_RESOURCE_LINK_PROPERTY_NAME = "__parentResourceLink";

    /**
     * Clone current state and create {@link Operation} with cloned state as body.
     */
    Operation createCloneOperation(Service sender);
}
