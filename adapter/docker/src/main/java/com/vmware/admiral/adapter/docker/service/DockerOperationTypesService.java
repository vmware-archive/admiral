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

import static com.vmware.admiral.common.util.ServiceUtils.addServiceRequestRoute;

import java.util.ArrayList;
import java.util.List;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;

/**
 * Service to provide all Docker container Day2 operation types.
 */
public class DockerOperationTypesService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_OPERATIONS;

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() != Action.GET) {
            Operation.failActionNotSupported(op);
            return;
        }

        handleGet(op);
    }

    @Override
    public void handleGet(Operation op) {
        List<String> operationTypes = new ArrayList<>();
        for (ContainerOperationType requestType : ContainerOperationType.values()) {
            operationTypes.add(requestType.id);
        }
        op.setBody(operationTypes);
        op.complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        addServiceRequestRoute(d, Action.GET,
                "Get list containing docker Day 2 operation types.", String[].class);
        return d;
    }
}
