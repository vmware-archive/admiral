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
import com.vmware.admiral.request.ContainerRemovalTaskService.ContainerRemovalTaskState;
import com.vmware.admiral.request.ContainerRemovalTaskService.ContainerRemovalTaskState.SubStage;
import com.vmware.admiral.request.utils.EventTopicUtils;
import com.vmware.admiral.service.common.EventTopicDeclarator;
import com.vmware.admiral.service.common.EventTopicService;
import com.vmware.photon.controller.model.data.SchemaBuilder;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState.TaskStage;

/**
 * Factory service implementing {@link FactoryService} used to create instances of
 * {@link ContainerRemovalTaskState}.
 */
public class ContainerRemovalTaskFactoryService extends FactoryService implements
        EventTopicDeclarator {
    public static final String SELF_LINK = ManagementUriParts.REQUEST_REMOVAL_OPERATIONS;

    public ContainerRemovalTaskFactoryService() {
        super(ContainerRemovalTaskState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new ContainerRemovalTaskService();
    }

    public static final String CONTAINER_REMOVAL_TOPIC_TASK_SELF_LINK =
            "container-removal";
    public static final String CONTAINER_REMOVAL_TOPIC_ID = "com.vmware.container.removal.pre";
    public static final String CONTAINER_REMOVAL_TOPIC_NAME = "Container removal";
    public static final String CONTAINER_REMOVAL_TOPIC_TASK_DESCRIPTION = "Fired before a container is being destroyed";

    private void containerRemovalEventTopic(ServiceHost host) {
        EventTopicService.TopicTaskInfo taskInfo = new EventTopicService.TopicTaskInfo();
        taskInfo.task = ContainerRemovalTaskState.class.getSimpleName();
        taskInfo.stage = TaskStage.STARTED.name();
        taskInfo.substage = SubStage.REMOVING_RESOURCE_STATES.name();

        EventTopicUtils.registerEventTopic(CONTAINER_REMOVAL_TOPIC_ID,
                CONTAINER_REMOVAL_TOPIC_NAME, CONTAINER_REMOVAL_TOPIC_TASK_DESCRIPTION,
                CONTAINER_REMOVAL_TOPIC_TASK_SELF_LINK, Boolean.TRUE,
                containerRemovalTopicSchema(), taskInfo, host);
    }

    private SchemaBuilder containerRemovalTopicSchema() {
        return new SchemaBuilder();//no special fields needed
    }

    @Override
    public void registerEventTopics(ServiceHost host) {
        containerRemovalEventTopic(host);
    }
}
