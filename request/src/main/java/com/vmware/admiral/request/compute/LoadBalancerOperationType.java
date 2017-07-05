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

package com.vmware.admiral.request.compute;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.request.compute.LoadBalancerOperationTaskService.LoadBalancerOperationTaskState;
import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;

/**
 * Load balancer operation types
 */
public enum LoadBalancerOperationType {

    CREATE("LoadBalancer.Create") {
        @Override
        public Object getBody(LoadBalancerOperationTaskState state, URI resourceReference,
                URI callbackReference) {
            return null;
        }

        @Override
        public URI getAdapterReference(LoadBalancerState state) {
            return state.instanceAdapterReference;
        }
    },
    UPDATE("LoadBalancer.Update") {
        @Override
        public Object getBody(LoadBalancerOperationTaskState state, URI resourceReference,
                URI callbackReference) {
            LoadBalancerInstanceRequest req = new LoadBalancerInstanceRequest();
            req.requestType = InstanceRequestType.UPDATE;
            req.resourceReference = resourceReference;
            req.taskReference = callbackReference;
            req.isMockRequest = DeploymentProfileConfig.getInstance().isTest();
            return req;
        }

        @Override
        public URI getAdapterReference(LoadBalancerState state) {
            return state.instanceAdapterReference;
        }

    },
    DELETE("LoadBalancer.Delete") {
        @Override
        public Object getBody(LoadBalancerOperationTaskState state, URI resourceReference,
                URI callbackReference) {
            return null;
        }

        @Override
        public URI getAdapterReference(LoadBalancerState state) {
            return state.instanceAdapterReference;
        }
    };

    public final String id;

    LoadBalancerOperationType(String id) {
        this.id = id;
    }

    private static final Map<String, LoadBalancerOperationType> operationsById = new HashMap<>();

    static {
        for (LoadBalancerOperationType opr : values()) {
            operationsById.put(opr.id, opr);
        }
    }

    public static LoadBalancerOperationType instanceById(String id) {
        if (id == null) {
            return null;
        }
        return operationsById.get(id);
    }

    public static String extractDisplayName(String id) {
        return id.substring(id.lastIndexOf(".") + 1);
    }

    @Override
    public String toString() {
        return id;
    }

    public abstract Object getBody(LoadBalancerOperationTaskState state, URI resourceReference,
            URI callbackReference);

    public abstract URI getAdapterReference(LoadBalancerState state);
}
