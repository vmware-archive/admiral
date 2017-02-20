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
import com.vmware.admiral.compute.kubernetes.entities.replicationcontrollers.ReplicationController;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService.ReplicationControllerState;
import com.vmware.photon.controller.model.resources.ResourceState;

public class ReplicationControllerService
        extends AbstractKubernetesObjectService<ReplicationControllerState> {

    public static final String FACTORY_LINK = ManagementUriParts.KUBERNETES_REPLICATION_CONTROLLERS;

    public static class ReplicationControllerState extends ResourceState {

        /**
         * ReplicationController represents the configuration of a replication controller.
         */
        @Documentation(
                description = "ReplicationController represents the configuration of a replication controller.")
        public ReplicationController replicationController;

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

    public ReplicationControllerService() {
        super(ReplicationControllerState.class);
    }
}
