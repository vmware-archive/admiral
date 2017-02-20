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
 * ContainerStateTerminated is a terminated state of a container.
 */
public class ContainerStateTerminated {

    public Integer exitCode;

    public Integer signal;

    public String reason;

    public String message;

    public String startedAt;

    public String finishedAt;

    public String containerID;
}
