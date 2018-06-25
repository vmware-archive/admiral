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

import static com.vmware.admiral.adapter.pks.entities.PKSCluster.PARAMETER_MASTER_HOST;
import static com.vmware.admiral.adapter.pks.entities.PKSCluster.PARAMETER_MASTER_PORT;

import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.pks.PKSOperationType;
import com.vmware.admiral.adapter.pks.entities.PKSCluster;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.kubernetes.entities.config.KubeConfig;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class MockPKSAdapterService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_PKS;

    public static final String CLUSTER1_UUID = UUID.randomUUID().toString();
    public static final String CLUSTER2_UUID = UUID.randomUUID().toString();

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() != Action.PATCH) {
            op.fail(new IllegalArgumentException("action not supported"));
            return;
        }

        AdapterRequest request = op.getBody(AdapterRequest.class);
        request.validate();

        if (PKSOperationType.CREATE_USER.id.equals(request.operationTypeId)) {
            KubeConfig.UserEntry userEntry = new KubeConfig.UserEntry();
            userEntry.name = "user";
            userEntry.user = new KubeConfig.AuthInfo();
            userEntry.user.token = "token";
            KubeConfig result = new KubeConfig();
            result.users = Arrays.asList(userEntry);
            op.setBodyNoCloning(result).complete();
            return;
        }

        if (PKSOperationType.LIST_CLUSTERS.id.equals(request.operationTypeId)) {
            PKSCluster cluster1 = new PKSCluster();
            cluster1.name = "cluster1";
            cluster1.uuid = CLUSTER1_UUID;
            cluster1.planName = "small";
            cluster1.parameters = new HashMap<>();
            cluster1.parameters.put(PARAMETER_MASTER_HOST, "30.0.1.2");
            cluster1.parameters.put(PARAMETER_MASTER_PORT, "8443");

            PKSCluster cluster2 = new PKSCluster();
            cluster2.name = "cluster2";
            cluster2.uuid = CLUSTER2_UUID;
            cluster2.planName = "production";
            cluster2.parameters = new HashMap<>();
            cluster2.parameters.put(PARAMETER_MASTER_HOST, "30.0.1.6");
            cluster2.parameters.put(PARAMETER_MASTER_PORT, "8443");

            op.setBodyNoCloning(Arrays.asList(cluster1, cluster2)).complete();
            return;
        }

        op.fail(new IllegalArgumentException("operation not supported"));
    }
}
