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

package com.vmware.admiral.compute.kubernetes.service;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.admiral.compute.kubernetes.entities.common.ObjectMeta;
import com.vmware.admiral.compute.kubernetes.service.GenericKubernetesEntityService.GenericKubernetesEntityState;
import com.vmware.xenon.common.Utils;

public class GenericKubernetesEntityService
        extends AbstractKubernetesObjectService<GenericKubernetesEntityState> {

    public static final String FACTORY_LINK = ManagementUriParts.KUBERNETES_GENERIC_ENTITIES;

    public static class GenericKubernetesEntityState extends BaseKubernetesState {

        /**
         * Represents any kubernetes entity that can be scheduled on a node of the cluster.
         */
        @Documentation(description = "Represents any kubernetes entity that "
                + "can be scheduled on a node of the cluster.")
        public BaseKubernetesObject entity;

        @Override
        public String getType() {
            return this.entity != null ? this.entity.kind : null;
        }

        @Override
        public void setKubernetesEntityFromJson(String json) {
            this.entity = Utils.fromJson(json, BaseKubernetesObject.class);
        }

        @Override
        public ObjectMeta getMetadata() {
            return this.entity != null ? this.entity.metadata : null;
        }

        @Override
        public BaseKubernetesObject getEntityAsBaseKubernetesObject() {
            return this.entity;
        }
    }

    public GenericKubernetesEntityService() {
        super(GenericKubernetesEntityState.class);
    }
}
