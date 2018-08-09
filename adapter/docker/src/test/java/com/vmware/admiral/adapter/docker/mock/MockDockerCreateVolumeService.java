/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
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
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.CREATE;
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.VOLUMES;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;

import com.vmware.admiral.adapter.docker.mock.MockDockerVolumeListService.VolumeItem;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class MockDockerCreateVolumeService extends StatelessService {

    public static final String SELF_LINK = BASE_VERSIONED_PATH + VOLUMES + CREATE;

    private static final String VOLUME_NAME_KEY = "Name";
    private static final String VOLUME_DRIVER_KEY = "flocker";
    private static final String VOLUME_DIRECTORY_KEY = "/tmp";

    private final AtomicInteger idSequence = new AtomicInteger();

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.POST) {
            handlePost(op);

        } else {
            Operation.failActionNotSupported(op);
        }
    }

    @Override
    public void handlePost(Operation post) {

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = post.getBody(Map.class);

            AssertUtil.assertNotNull(request.get("Driver"), "Driver");

            if (!request.containsKey(VOLUME_NAME_KEY)
                    || StringUtils.isEmpty(request.get(VOLUME_NAME_KEY).toString())) {
                createVolumeName(request);
            }

            VolumeItem volumeItem = new VolumeItem();
            volumeItem.Name = request.get(VOLUME_NAME_KEY).toString();
            volumeItem.Driver = VOLUME_DRIVER_KEY;
            volumeItem.Mountpoint = VOLUME_DIRECTORY_KEY;

            MockDockerVolumeListService.volumesList.add(volumeItem);

            post.setBody(request);
            post.complete();

        } catch (Exception e) {
            post.fail(e);
            return;
        }

    }

    public Map<String, Object> createVolumeName(Map<String, Object> body) {

        // create a 64 char long ID
        body.put(VOLUME_NAME_KEY, String.format("%064d", idSequence.incrementAndGet()));

        return body;
    }

}
