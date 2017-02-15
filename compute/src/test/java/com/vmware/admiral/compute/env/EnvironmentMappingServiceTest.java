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

package com.vmware.admiral.compute.env;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.env.EnvironmentMappingService.EnvironmentMappingState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;

/**
 * Tests for the {@link EnvironmentMappingService} class.
 */
public class EnvironmentMappingServiceTest extends ComputeBaseTest {

    @Test
    public void test() throws Throwable {
        waitForServiceAvailability(EnvironmentMappingService.SELF_LINK);
        Operation result = host.waitForResponse(Operation.createGet(host, EnvironmentMappingService.SELF_LINK));
        Map<String, Object> documents = result.getBody(ServiceDocumentQueryResult.class).documents;
        EnvironmentMappingState state = Utils.fromJson(documents.get(ComputeProfileService.FACTORY_LINK),
                EnvironmentMappingState.class);
        assertEquals(Arrays.asList("coreos", "ubuntu-1604", "photon"),
                state.mappings.get("imageMapping"));
        assertEquals(Arrays.asList("small", "medium", "large", "xlarge"), state.mappings.get("instanceTypeMapping"));
    }
}
