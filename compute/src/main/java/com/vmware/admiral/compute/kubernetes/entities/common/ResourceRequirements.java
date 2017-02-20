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

package com.vmware.admiral.compute.kubernetes.entities.common;

import java.util.Map;

/**
 * ResourceRequirements describes the compute resource requirements.
 */
public class ResourceRequirements {

    /**
     * Limits describes the maximum amount of compute resources allowed.
     */
    public Map<String, Object> limits;

    /**
     * Requests describes the minimum amount of compute resources required.
     * If Requests is omitted for a container, it defaults to Limits if that is explicitly
     * specified, otherwise to an implementation-defined value.
     */
    public Map<String, Object> requests;
}
