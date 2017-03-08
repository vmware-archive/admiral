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

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlTransient;

/**
 * The base class for all ServiceDocument for Task type services.
 */
@XmlTransient
public abstract class TaskServiceDocument extends TenantedServiceDocument {

    /** The substage of the request */
    public String taskSubStage;

    /** (Optional) The main task state of a given request life cycle. */
    public TaskState taskInfo;

    /** Callback link and response from the service initiated this task. */
    public ServiceTaskCallback serviceTaskCallback;

    /** (Optional) link to a service that will receive updates when the task changes state. */
    public String requestTrackerLink;

    /** (Optional) Custom properties */
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
