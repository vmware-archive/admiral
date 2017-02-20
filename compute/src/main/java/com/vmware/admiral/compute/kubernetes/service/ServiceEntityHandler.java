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
import com.vmware.admiral.compute.kubernetes.entities.services.Service;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler.ServiceState;
import com.vmware.photon.controller.model.resources.ResourceState;

public class ServiceEntityHandler extends AbstractKubernetesObjectService<ServiceState> {

    public static final String FACTORY_LINK = ManagementUriParts.KUBERNETES_SERVICES;

    public static class ServiceState extends ResourceState {

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

        /**
         * Defines the description of the entity
         */
        @Documentation(description = "Defines the description of the container.")
        public String descriptionLink;

        /**
         * Link to CompositeComponent when a entity is part of App/Composition request.
         */
        @Documentation(
                description = "Link to CompositeComponent when a entity is part of App/Composition request.")
        public String compositeComponentLink;

        /**
         * Entity host link
         */
        @Documentation(description = "Entity host link")
        public String parentLink;
    }

    public ServiceEntityHandler() {
        super(ServiceState.class);
    }
}
