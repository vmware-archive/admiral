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

package com.vmware.admiral.compute.kubernetes.service;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.admiral.compute.kubernetes.entities.common.ObjectMeta;
import com.vmware.admiral.compute.kubernetes.entities.replicaset.ReplicaSet;
import com.vmware.admiral.compute.kubernetes.service.ReplicaSetService.ReplicaSetState;
import com.vmware.xenon.common.Utils;

public class ReplicaSetService extends AbstractKubernetesObjectService<ReplicaSetState> {

    public static final String FACTORY_LINK = ManagementUriParts.KUBERNETES_REPLICA_SETS;

    public static class ReplicaSetState extends BaseKubernetesState {

        @Documentation(description = "ReplicaSet represents the configuration of a ReplicaSet.")
        public ReplicaSet replicaSet;

        @Override
        public String getType() {
            return KubernetesUtil.REPLICA_SET_TYPE;
        }

        @Override
        public String getKubernetesSelfLink() {
            return this.replicaSet.metadata.selfLink;
        }

        @Override
        public void setKubernetesEntityFromJson(String json) {
            this.replicaSet = Utils.fromJson(json, ReplicaSet.class);
        }

        @Override
        public ObjectMeta getMetadata() {
            return this.replicaSet.metadata;
        }

        @Override
        public BaseKubernetesObject getEntityAsBaseKubernetesObject() {
            return this.replicaSet;
        }
    }

    public ReplicaSetService() {
        super(ReplicaSetState.class);
    }
}
