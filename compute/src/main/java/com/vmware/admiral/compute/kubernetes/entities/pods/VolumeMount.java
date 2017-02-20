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

package com.vmware.admiral.compute.kubernetes.entities.pods;

/**
 * VolumeMount describes a mounting of a Volume within a container.
 */
public class VolumeMount {

    /**
     * This must match the Name of a Volume.
     */
    public String name;

    /**
     * Mounted read-only if true, read-write otherwise (false or unspecified). Defaults to false.
     */
    public Boolean readOnly;

    /**
     * Path within the container at which the volume should be mounted. Must not contain ":".
     */
    public String mountPath;

    /**
     * Path within the volume from which the container’s volume should be mounted.
     * Defaults to "" (volume’s root).
     */
    public String subPath;
}
