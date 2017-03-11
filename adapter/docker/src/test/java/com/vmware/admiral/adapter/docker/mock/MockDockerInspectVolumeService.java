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

package com.vmware.admiral.adapter.docker.mock;

import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.BASE_VERSIONED_PATH;
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.VOLUMES;

import com.vmware.admiral.adapter.docker.mock.MockDockerVolumeListService.VolumeItem;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class MockDockerInspectVolumeService extends StatelessService {

    private static final String TEST_FOO_VOLUME_NAME = "/foo";

    public static final String SELF_LINK = BASE_VERSIONED_PATH + VOLUMES + TEST_FOO_VOLUME_NAME;

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.GET) {
            handleGet(op);

        } else {
            Operation.failActionNotSupported(op);
        }
    }

    @Override
    public void handleGet(Operation get) {
        AssertUtil.assertNotNull(MockDockerVolumeListService.volumesList,
                "MockDockerVolumeListService.volumesList");
        AssertUtil.assertNotEmpty(MockDockerVolumeListService.volumesList,
                "MockDockerVolumeListService.volumesList");

        VolumeItem volumeItem = MockDockerVolumeListService.volumesList.get(0);
        get.setBody(volumeItem);

        get.complete();

    }

}
