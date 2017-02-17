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

package com.vmware.admiral.compute.kubernetes.entities;

/**
 * EnvVar represents an environment variable present in a Container.
 */
public class EnvVar {

    /**
     * Name of the environment variable.
     */
    public String name;

    /**
     * Variable references $(VAR_NAME) are expanded using the previous defined environment
     * variables in the container and any service environment variables. If a variable cannot be
     * resolved, the reference in the input string will be unchanged.
     */
    public String value;

    /**
     * Source for the environment variable’s value. Cannot be used if value is not empty.
     */
    public EnvVarSource valueFrom;

}
