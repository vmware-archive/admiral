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
import com.vmware.admiral.compute.kubernetes.entities.services.Service;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler.ServiceState;
import com.vmware.xenon.common.Utils;

public class ServiceEntityHandler extends AbstractKubernetesObjectService<ServiceState> {

    public static final String FACTORY_LINK = ManagementUriParts.KUBERNETES_SERVICES;

    public static class ServiceState extends BaseKubernetesState {

        /**
         * Service is a named abstraction of software service (for example, mysql) consisting of
         * local port (for example 3306) that the proxy listens on, and the selector that determines
         * which pods will answer requests sent through the proxy.
         */
        @Documentation(description =
                "Service is a named abstraction of software service (for example, mysql)"
                        + " consisting of local port (for example 3306) that the proxy listens on, "
                        + "and the selector that determines which pods will answer requests sent through the proxy.")
        public Service service;

        @Override
        public String getType() {
            return KubernetesUtil.SERVICE_TYPE;
        }

        @Override
        public void setKubernetesEntityFromJson(String json) {
            this.service = Utils.fromJson(json, Service.class);
        }

        @Override
        public ObjectMeta getMetadata() {
            return this.service.metadata;
        }

        @Override
        public BaseKubernetesObject getEntityAsBaseKubernetesObject() {
            return this.service;
        }
    }

    public ServiceEntityHandler() {
        super(ServiceState.class);
    }
}
