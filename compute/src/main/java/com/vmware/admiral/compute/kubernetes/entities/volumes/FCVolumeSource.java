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
 * Represents a Fibre Channel volume. Fibre Channel volumes can only be mounted as read/write once.
 * Fibre Channel volumes support ownership management and SELinux relabeling.
 */
public class FCVolumeSource {

    /**
     * Required: FC target worldwide names (WWNs)
     */
    public List<String> targetWWNs;

    /**
     * Required: FC target lun number
     */
    public Integer lun;

    /**
     * Filesystem type to mount. Must be a filesystem type supported by the host operating system.
     * Ex. "ext4", "xfs", "ntfs". Implicitly inferred to be "ext4" if unspecified.
     */
    public String fsType;

    /**
     * Optional: Defaults to false (read/write). ReadOnly here will force the ReadOnly setting in
     * VolumeMounts.
     */
    public Boolean readOnly;
}
