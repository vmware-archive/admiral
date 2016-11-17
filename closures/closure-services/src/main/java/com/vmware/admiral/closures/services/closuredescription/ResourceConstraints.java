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
 * Resource constraints to apply on code snippet execution.
 */
public class ResourceConstraints {

    /**
     * Required runtime CPU constraints.
     */
    public Integer cpuShares = ResourcesConstants.DEFAULT_CPU_SHARES;

    /**
     * Required runtime RAM constraints in megabytes.
     */
    public Integer ramMB = ResourcesConstants.DEFAULT_MEMORY_MB_RES_CONSTRAINT;

    /**
     * Timeout in seconds for code snippet execution.
     */
    public Integer timeoutSeconds = ResourcesConstants.DEFAULT_EXEC_TIMEOUT_SECONDS;

}
