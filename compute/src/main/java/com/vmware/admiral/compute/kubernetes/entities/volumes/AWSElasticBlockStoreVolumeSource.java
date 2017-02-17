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
 * Represents a Persistent Disk resource in AWS.
 * An AWS EBS disk must exist before mounting to a container.
 * The disk must also be in the same AWS zone as the kubelet.
 * An AWS EBS disk can only be mounted as read/write once.
 * AWS EBS volumes support ownership management and SELinux relabeling.
 */
public class AWSElasticBlockStoreVolumeSource {

    /**
     * Unique ID of the persistent disk resource in AWS (Amazon EBS volume).
     */
    public String volumeID;

    /**
     * Filesystem type of the volume that you want to mount.
     * Tip: Ensure that the filesystem type is supported by the host operating system.
     */
    public String fsType;

    /**
     * The partition in the volume that you want to mount.
     * If omitted, the default is to mount by volume name.
     */
    public Integer partition;

    /**
     * Specify "true" to force and set the ReadOnly property in VolumeMounts to "true".
     * If omitted, the default is "false".
     */
    public Boolean readOnly;
}
