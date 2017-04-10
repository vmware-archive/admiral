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

package com.vmware.admiral.adapter.common;

import java.net.URI;
import java.util.Map;

import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Service;

/**
 * Adapter request object used to wrap the operation and the resource reference for an adapter to
 * execute a given operation.
 */
public class AdapterRequest {

    /** Id of operation */
    public String operationTypeId;

    /** The reference of the resource to which the operation will be applied */
    public URI resourceReference;

    /** Attributes for given request */
    public Map<String, String> customProperties;

    /**
     * The service callback, which the adapter will call with the status after operation is
     * completed
     */
    public ServiceTaskCallback serviceTaskCallback;

    public URI getContainerStateReference() {
        return resourceReference;
    }

    public void validate() {
        StringBuilder sb = new StringBuilder();
        if (resourceReference == null) {
            sb.append("'resourceReference' is required.");
        }
        if (serviceTaskCallback == null || serviceTaskCallback.serviceSelfLink == null
                || serviceTaskCallback.serviceSelfLink.isEmpty()) {
            sb.append(" 'service callback reference' is required.");
        }
        if (operationTypeId == null || operationTypeId.isEmpty()) {
            sb.append(" 'operationTypeId' is required for callback: "
                    + serviceTaskCallback.serviceSelfLink);
        }
        if (sb.length() > 0) {
            throw new IllegalArgumentException(sb.toString());
        }
    }

    public URI resolve(String link) {
        if (resourceReference == null) {
            return null;
        }
        return resourceReference.resolve(link);
    }

    public String getRequestTrackingLog() {
        return (serviceTaskCallback == null || serviceTaskCallback.isEmpty()) ? "" : " request: "
                + serviceTaskCallback.serviceSelfLink;
    }

    public String getRequestId() {
        if (serviceTaskCallback == null) {
            return null;
        }

        return serviceTaskCallback.serviceSelfLink != null ?
                Service.getId(serviceTaskCallback.serviceSelfLink)
                : null;
    }
}
