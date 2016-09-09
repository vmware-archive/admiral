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

package com.vmware.admiral.compute.container.volume;

import org.apache.commons.lang3.StringUtils;

/**
 * Utility class for docker volume related operations.
 */
public class VolumeUtil {

    private static final String HOST_CONTAINER_DIR_DELIMITER = ":/";

    /**
     * Parses volume host directory only.
     *
     * @param volume
     *            - volume name. It might be named volume [volume-name] or path:
     *            [/host-directory:/container-directory, named-volume1:/container-directory]
     * @return host directory or named volume itself.
     */
    public static String parseVolumeHostDirectory(String volume) {

        if (StringUtils.isEmpty(volume)) {
            return volume;
        }

        if (!volume.contains(HOST_CONTAINER_DIR_DELIMITER)) {
            return volume;
        }

        String[] hostContainerDir = volume.split(HOST_CONTAINER_DIR_DELIMITER);

        if (hostContainerDir.length != 2) {
            throw new IllegalArgumentException("Invalid volume directory.");
        }

        String hostDir = hostContainerDir[0];

        return hostDir;
    }

}
