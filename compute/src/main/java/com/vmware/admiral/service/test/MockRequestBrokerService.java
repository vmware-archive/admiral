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

package com.vmware.admiral.service.test;

import com.amazonaws.services.elasticloadbalancing.model.InvalidConfigurationRequestException;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.cluster.ClusterService.ContainerHostRemovalTaskState;
import com.vmware.xenon.common.Operation;

public class MockRequestBrokerService extends BaseMockAdapterService {
    public static final String SELF_LINK = ManagementUriParts.REQUESTS;

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.POST) {
            ContainerHostRemovalTaskState containerHostRemovalTaskState = op
                    .getBody(ContainerHostRemovalTaskState.class);
            if (containerHostRemovalTaskState.resourceType
                    .equals(ContainerHostRemovalTaskState.RESOURCE_TYPE_CONTAINER_HOST)
                    && containerHostRemovalTaskState.operation
                            .equals(ContainerHostRemovalTaskState.OPERATION_REMOVE_RESOURCE)) {
                for (String computeDocumentSelfLink : containerHostRemovalTaskState.resourceLinks) {
                    deleteCompute(computeDocumentSelfLink);
                }
                op.complete();
                return;
            }
        }
        op.fail(new InvalidConfigurationRequestException("Operation not supported in MockRequestBrokerService"));
    }

    private void deleteCompute(String computeDocumentSelfLink) {
        getHost().sendWithDeferredResult(
                Operation.createDelete(getHost(), computeDocumentSelfLink)
                        .setReferer(getHost().getUri())
        );
    }
}
