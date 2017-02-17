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
 *Represents a Persistent Disk resource in Google Compute Engine.
 * A GCE PD must exist before mounting to a container. The disk must also be in the same
 * GCE project and zone as the kubelet. A GCE PD can only be mounted as read/write once or
 * read-only many times. GCE PDs support ownership management and SELinux relabeling.
 */
public class GCEPersistentDiskVolumeSource {

    /**
     * Unique name of the PD resource in GCE. Used to identify the disk in GCE.
     */
    public String pdName;

    /**
     * Filesystem type of the volume that you want to mount.
     * Tip: Ensure that the filesystem type is supported by the host operating system.
     * Examples: "ext4", "xfs", "ntfs". Implicitly inferred to be "ext4" if unspecified.
     */
    public String fsType;

    /**
     * The partition in the volume that you want to mount.
     * If omitted, the default is to mount by volume name.
     */
    public Integer partition;

    /**
     * ReadOnly here will force the ReadOnly setting in VolumeMounts.
     */
    public Boolean readOnly;
}
