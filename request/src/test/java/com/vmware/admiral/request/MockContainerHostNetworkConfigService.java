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

package com.vmware.admiral.request;

import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService.ContainerHostNetworkConfigState;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService.ContainerNetworkConfigState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;

public class MockContainerHostNetworkConfigService extends StatefulService {
    private Map<String, ContainerNetworkConfigState> configs;

    public MockContainerHostNetworkConfigService() {
        super(ContainerHostNetworkConfigState.class);
        configs = new HashMap<>();
    }

    public ContainerNetworkConfigState getConfig(String containerLink) {
        return configs.get(containerLink);
    }

    public Map<String, ContainerNetworkConfigState> getConfigs() {
        return configs;
    }

    @Override
    public void handlePatch(Operation patch) {
        if (patch.hasBody()) {
            ContainerHostNetworkConfigState body = patch
                    .getBody(ContainerHostNetworkConfigState.class);

            if (body.remove) {
                for (String key : body.containerNetworkConfigs.keySet()) {
                    configs.remove(key);
                }
            } else {
                configs.putAll(body.containerNetworkConfigs);
            }
        }
        patch.complete();
    }
}
