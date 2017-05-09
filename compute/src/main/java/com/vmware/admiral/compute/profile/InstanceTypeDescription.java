/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.profile;

/**
 * Describes a compute instance type.
 */
public class InstanceTypeDescription {

    public String name;

    public String description;

    /**
     * Instance type identifier valid in the context of the particular endpoint this description is
     * created for. If {@code null}, detailed instance figures such as CPU and memory must be
     * provided instead.
     */
    public String instanceType;

    /**
     * CPU cores for the instance. Ignored if {@link #instanceType} is provided.
     */
    public int cpuCount;

    /**
     * Memory for the instance (in MBs). Ignored if {@link #instanceType} is provided.
     */
    public long memoryMb;

    /**
     * Disk size for the instance (in MBs). Ignored if {@link #instanceType} is provided.
     */
    public long diskSizeMb;
}
