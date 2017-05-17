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

import static com.vmware.photon.controller.model.data.SchemaField.DATATYPE_STRING;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState.SubStage;
import com.vmware.admiral.request.utils.EventTopicUtils;
import com.vmware.admiral.service.common.EventTopicDeclarator;
import com.vmware.admiral.service.common.EventTopicService;
import com.vmware.photon.controller.model.data.SchemaBuilder;
import com.vmware.photon.controller.model.data.SchemaField.Constraint;
import com.vmware.photon.controller.model.data.SchemaField.Type;
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

    //EventTopic constants
    private static final String CONTAINER_NAME_TOPIC_TASK_SELF_LINK = "change-container-name";
    private static final String CONTAINER_NAME_TOPIC_ID = "com.vmware.container.name.assignment";
    private static final String CONTAINER_NAME_TOPIC_NAME = "Container name assignment";
    private static final String CONTAINER_NAME_TOPIC_TASK_DESCRIPTION = "Assign custom container "
            + "name.";
    private static final String CONTAINER_NAME_TOPIC_FIELD_RESOURCE_TO_HOST_SELECTIONS =
            "resourceToHostSelection";
    private static final String CONTAINER_NAME_TOPIC_FIELD_RESOURCE_NAMES = "resourceNames";
    private static final String CONTAINER_NAME_TOPIC_FIELD_RESOURCE_NAMES_DESCRIPTION = "Generated resource names";
    private static final String CONTAINER_NAME_TOPIC_FIELD_RESOURCE_NAMES_LABEL = "Generated resource names";
    private static final String CONTAINER_NAME_TOPIC_FIELD_RESOURCE_TO_HOST_LABEL = "Resource to host selection(Read Only)";
    private static final String CONTAINER_NAME_TOPIC_FIELD_RESOURCE_TO_HOST_DESCRIPTION = "Eeach string entry represents resource and host on which it will be deployed";

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

        return new SchemaBuilder()
                .addField(CONTAINER_NAME_TOPIC_FIELD_RESOURCE_NAMES)
                .withType(Type.LIST)
                .withDataType(DATATYPE_STRING)
                .withLabel(CONTAINER_NAME_TOPIC_FIELD_RESOURCE_NAMES_LABEL)
                .withDescription(CONTAINER_NAME_TOPIC_FIELD_RESOURCE_NAMES_DESCRIPTION)
                .done()

                // Add resourceToHostSelection info
                .addField(CONTAINER_NAME_TOPIC_FIELD_RESOURCE_TO_HOST_SELECTIONS)
                .withType(Type.MAP)
                .withLabel(CONTAINER_NAME_TOPIC_FIELD_RESOURCE_TO_HOST_LABEL)
                .withDescription(CONTAINER_NAME_TOPIC_FIELD_RESOURCE_TO_HOST_DESCRIPTION)
                .withConstraint(Constraint.readOnly, true)
                .withDataType(DATATYPE_STRING)
                .done();

    }

    private void changeContainerNameEventTopic(ServiceHost host) {
        EventTopicService.TopicTaskInfo taskInfo = new EventTopicService.TopicTaskInfo();
        taskInfo.task = ContainerAllocationTaskState.class.getSimpleName();
        taskInfo.stage = TaskStage.STARTED.name();
        taskInfo.substage = SubStage.BUILD_RESOURCES_LINKS.name();

        EventTopicUtils.registerEventTopic(CONTAINER_NAME_TOPIC_ID, CONTAINER_NAME_TOPIC_NAME,
                CONTAINER_NAME_TOPIC_TASK_DESCRIPTION,
                CONTAINER_NAME_TOPIC_TASK_SELF_LINK, Boolean.TRUE,
                changeContainerNameTopicSchema(), taskInfo, host);
    }

    @Override
    public void registerEventTopics(ServiceHost host) {

        changeContainerNameEventTopic(host);
    }
}
