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

package com.vmware.admiral.service.common;

import java.util.ArrayList;
import java.util.List;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;

/**
 * Initial boot service for creating system default documents for the common module.
 */
public class CommonInitialBootService extends AbstractInitialBootService {
    public static final String SELF_LINK = ManagementUriParts.CONFIG + "/common-initial-boot";

    @Override
    public void handlePost(Operation post) {
        ConfigurationState[] configs = ConfigurationService.getConfigurationProperties();
        initInstances(post, false, false, configs);
        ConfigurationUtil.initialize(configs);

        List<ServiceDocument> resources = new ArrayList<>();
        resources.add(ResourceNamePrefixService.buildDefaultStateInstance());

        ServiceDocument defaultRegistryState = RegistryService.buildDefaultStateInstance(getHost());
        if (defaultRegistryState != null) {
            resources.add(defaultRegistryState);
        }

        initInstances(post, resources.toArray(new ServiceDocument[resources.size()]));
    }
}
