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

package com.vmware.admiral.adapter.pks.service;

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_ENDPOINT_QUERY_PARAM_NAME;

import java.util.Map;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.pks.PKSOperationType;
import com.vmware.admiral.adapter.pks.entities.PKSPlan;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Help service to retrieve existing Kubernetes clusters for a given PKS endpoint.
 * A flag indicates whether each cluster has been added to Admiral.
 */
public class PKSPlanListService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.PKS_PLANS;

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() != Action.GET) {
            Operation.failActionNotSupported(op);
            return;
        }

        super.handleRequest(op);
    }

    @Override
    public void handleGet(Operation op) {
        try {
            Map<String, String> queryParams = UriUtils.parseUriQueryParams(op.getUri());

            String endpointLink = queryParams.get(PKS_ENDPOINT_QUERY_PARAM_NAME);
            AssertUtil.assertNotNullOrEmpty(endpointLink, PKS_ENDPOINT_QUERY_PARAM_NAME);

            handleListRequest(op, endpointLink);
        } catch (Exception x) {
            logSevere(x);
            op.fail(x);
        }
    }

    private void handleListRequest(Operation op, String endpointLink) {
        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = PKSOperationType.LIST_PLANS.toString();
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = UriUtils.buildUri(getHost(), endpointLink);

        sendRequest(Operation.createPatch(this, ManagementUriParts.ADAPTER_PKS)
                .setBodyNoCloning(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere("Adapter request for listing PKS plans failed. Error: %s",
                                Utils.toString(ex));
                        op.fail(ex);
                    } else {
                        PKSPlan[] pksPlans = o.getBody(PKSPlan[].class);
                        op.setBodyNoCloning(pksPlans);
                        op.complete();
                    }
                }));
    }

}
