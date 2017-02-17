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
 * Represents a Quobyte mount that lasts the lifetime of a pod. Quobyte volumes do not support
 * ownership management or SELinux relabeling.
 */
public class QuobyteVolumeSource {

    /**
     * Registry represents a single or multiple Quobyte Registry services specified as a string as
     * host:port pair (multiple entries are separated with commas) which acts as the central registry for volumes
     */
    public String registry;

    /**
     * Volume is a string that references an already created Quobyte volume by name.
     */
    public String volume;

    /**
     * ReadOnly here will force the Quobyte volume to be mounted with read-only permissions.
     * Defaults to false.
     */
    public Boolean readOnly;

    /**
     * User to map volume access to Defaults to serivceaccount user.
     */
    public String user;

    /**
     * Group to map volume access to Default is no group.
     */
    public String group;
}
