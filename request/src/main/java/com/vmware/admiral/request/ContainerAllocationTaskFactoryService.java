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
import com.vmware.admiral.service.common.AbstractSecuredFactoryService;
import com.vmware.admiral.service.common.EventTopicDeclarator;
import com.vmware.admiral.service.common.EventTopicService;
import com.vmware.photon.controller.model.data.SchemaBuilder;
import com.vmware.photon.controller.model.data.SchemaField.Type;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState.TaskStage;

/**
 * Factory service implementing {@link AbstractSecuredFactoryService} used to create instances of
 * {@link ContainerAllocationTaskService}.
 */
public class ContainerAllocationTaskFactoryService extends AbstractSecuredFactoryService
        implements EventTopicDeclarator {
    public static final String SELF_LINK = ManagementUriParts.REQUEST_ALLOCATION_TASKS;

    //EventTopic constants
    private static final String CONTAINER_ALLOCATION_TOPIC_TASK_SELF_LINK = "container-allocation";
    private static final String CONTAINER_ALLOCATION_TOPIC_ID = "com.vmware.container.allocation.pre";
    private static final String CONTAINER_ALLOCATION_TOPIC_NAME = "Container allocation";
    private static final String CONTAINER_ALLOCATION_TOPIC_TASK_DESCRIPTION = "Pre allocation for containers";

    private static final String CONTAINER_PRE_PROVISION_TOPIC_TASK_SELF_LINK = "container-provision";
    private static final String CONTAINER_PRE_PROVISION_TOPIC_ID = "com.vmware.container.provision.pre";
    private static final String CONTAINER_PRE_PROVISION_TOPIC_NAME = "Container pre provision";
    private static final String CONTAINER_PRE_PROVISION_TOPIC_TASK_DESCRIPTION = "Pre provision for containers";

    private static final String CONTAINER_POST_PROVISION_TOPIC_TASK_SELF_LINK = "container-provision-post";
    private static final String CONTAINER_POST_PROVISION_TOPIC_ID = "com.vmware.container.provision.post";
    private static final String CONTAINER_POST_PROVISION_TOPIC_NAME = "Container post provisioning";
    private static final String CONTAINER_POST_PROVISION_TOPIC_TASK_DESCRIPTION = "Post provision for containers";

    private static final String CONTAINER_ALLOCATION_TOPIC_FIELD_RESOURCE_NAMES = "resourceNames";
    private static final String CONTAINER_ALLOCATION_TOPIC_FIELD_RESOURCE_NAMES_DESCRIPTION = "Generated resource names";
    private static final String CONTAINER_ALLOCATION_TOPIC_FIELD_RESOURCE_NAMES_LABEL = "Generated resource names";

    private static final String CONTAINER_ALLOCATION_TOPIC_FIELD_HOST_SELECTIONS = "hosts";
    private static final String CONTAINER_ALLOCATION_TOPIC_FIELD_HOST_SELECTIONS_LABEL =
            "Selected hosts";
    private static final String CONTAINER_ALLOCATION_TOPIC_FIELD_HOST_SELECTIONS_DESCRIPTION =
            "Host selections for given resource";

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

    private SchemaBuilder containerAllocationTopicSchema() {

        return new SchemaBuilder()
                // Add resource names info
                .addField(CONTAINER_ALLOCATION_TOPIC_FIELD_RESOURCE_NAMES)
                .withType(Type.LIST)
                .withDataType(DATATYPE_STRING)
                .withLabel(CONTAINER_ALLOCATION_TOPIC_FIELD_RESOURCE_NAMES_LABEL)
                .withDescription(CONTAINER_ALLOCATION_TOPIC_FIELD_RESOURCE_NAMES_DESCRIPTION)
                .done()

                // Add hosts info
                .addField(CONTAINER_ALLOCATION_TOPIC_FIELD_HOST_SELECTIONS)
                .withType(Type.LIST)
                .withDataType(DATATYPE_STRING)
                .withLabel(CONTAINER_ALLOCATION_TOPIC_FIELD_HOST_SELECTIONS_LABEL)
                .withDescription(CONTAINER_ALLOCATION_TOPIC_FIELD_HOST_SELECTIONS_DESCRIPTION)
                .done();

    }

    private void containerAllocationEventTopic(ServiceHost host) {
        EventTopicService.TopicTaskInfo taskInfo = new EventTopicService.TopicTaskInfo();
        taskInfo.task = ContainerAllocationTaskState.class.getSimpleName();
        taskInfo.stage = TaskStage.STARTED.name();
        taskInfo.substage = SubStage.BUILD_RESOURCES_LINKS.name();

        EventTopicUtils.registerEventTopic(CONTAINER_ALLOCATION_TOPIC_ID,
                CONTAINER_ALLOCATION_TOPIC_NAME,
                CONTAINER_ALLOCATION_TOPIC_TASK_DESCRIPTION,
                CONTAINER_ALLOCATION_TOPIC_TASK_SELF_LINK, Boolean.TRUE,
                containerAllocationTopicSchema(), taskInfo, host);
    }

    private void containerPreProvisionEventTopic(ServiceHost host) {
        EventTopicService.TopicTaskInfo taskInfo = new EventTopicService.TopicTaskInfo();
        taskInfo.task = ContainerAllocationTaskState.class.getSimpleName();
        taskInfo.stage = TaskStage.STARTED.name();
        taskInfo.substage = SubStage.START_PROVISIONING.name();

        EventTopicUtils.registerEventTopic(CONTAINER_PRE_PROVISION_TOPIC_ID,
                CONTAINER_PRE_PROVISION_TOPIC_NAME,
                CONTAINER_PRE_PROVISION_TOPIC_TASK_DESCRIPTION,
                CONTAINER_PRE_PROVISION_TOPIC_TASK_SELF_LINK, Boolean.TRUE,
                new SchemaBuilder(), taskInfo, host);
    }

    private void containerPostProvisionEventTopic(ServiceHost host) {
        EventTopicService.TopicTaskInfo taskInfo = new EventTopicService.TopicTaskInfo();
        taskInfo.task = ContainerAllocationTaskState.class.getSimpleName();
        taskInfo.stage = TaskStage.FINISHED.name();
        taskInfo.substage = SubStage.COMPLETED.name();

        EventTopicUtils.registerEventTopic(CONTAINER_POST_PROVISION_TOPIC_ID,
                CONTAINER_POST_PROVISION_TOPIC_NAME,
                CONTAINER_POST_PROVISION_TOPIC_TASK_DESCRIPTION,
                CONTAINER_POST_PROVISION_TOPIC_TASK_SELF_LINK, Boolean.TRUE,
                new SchemaBuilder(), taskInfo, host);
    }

    @Override
    public void registerEventTopics(ServiceHost host) {
        containerAllocationEventTopic(host);
        containerPreProvisionEventTopic(host);
        containerPostProvisionEventTopic(host);
    }
}
