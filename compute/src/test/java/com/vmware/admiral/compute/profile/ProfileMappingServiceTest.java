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

package com.vmware.admiral.compute.profile;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.profile.ComputeProfileService;
import com.vmware.admiral.compute.profile.ProfileMappingService;
import com.vmware.admiral.compute.profile.ProfileMappingService.ProfileMappingState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;

/**
 * Tests for the {@link ProfileMappingService} class.
 */
public class ProfileMappingServiceTest extends ComputeBaseTest {

    @Test
    public void test() throws Throwable {
        waitForServiceAvailability(ProfileMappingService.SELF_LINK);
        Operation result = host.waitForResponse(Operation.createGet(host, ProfileMappingService.SELF_LINK));
        Map<String, Object> documents = result.getBody(ServiceDocumentQueryResult.class).documents;
        ProfileMappingState state = Utils.fromJson(documents.get(ComputeProfileService.FACTORY_LINK),
                ProfileMappingState.class);
        assertEquals(Arrays.asList("coreos", "ubuntu-1604"),
                state.mappings.get("imageMapping"));
        assertEquals(Arrays.asList("small", "medium", "large", "xlarge"),
                state.mappings.get("instanceTypeMapping"));
    }
}
