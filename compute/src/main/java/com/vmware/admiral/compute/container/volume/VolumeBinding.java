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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vmware.xenon.common.LocalizableValidationException;

/*
 * Volume binding parsing utility. A volume binding is a string in the following form:
 * [volume-name|host-src:]container-dest[:ro]. Both host-src, and container-dest must be
 * an absolute path.
 */
public class VolumeBinding {

    private static final String RX_HOSTDIR = "(?:(?:/(?:[a-zA-Z0-9_.-]+))+/?)|/";
    private static final String RX_NAME = "[a-zA-Z0-9_.-]+";
    private static final String RX_SOURCE = "(?:(?<src>(?:" + RX_HOSTDIR + ")|(?:" + RX_NAME + ")):)?";
    private static final String RX_DESTINATION = "(?<dst>" + RX_HOSTDIR + ")";
    private static final String RX_MODE = "(?::(?<mode>ro))?";
    private static final Pattern VOLUME_STRING_PATTERN = Pattern.compile("^" + RX_SOURCE + RX_DESTINATION + RX_MODE + "$");

    private String hostPart;
    private String containerPart;
    private boolean readOnly;

    public String getHostPart() {
        return hostPart;
    }

    public String getContainerPart() {
        return containerPart;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public static VolumeBinding fromString(String volume) {
        Matcher matcher = VOLUME_STRING_PATTERN.matcher(volume);

        if (!matcher.matches()) {
            throw new LocalizableValidationException("Volume must be [host_path|named_volume:]container_path[:ro]",
                    "compute.volume-binding.format");
        }

        VolumeBinding volumeString = new VolumeBinding();
        volumeString.hostPart = matcher.group("src");
        volumeString.containerPart = matcher.group("dst");
        volumeString.readOnly = matcher.group("mode") != null;

        return volumeString;
    }
}
