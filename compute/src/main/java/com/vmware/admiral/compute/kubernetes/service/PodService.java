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
import com.vmware.admiral.compute.kubernetes.entities.pods.Pod;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.xenon.common.Utils;

public class PodService extends AbstractKubernetesObjectService<PodState> {

    public static final String FACTORY_LINK = ManagementUriParts.KUBERNETES_PODS;

    public static class PodState extends BaseKubernetesState {

        /**
         * Pod is a collection of containers that can run on a host.
         * This resource is created by clients and scheduled onto hosts.
         */
        @Documentation(description = "Pod is a collection of containers that can run on a host. "
                + "This resource is created by clients and scheduled onto hosts.")
        public Pod pod;

        @Override
        public String getType() {
            return KubernetesUtil.POD_TYPE;
        }

        @Override
        public void setKubernetesEntityFromJson(String json) {
            this.pod = Utils.fromJson(json, Pod.class);
        }

        @Override
        public ObjectMeta getMetadata() {
            return this.pod.metadata;
        }

        @Override
        public BaseKubernetesObject getEntityAsBaseKubernetesObject() {
            return this.pod;
        }
    }

    public PodService() {
        super(PodState.class);
    }
}
