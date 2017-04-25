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

package com.vmware.admiral.host.interceptor;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.photon.controller.model.adapters.vsphere.DatacenterEnumeratorService;
import com.vmware.photon.controller.model.adapters.vsphere.DatacenterEnumeratorService.EnumerateDatacentersRequest;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;

/**
 * Interceptor that initializes the {@code isMock} property for photon-model requests based on
 * Admiral's {@link DeploymentProfileConfig} configuration. This is only needed for requests that
 * are sent outside Admiral's code, i.e. from the UI or other clients.
 *
 * Could be generalized in the future, but for now there is a dedicated handler for each type of
 * request.
 */
public class PhotonModelMockRequestInterceptor {
    public static void register(OperationInterceptorRegistry registry) {
        registry.addServiceInterceptor(DatacenterEnumeratorService.class, Action.PATCH,
                PhotonModelMockRequestInterceptor::handleDatacenterEnumerationRequest);
    }

    public static DeferredResult<Void> handleDatacenterEnumerationRequest(Service service,
            Operation operation) {
        if (!DeploymentProfileConfig.getInstance().isTest()) {
            return null;
        }

        EnumerateDatacentersRequest body = operation.getBody(EnumerateDatacentersRequest.class);
        if (body.isMock) {
            return null;
        }

        body.isMock = true;
        operation.setBodyNoCloning(body);
        return DeferredResult.completed(null);
    }
}
