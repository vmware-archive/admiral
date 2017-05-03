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

package com.vmware.admiral.request.utils;

/**
 * Interface that declares constants related to link{@EventTopicState} class.
 */
public interface EventTopicConstants {

    public static final String CONTAINER_NAME_TOPIC_TASK_SELF_LINK = "change-container-name";
    public static final String CONTAINER_NAME_TOPIC_ID = "com.vmware.container.name.assignment";
    public static final String CONTAINER_NAME_TOPIC_NAME = "Container name assignment";
    public static final String CONTAINER_NAME_TOPIC_TASK_DESCRIPTION = "Assign custom container name.";
    public static final String CONTAINER_NAME_TOPIC_FIELD_RESOURCE_TO_HOST_SELECTIONS =
            "resourceToHostSelection";

    public static final String CONTAINER_NAME_TOPIC_FIELD_RESOURCE_NAMES = "resourceNames";
    public static final String CONTAINER_NAME_TOPIC_FIELD_RESOURCE_NAMES_DESCRIPTION =
            "\"Generated resource names.\"";
    public static final String CONTAINER_NAME_TOPIC_FIELD_RESOURCE_NAMES_LABEL = "\"Generated "
            + "resource names.\"";
    public static final String CONTAINER_NAME_TOPIC_FIELD_RESOURCE_TO_HOST_LABEL = "Resource"
            + " to host selection (Read Only)";

    public static final String CONTAINER_NAME_TOPIC_FIELD_RESOURCE_TO_HOST_DESCRIPTION = "Eeach "
            + "string entry represents resource and host on which it will be deployed.";

    //Compute name assignment topic
    public static final String COMPUTE_NAME_TOPIC_TASK_SELF_LINK = "change-compute-name";
    public static final String COMPUTE_NAME_TOPIC_ID = "com.vmware.compute.name.assignment";
    public static final String COMPUTE_NAME_TOPIC_NAME = "Compute name assignment";
    public static final String COMPUTE_NAME_TOPIC_TASK_DESCRIPTION = "Assign custom compute name.";
    public static final String COMPUTE_NAME_TOPIC_FIELD_RESOURCE_NAMES = "resourceNames";
    public static final String COMPUTE_NAME_TOPIC_FIELD_RESOURCE_NAMES_LABEL = "\"Generated "
            + "resource names.\"";
    public static final String COMPUTE_NAME_TOPIC_FIELD_RESOURCE_NAMES_DESCRIPTION =
            "\"Generated resource names.\"";

}
