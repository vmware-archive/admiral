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
 * Represents a Ceph Filesystem mount that lasts the lifetime of a pod Cephfs
 * volumes do not support ownership management or SELinux relabeling.
 */
public class CephFSVolumeSource {

    /**
     * Required: Monitors is a collection of Ceph monitors.
     */
    public List<String> monitors;

    /**
     * Optional: Used as the mounted root, rather than the full Ceph tree, default is /
     */
    public String path;

    /**
     * Optional: User is the rados user name, default is admin
     */
    public String user;

    /**
     * Optional: SecretFile is the path to key ring for User, default is /etc/ceph/user.secret
     */
    public String secretFile;

    /**
     * Optional: SecretRef is reference to the authentication secret for User, default is empty.
     */
    public LocalObjectReference secretRef;

    /**
     * Optional: Defaults to false (read/write). ReadOnly here will force the ReadOnly setting in VolumeMounts.
     */
    public Boolean readOnly;
}
