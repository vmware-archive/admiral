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
import com.vmware.admiral.compute.kubernetes.entities.deployments.Deployment;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService.DeploymentState;
import com.vmware.xenon.common.Utils;

public class DeploymentService extends AbstractKubernetesObjectService<DeploymentState> {

    public static final String FACTORY_LINK = ManagementUriParts.KUBERNETES_DEPLOYMENTS;

    public static class DeploymentState extends BaseKubernetesState {

        /**
         * Deployment enables declarative updates for Pods and ReplicaSets.
         */
        @Documentation(
                description = "Deployment enables declarative updates for Pods and ReplicaSets.")
        public Deployment deployment;

        @Override
        public String getKubernetesSelfLink() {
            return this.deployment.metadata.selfLink;
        }

        @Override
        public String getType() {
            return KubernetesUtil.DEPLOYMENT_TYPE;
        }

        @Override
        public void setKubernetesEntityFromJson(String json) {
            this.deployment = Utils.fromJson(json, Deployment.class);
        }

        @Override
        public ObjectMeta getMetadata() {
            return this.deployment.metadata;
        }

        @Override
        public BaseKubernetesObject getEntityAsBaseKubernetesObject() {
            return this.deployment;
        }
    }

    public DeploymentService() {
        super(DeploymentState.class);
    }
}
