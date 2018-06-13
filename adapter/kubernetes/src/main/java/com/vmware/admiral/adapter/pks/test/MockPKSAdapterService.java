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

package com.vmware.admiral.adapter.pks.test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.pks.PKSOperationType;
import com.vmware.admiral.adapter.pks.entities.KubeConfig;
import com.vmware.admiral.adapter.pks.entities.KubeConfig.Token;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class MockPKSAdapterService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_PKS;

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() != Action.PATCH) {
            op.fail(new IllegalArgumentException("action not supported"));
            return;
        }

        AdapterRequest request = op.getBody(AdapterRequest.class);
        request.validate();

        if (PKSOperationType.CREATE_USER.id.equals(request.operationTypeId)) {
            KubeConfig.AuthInfo result = new KubeConfig.AuthInfo();
            result.name = "user";
            result.user = new Token();
            result.user.token = "token";
            op.setBodyNoCloning(result).complete();
            return;
        }

        op.fail(new IllegalArgumentException("operation not supported"));
    }
}
