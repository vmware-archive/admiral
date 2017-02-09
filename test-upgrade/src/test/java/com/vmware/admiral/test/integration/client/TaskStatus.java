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

package com.vmware.admiral.test.integration.client;

import java.util.List;

import javax.xml.bind.annotation.XmlTransient;

/**
 * The status information for a task.
 */
@XmlTransient
public class TaskStatus extends TenantedServiceDocument {

    /** The name of the TaskService */
    public String phase;

    /** TaskInfo state of the current TaskService */
    public TaskState taskInfo;

    /** Substage of the current TaskService */
    public String subStage;

    /** Progress of the task (0-100%) - should only reported by leaf tasks, otherwise null */
    public Integer progress;

    /** Name of a given task status */
    public String name;

    /** Available when task is marked failed. Link to the corresponding event log. */
    public String eventLogLink;

    /** List of resource links provisioned or performed operation on them. */
    public List<String> resourceLinks;
}
