/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.adapters.registry.operations;

import java.util.Map;

import com.vmware.photon.controller.model.adapterapi.ResourceRequest;

/**
 * {@link ResourceOperationRequest} is the contract between the caller/consumer of a service
 * registered with {@link com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec}
 * and the adapter providing the actual functionality, manifested by the same {@link
 * com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec}.
 * In other words this is the body of the request to the resource operation adapter.
 */
public class ResourceOperationRequest extends ResourceRequest {
    /**
     * This is the value of {@link com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec#operation}
     * <p>
     * Caller of the Resource Operation Adapter, specified by the {@link
     * com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec},
     * shall set the value, so that the adapter knows for which operation was called. This is
     * introduced for convenience, so that a single adapter can serve multiple operations
     */
    public String operation;

    /**
     * This is the actual payload of the resource operation's request.
     */
    public Map<String, String> payload;
}
