/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.kubernetes.entities.volumes;

import java.util.List;

/**
 * DownwardAPIVolumeSource represents a volume containing downward API info.
 * Downward API volumes support ownership management and SELinux relabeling.
 */
public class DownwardAPIVolumeSource {

    /**
     * Items is a list of downward API volume file
     */
    public List<DownwardAPIVolumeFile> items;

    /**
     * Optional: mode bits to use on created files by default. Must be a value between 0 and 0777.
     * Defaults to 0644. Directories within the path are not affected by this setting.
     * This might be in conflict with other options that affect the file mode, like fsGroup,
     * and the result can be other mode bits set.
     */
    public Integer defaultMode;
}
