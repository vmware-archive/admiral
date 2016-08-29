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

package com.vmware.admiral.service.common;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Default SubStage providing common sub stages for a particular Task service.
 */
public enum DefaultSubStage {
    CREATED, PROCESSING, COMPLETED, ERROR;

    public static final Set<DefaultSubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
            Arrays.asList(PROCESSING));
}
