/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatefulService;

/**
 * This service represents the publicly exposed services and their access points.
 */
public class ExposedServiceDescriptionService extends StatefulService {

    public static class ExposedServiceDescriptionState extends
            com.vmware.xenon.common.ServiceDocument {
        /**
         * The host that will be the public entry point of a service.
         * It will serve external clients' requests and load balance them.
         */
        public String hostLink;

        /**
         * The addresses that expose the service, mapped by alias and port.
         */
        public ServiceAddressConfig[] addressConfigs;
    }

    public static class ExposedServiceDescriptionFactoryService extends FactoryService {
        public static final String SELF_LINK = ManagementUriParts.CONTAINER_EXPOSED_SERVICES;

        public ExposedServiceDescriptionFactoryService() {
            super(ExposedServiceDescriptionState.class);
            super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        }

        @Override
        public Service createServiceInstance() {
            return new ExposedServiceDescriptionService();
        }
    }

    public ExposedServiceDescriptionService() {
        super(ExposedServiceDescriptionState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }
}
