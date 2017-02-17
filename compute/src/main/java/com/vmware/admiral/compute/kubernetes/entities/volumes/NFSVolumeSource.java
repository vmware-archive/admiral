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
 * Represents an NFS mount that lasts the lifetime of a pod.
 * NFS volumes do not support ownership management or SELinux relabeling.
 */
public class NFSVolumeSource {

    /**
     * Server is the hostname or IP address of the NFS server.
     */
    public String server;

    /**
     * Path that is exported by the NFS server.
     */
    public String path;

    /**
     * ReadOnly here will force the NFS export to be mounted with read-only permissions.
     */
    public Boolean readOnly;
}
