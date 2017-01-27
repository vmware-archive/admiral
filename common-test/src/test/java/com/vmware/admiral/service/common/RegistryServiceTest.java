/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.RegistryService.RegistryState;

public class RegistryServiceTest extends BaseRegistryStateQueryTest {

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    @Test
    public void testDeleteDefaultRegistryOnStartup() throws Throwable {
        RegistryState registryState = new RegistryState();
        registryState.documentSelfLink = RegistryService.DEFAULT_INSTANCE_LINK;
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;
        registryState.address = RegistryService.DEFAULT_REGISTRY_ADDRESS;
        registryState = doPost(registryState, RegistryService.FACTORY_LINK);

        assertNotNull("Failed to create default registry", registryState);

        ConfigurationState config = new ConfigurationState();
        config.key = RegistryService.DISABLE_DEFAULT_REGISTRY_PROP_NAME;
        config.value = Boolean.toString(true);

        ConfigurationUtil.initialize(config);

        RegistryService.buildDefaultStateInstance(host);

        waitFor("Ensure default registry is deleted.", () -> {
            List<String> resourceLinks = findResourceLinks(RegistryState.class,
                    Collections.singletonList(RegistryService.DEFAULT_INSTANCE_LINK));
            return resourceLinks.size() == 0;
        });
    }
}
