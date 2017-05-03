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

package com.vmware.admiral.request;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState.SubStage;
import com.vmware.admiral.request.utils.EventTopicConstants;
import com.vmware.admiral.request.utils.EventTopicUtils;
import com.vmware.admiral.request.utils.EventTopicUtils.SchemaBuilder;
import com.vmware.admiral.service.common.EventTopicDeclarator;
import com.vmware.admiral.service.common.EventTopicService;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState.TaskStage;

/**
 * Factory service implementing {@link FactoryService} used to create instances of
 * {@link ContainerAllocationTaskService}.
 */
public class ContainerAllocationTaskFactoryService extends FactoryService implements EventTopicDeclarator {
    public static final String SELF_LINK = ManagementUriParts.REQUEST_ALLOCATION_TASKS;

    public ContainerAllocationTaskFactoryService() {
        super(ContainerAllocationTaskState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new ContainerAllocationTaskService();
    }

    private SchemaBuilder changeContainerNameTopicSchema() {
        return SchemaBuilder.create().addField(EventTopicConstants.CONTAINER_NAME_TOPIC_FIELD_RESOURCE_NAMES)
                .addDataType(String.class.getSimpleName())
                .addLabel(EventTopicConstants.CONTAINER_NAME_TOPIC_FIELD_RESOURCE_NAMES_LABEL)
                .addDescription(EventTopicConstants.CONTAINER_NAME_TOPIC_FIELD_RESOURCE_NAMES_DESCRIPTION)
                .whereMultiValued(true)
                .whereReadOnly(false)
                // Add resourceToHostSelection info
                .addField(EventTopicConstants.CONTAINER_NAME_TOPIC_FIELD_RESOURCE_TO_HOST_SELECTIONS)
                .addDataType(String.class.getSimpleName())
                .addLabel(EventTopicConstants.CONTAINER_NAME_TOPIC_FIELD_RESOURCE_TO_HOST_LABEL)
                .addDescription(EventTopicConstants.CONTAINER_NAME_TOPIC_FIELD_RESOURCE_TO_HOST_DESCRIPTION)
                .whereMultiValued(false)
                .whereReadOnly(true);
    }

    private void changeContainerNameEventTopic(ServiceHost host) {
        EventTopicService.TopicTaskInfo taskInfo = new EventTopicService.TopicTaskInfo();
        taskInfo.task = ContainerAllocationTaskState.class.getSimpleName();
        taskInfo.stage = TaskStage.STARTED.name();
        taskInfo.substage = SubStage.BUILD_RESOURCES_LINKS.name();

        EventTopicUtils.registerEventTopic(EventTopicConstants
                        .CONTAINER_NAME_TOPIC_ID,
                EventTopicConstants.CONTAINER_NAME_TOPIC_NAME, EventTopicConstants
                        .CONTAINER_NAME_TOPIC_TASK_DESCRIPTION, EventTopicConstants
                        .CONTAINER_NAME_TOPIC_TASK_SELF_LINK, Boolean.TRUE,
                changeContainerNameTopicSchema(), taskInfo, host);
    }

    @Override
    public void registerEventTopics(ServiceHost host) {

        changeContainerNameEventTopic(host);
    }
}
