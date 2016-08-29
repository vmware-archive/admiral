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

package com.vmware.admiral.host;

import java.util.function.Predicate;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Prevent deletion of {@link ResourcePoolState} if its in use by a {@link ComputeState}
 */
class ResourcePoolOperationProcessingChain extends OperationProcessingChain {

    public ResourcePoolOperationProcessingChain(ResourcePoolService service) {
        super(service);
        this.add(new Predicate<Operation>() {

            @Override
            public boolean test(Operation op) {
                if (op.getAction() != Action.DELETE) {
                    return true;
                }

                service.sendRequest(Operation.createPost(service, ServiceUriPaths.CORE_QUERY_TASKS)
                        .setBody(QueryUtil.addCountOption(QueryUtil.buildPropertyQuery(ComputeState.class,
                                ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, service.getSelfLink())))
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                service.logWarning(Utils.toString(e));
                                op.fail(e);
                            }
                            ServiceDocumentQueryResult result = o.getBody(QueryTask.class).results;
                            if (result.documentCount != 0) {
                                op.fail(new IllegalStateException("Resource Pool is in use"));
                            }
                            resumeProcessingRequest(op, this);
                        }));

                return false;
            }
        });
    }

}
