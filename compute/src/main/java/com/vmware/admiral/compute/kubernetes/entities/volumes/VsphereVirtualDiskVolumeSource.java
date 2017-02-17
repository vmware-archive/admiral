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

/**
 * Represents a vSphere volume resource.
 */
public class VsphereVirtualDiskVolumeSource {

    /**
     * Path that identifies vSphere volume vmdk.
     */
    public String volumePath;

    /**
     * Filesystem type to mount. Must be a filesystem type supported by the host operating system.
     * Ex. "ext4", "xfs", "ntfs". Implicitly inferred to be "ext4" if unspecified.
     */
    public String fsType;
}
