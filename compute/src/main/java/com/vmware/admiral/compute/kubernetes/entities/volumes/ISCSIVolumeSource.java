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
 * Represents an ISCSI disk. ISCSI volumes can only be mounted as read/write once.
 * ISCSI volumes support ownership management and SELinux relabeling.
 */
public class ISCSIVolumeSource {

    /**
     * iSCSI target portal. The portal is either an IP or ip_addr:port
     * if the port is other than default (typically TCP ports 860 and 3260).
     */
    public String targetPortal;

    /**
     * Target iSCSI Qualified Name
     */
    public String iqn;

    /**
     * iSCSI target lun number.
     */
    public Integer lun;

    /**
     * Optional: Defaults to default (tcp).
     * iSCSI interface name that uses an iSCSI transport.
     */
    public String iscsiInterface;

    /**
     * Filesystem type of the volume that you want to mount. Tip: Ensure that the filesystem type
     * is supported by the host operating system.
     * Examples: "ext4", "xfs", "ntfs". Implicitly inferred to be "ext4" if unspecified.
     */
    public String fsType;

    /**
     * ReadOnly here will force the ReadOnly setting in VolumeMounts. Defaults to false.
     */
    public Boolean readOnly;
}
