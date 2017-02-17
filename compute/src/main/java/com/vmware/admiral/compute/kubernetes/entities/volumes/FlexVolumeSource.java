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

import java.util.Map;

import com.vmware.admiral.compute.kubernetes.entities.LocalObjectReference;

/**
 * FlexVolume represents a generic volume resource that is
 * provisioned/attached using an exec based plugin. This is an alpha feature and may change in future.
 */
public class FlexVolumeSource {

    /**
     * Driver is the name of the driver to use for this volume.
     */
    public String driver;

    /**
     * Filesystem type to mount. Must be a filesystem type supported by the host operating system.
     * Ex. "ext4", "xfs", "ntfs". The default filesystem depends on FlexVolume script.
     */
    public String fsType;

    /**
     * Optional: SecretRef is reference to the secret object containing sensitive information
     * to pass to the plugin scripts. This may be empty if no secret object is specified.
     * If the secret object contains more than one secret, all secrets are passed to the plugin scripts.
     */
    public LocalObjectReference secretRef;

    /**
     * Optional: Defaults to false (read/write).
     * ReadOnly here will force the ReadOnly setting in VolumeMounts
     */
    public Boolean readOnly;

    /**
     * Optional: Extra command options if any.
     */
    public Map<String, Object> options;
}
