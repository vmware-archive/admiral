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

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_CREATE;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_DELETE;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_STATE_FAILED;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_STATE_IN_PROGRESS;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_STATE_SUCCEEDED;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_UPDATE;
import static com.vmware.admiral.adapter.pks.entities.PKSCluster.PARAMETER_MASTER_HOST;
import static com.vmware.admiral.adapter.pks.entities.PKSCluster.PARAMETER_MASTER_PORT;

import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.pks.PKSException;
import com.vmware.admiral.adapter.pks.PKSOperationType;
import com.vmware.admiral.adapter.pks.entities.PKSCluster;
import com.vmware.admiral.adapter.pks.entities.PKSPlan;
import com.vmware.admiral.adapter.pks.util.PKSClusterMapper;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.kubernetes.entities.config.KubeConfig;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class MockPKSAdapterService extends StatelessService {

    public static final String CLUSTER_NAME_CREATE_SUCCESS = "unit-test-create-success";
    public static final String CLUSTER_NAME_CREATE_FAIL = "unit-test-create-fail";

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_PKS;

    public static final String CLUSTER1_UUID = UUID.randomUUID().toString();
    public static final String CLUSTER2_UUID = UUID.randomUUID().toString();

    private static String lastAction;
    private static int counter = 0;

    public static void resetCounter() {
        counter = 0;
    }

    public static void setLastAction(String lastAction) {
        MockPKSAdapterService.lastAction = lastAction;
    }

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
            result.clusters = Arrays.asList(new KubeConfig.ClusterEntry());
            result.clusters.get(0).name = "cluster-name";
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

        if (PKSOperationType.DELETE_CLUSTER.id.equals(request.operationTypeId)) {
            PKSCluster cluster = PKSClusterMapper.fromMap(request.customProperties);
            cluster.lastAction = PKS_LAST_ACTION_DELETE;
            cluster.lastActionState = PKS_LAST_ACTION_STATE_IN_PROGRESS;
            op.setBodyNoCloning(cluster).complete();
            setLastAction(PKS_LAST_ACTION_DELETE);
            return;
        }

        if (PKSOperationType.GET_CLUSTER.id.equals(request.operationTypeId)) {
            String clusterName = request.customProperties.get(PKS_CLUSTER_NAME_PROP_NAME);
            if (clusterName.equals(CLUSTER_NAME_CREATE_SUCCESS)) {
                PKSCluster c = constructPKSCluster(CLUSTER_NAME_CREATE_SUCCESS,
                        PKS_LAST_ACTION_CREATE, PKS_LAST_ACTION_STATE_IN_PROGRESS);

                if (counter++ >= 1) {
                    c.lastActionState = PKS_LAST_ACTION_STATE_SUCCEEDED;
                }

                if (PKS_LAST_ACTION_DELETE.equals(lastAction)) {
                    if (counter >= 2) {
                        PKSException pe = new PKSException("not found", new Exception(),
                                Operation.STATUS_CODE_NOT_FOUND);
                        op.fail(Operation.STATUS_CODE_NOT_FOUND, new Exception(pe), null);
                        return;
                    }
                    c.lastAction = PKS_LAST_ACTION_DELETE;
                } else if (PKS_LAST_ACTION_UPDATE.equals(lastAction)) {
                    c.lastAction = PKS_LAST_ACTION_UPDATE;
                }

                op.setBodyNoCloning(c).complete();
            } else if (clusterName.equals(CLUSTER_NAME_CREATE_FAIL)) {
                PKSCluster c = constructPKSCluster(
                        CLUSTER_NAME_CREATE_FAIL,
                        lastAction != null ? lastAction : PKS_LAST_ACTION_CREATE,
                        PKS_LAST_ACTION_STATE_IN_PROGRESS);
                if (counter++ >= 1) {
                    c.lastActionState = PKS_LAST_ACTION_STATE_FAILED;
                }
                op.setBodyNoCloning(c).complete();
            } else {
                op.fail(Operation.STATUS_CODE_NOT_FOUND);
            }

            return;
        }

        if (PKSOperationType.CREATE_CLUSTER.id.equals(request.operationTypeId)) {
            PKSCluster cluster = PKSClusterMapper.fromMap(request.customProperties);
            cluster.lastAction = PKS_LAST_ACTION_CREATE;
            cluster.lastActionState = PKS_LAST_ACTION_STATE_IN_PROGRESS;
            cluster.uuid = "-";
            op.setBodyNoCloning(cluster).complete();
            setLastAction(PKS_LAST_ACTION_CREATE);
            return;
        }

        if (PKSOperationType.LIST_PLANS.id.equals(request.operationTypeId)) {
            PKSPlan plan1 = new PKSPlan();
            plan1.id = "1";
            plan1.name = "tiny";
            plan1.description = "small plan";

            PKSPlan plan2 = new PKSPlan();
            plan2.id = "2";
            plan2.name = "huge";
            plan2.description = "big plan";

            op.setBodyNoCloning(Arrays.asList(plan1, plan2)).complete();
            return;
        }

        if (PKSOperationType.RESIZE_CLUSTER.id.equals(request.operationTypeId)) {
            PKSCluster cluster = PKSClusterMapper.fromMap(request.customProperties);
            cluster.lastAction = PKS_LAST_ACTION_UPDATE;
            cluster.lastActionState = PKS_LAST_ACTION_STATE_SUCCEEDED;
            cluster.uuid = "-";
            op.setBodyNoCloning(cluster).complete();
            setLastAction(PKS_LAST_ACTION_UPDATE);
            return;
        }


        op.fail(new IllegalArgumentException("operation not supported"));
    }

    private PKSCluster constructPKSCluster(String name, String lastAction, String lastActionState) {
        PKSCluster cluster = new PKSCluster();
        cluster.name = name;
        cluster.uuid = CLUSTER1_UUID;
        cluster.planName = "small";
        cluster.parameters = new HashMap<>();
        cluster.parameters.put(PARAMETER_MASTER_HOST, "30.0.1.2");
        cluster.parameters.put(PARAMETER_MASTER_PORT, "8443");
        cluster.lastAction = lastAction;
        cluster.lastActionState = lastActionState;
        return  cluster;
    }

}
