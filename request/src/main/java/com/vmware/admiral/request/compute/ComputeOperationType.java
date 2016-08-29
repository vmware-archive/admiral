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

package com.vmware.admiral.request.compute;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.request.compute.ComputeOperationTaskService.ComputeOperationTaskState;
import com.vmware.photon.controller.model.adapterapi.ComputePowerRequest;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerTransition;

public enum ComputeOperationType {

    CREATE("Compute.Create") {

        @Override
        public Object getBody(ComputeOperationTaskState state, URI computeReference,
                URI callbackReference) {
            return null;
        }

        @Override
        public URI getAdapterReference(ComputeStateWithDescription compute) {
            return compute.description.instanceAdapterReference;
        }

    },
    DELETE("Compute.Delete") {

        @Override
        public Object getBody(ComputeOperationTaskState state, URI computeReference,
                URI callbackReference) {
            return null;
        }

        @Override
        public URI getAdapterReference(ComputeStateWithDescription compute) {
            return compute.description.instanceAdapterReference;
        }

    },
    POWER_ON("Compute.PowerOn") {

        @Override
        public Object getBody(ComputeOperationTaskState state, URI computeReference,
                URI callbackReference) {
            ComputePowerRequest cpr = new ComputePowerRequest();
            cpr.resourceReference = computeReference;
            cpr.powerState = PowerState.ON;
            cpr.taskReference = callbackReference;
            cpr.isMockRequest = DeploymentProfileConfig.getInstance().isTest();
            return cpr;
        }

        @Override
        public URI getAdapterReference(ComputeStateWithDescription compute) {
            return compute.description.powerAdapterReference;
        }

    },
    POWER_OFF("Compute.PowerOff") {

        @Override
        public Object getBody(ComputeOperationTaskState state, URI computeReference,
                URI callbackReference) {
            ComputePowerRequest cpr = new ComputePowerRequest();
            cpr.resourceReference = computeReference;
            cpr.powerState = PowerState.OFF;
            cpr.powerTransition = PowerTransition.SOFT;
            cpr.taskReference = callbackReference;
            cpr.isMockRequest = DeploymentProfileConfig.getInstance().isTest();
            return cpr;
        }

        @Override
        public URI getAdapterReference(ComputeStateWithDescription compute) {
            return compute.description.powerAdapterReference;
        }

    },
    STATS("Compute.Stats") {

        @Override
        public Object getBody(ComputeOperationTaskState state, URI computeReference,
                URI callbackReference) {
            return null;
        }

        @Override
        public URI getAdapterReference(ComputeStateWithDescription compute) {
            return compute.description.statsAdapterReference;
        }

    };

    private static final Map<String, ComputeOperationType> operationsById = new HashMap<>();

    static {
        for (ComputeOperationType opr : values()) {
            operationsById.put(opr.id, opr);
        }
    }

    public final String id;

    ComputeOperationType(String id) {
        this.id = id;
    }

    public static ComputeOperationType instanceById(String id) {
        if (id == null) {
            return null;
        }
        return operationsById.get(id);
    }

    public static String extractDisplayName(String id) {
        return id.substring(id.lastIndexOf(".") + 1);
    }

    public abstract Object getBody(ComputeOperationTaskState state, URI computeReference,
            URI callbackReference);

    public abstract URI getAdapterReference(ComputeStateWithDescription compute);

    @Override
    public String toString() {
        return id;
    }
}
