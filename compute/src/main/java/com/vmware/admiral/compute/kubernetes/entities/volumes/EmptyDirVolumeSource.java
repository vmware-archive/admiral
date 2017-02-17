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
 * Represents an empty directory for a pod. Empty directory volumes support ownership
 * management and SELinux relabeling.
 */
public class EmptyDirVolumeSource {

    /**
     * What type of storage medium should back this directory.
     * The default is "" which means to use the node’s default medium.
     * Must be an empty string (default) or Memory.
     */
    public String medium;
}
