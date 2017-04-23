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

import java.util.HashMap;
import java.util.Map;

import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState;

/**
 * The base class for all ServiceDocument for Task type services.
 */
public abstract class TaskServiceDocument<E extends Enum<E>> extends MultiTenantDocument {

    public static final String FIELD_NAME_TASK_SUB_STAGE = "taskSubStage";
    public static final String FIELD_NAME_TASK_INFO = "taskInfo";
    public static final String FIELD_NAME_TASK_STAGE = "taskInfo.stage";
    public static final String FIELD_NAME_SERVICE_CALLBACK = "serviceTaskCallback";

    /** Describes a service task sub stage.*/
    @Documentation(description = " Describes a service task sub stage.")
    public E taskSubStage;

    /** Describes a service task */
    @Documentation(description = " Describes a service task state.")
    public TaskState taskInfo;

    /** Callback link and response from the service initiated this task. */
    @Documentation(description = "Callback link and response from the service initiated this task.")
    @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
            PropertyUsageOption.SINGLE_ASSIGNMENT }, indexing = PropertyIndexingOption.STORE_ONLY)
    public ServiceTaskCallback serviceTaskCallback;

    /** (Optional) link to a service that will receive updates when the task changes state */
    @Documentation(description = "link to a service that will receive updates when the task changes state.")
    @PropertyOptions(
            usage = { PropertyUsageOption.LINK, PropertyUsageOption.SINGLE_ASSIGNMENT,
                    PropertyUsageOption.OPTIONAL, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
    public String requestTrackerLink;

    /** (Optional) Custom properties */
    @Documentation(description = "Custom properties.")
    @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
            PropertyUsageOption.OPTIONAL }, indexing = PropertyIndexingOption.STORE_ONLY)
    public volatile Map<String, String> customProperties;

    public void addCustomProperty(String propName, String propValue) {
        if (customProperties == null) {
            customProperties = new HashMap<>();
        }
        customProperties.put(propName, propValue);
    }

    public String getCustomProperty(String propName) {
        if (customProperties == null) {
            return null;
        }
        return customProperties.get(propName);
    }

    public String removeCustomProperty(String propName) {
        if (customProperties == null) {
            return null;
        }
        return customProperties.remove(propName);
    }
}
