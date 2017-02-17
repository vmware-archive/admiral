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
 * Represents a Glusterfs mount that lasts the lifetime of a pod.
 * Glusterfs volumes do not support ownership management or SELinux relabeling.
 */
public class GlusterfsVolumeSource {

    /**
     * EndpointsName is the endpoint name that details Glusterfs topology.
     */
    public String endpoints;

    /**
     * Path is the Glusterfs volume path.
     */
    public String path;

    /**
     * ReadOnly here will force the Glusterfs volume to be mounted with read-only permissions.
     * Defaults to false.
     */
    public Boolean readOnly;
}
