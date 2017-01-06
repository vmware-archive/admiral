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

package com.vmware.admiral.closures.services.closuredescription;

/**
 * Defines resource constants
 *
 */
public final class ResourcesConstants {

    public static final Integer MIN_MEMORY_MB_RES_CONSTRAINT = 50;
    public static final Integer MAX_MEMORY_MB_RES_CONSTRAINT = 1536;

    public static final Integer MIN_EXEC_TIMEOUT_SECONDS = 1;
    public static final Integer MAX_EXEC_TIMEOUT_SECONDS = 600;

    public static final Integer MIN_CPU_SHARES = 50;
    public static final Integer DEFAULT_CPU_SHARES = 1024;

    public static final Integer DEFAULT_EXEC_TIMEOUT_SECONDS = 180;
    public static final Integer DEFAULT_MEMORY_MB_RES_CONSTRAINT = MIN_MEMORY_MB_RES_CONSTRAINT;

    private ResourcesConstants() {

    }
}
