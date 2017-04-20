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

package com.vmware.admiral.request.utils;

import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Service;

public abstract class RequestUtils {
    // Allocation constants:
    /**
     * Property field name indicating whether a give request is an allocation one - meaning that the
     * container states will be created and allocated but no actual provisioning will take place.
     */
    public static final String FIELD_NAME_ALLOCATION_REQUEST = "__allocation_request";

    /**
     * Property field name indicating whether a given request is a deallocation one - meaning that
     * the resource states will be deleted and deallocated.
     */
    public static final String FIELD_NAME_DEALLOCATION_REQUEST = "__deallocation_request";

    /**
     * Context id that spreads across multiple allocation request as part of multi-container
     * composite deployment.
     */
    public static final String FIELD_NAME_CONTEXT_ID_KEY = "__composition_context_id";

    /**
     * Flag that tells if this is a clustering request
     */
    public static final String CLUSTERING_OPERATION_CUSTOM_PROP = "__clustering_operation";

    private RequestUtils() {
    }

    public static String getContextId(TaskServiceDocument<?> state) {
        if (state.customProperties != null) {
            String contextId = state.customProperties.get(FIELD_NAME_CONTEXT_ID_KEY);
            if (contextId != null && !contextId.isEmpty()) {
                return contextId;
            }
        }

        return Service.getId(state.documentSelfLink);
    }
}
