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

package com.vmware.photon.controller.model.adapters.registry;

import java.util.logging.Level;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecFactoryService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;

/**
 * Helper class to start Photon model adapter registry related services
 */
public class PhotonModelAdaptersRegistryAdapters {
    public static final String[] LINKS = {
            PhotonModelAdaptersRegistryService.FACTORY_LINK,
            ResourceOperationSpecService.FACTORY_LINK,
            ResourceOperationService.SELF_LINK
    };

    public static void startServices(ServiceHost host) {
        try {
            host.startFactory(
                    PhotonModelAdaptersRegistryService.class,
                    PhotonModelAdaptersRegistryFactoryService::new);
            host.startFactory(
                    ResourceOperationSpecService.class,
                    ResourceOperationSpecFactoryService::new);
            host.startService(new ResourceOperationService());
        } catch (Exception e) {
            host.log(Level.WARNING, "Error on start adapter registry related services. %s",
                    Utils.toString(e));
            throw e;
        }
    }
}
