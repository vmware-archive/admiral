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

import com.vmware.admiral.compute.kubernetes.entities.common.LocalObjectReference;

/**
 * Represents a Rados Block Device mount that lasts the lifetime of a pod.
 * RBD volumes support ownership management and SELinux relabeling.
 */
public class RBDVolumeSource {

    /**
     * A collection of Ceph monitors.
     */
    public List<String> monitors;

    /**
     * The rados image name.
     */
    public String image;

    /**
     * Filesystem type of the volume that you want to mount. Tip: Ensure that the filesystem type
     * is supported by the host operating system.
     * Examples: "ext4", "xfs", "ntfs". Implicitly inferred to be "ext4" if unspecified.
     */
    public String fsType;

    /**
     * The rados pool name. Default is rbd.
     */
    public String pool;

    /**
     * The rados user name. Default is admin.
     */
    public String user;

    /**
     * Keyring is the path to key ring for RBDUser. Default is /etc/ceph/keyring.
     */
    public String keyring;

    /**
     * SecretRef is name of the authentication secret for RBDUser.
     * If provided overrides keyring. Default is nil.
     */
    public LocalObjectReference sourceRef;

    /**
     * ReadOnly here will force the ReadOnly setting in VolumeMounts. Defaults to false.
     */
    public Boolean readOnly;
}
