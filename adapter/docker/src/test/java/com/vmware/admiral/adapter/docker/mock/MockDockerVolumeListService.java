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
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.VOLUMES;

import java.util.ArrayList;
import java.util.List;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class MockDockerVolumeListService extends StatelessService {

    public static final String SELF_LINK = BASE_VERSIONED_PATH + VOLUMES;

    public static List<VolumeItem> volumesList = new ArrayList<>();

    /**
     * Basic POJO for mocked volumes. For example:
     *
     * [
     *   {
     *   "Name": "foo",
     *   "Driver": "local",
     *   "Mountpoint": "/mnt/sda1/var/lib/docker/volumes/foo/_data"
     *   }
     * ]
     *
     */
    public static class VolumeItem {

        /**
         * The name of the volume.
         */
        public String Name;

        /**
         * Driver which volume uses.Default is 'local'.
         */
        public String Driver;

        /**
         * Volume directory.
         */
        public String Mountpoint;
    }

    @Override
    public void handleGet(Operation get) {
        get.setBody(volumesList);
        get.complete();
    }

}
