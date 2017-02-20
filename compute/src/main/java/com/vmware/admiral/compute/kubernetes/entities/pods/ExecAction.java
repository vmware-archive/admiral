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
 * ExecAction describes a "run in container" action.
 */
public class ExecAction {

    /**
     * Command is the command line to execute inside the container, the working directory for the
     * command is root (/) in the container’s filesystem. The command is simply exec’d,
     * it is not run inside a shell, so traditional shell instructions
     */
    public String[] command;
}
