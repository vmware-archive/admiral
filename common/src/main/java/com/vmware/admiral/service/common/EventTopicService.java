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

package com.vmware.admiral.service.common;

import com.vmware.admiral.common.ManagementUriParts;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

public class EventTopicService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.EVENT_TOPIC_REGISTRY;

    public static class EventTopicState extends MultiTenantDocument {

        @Documentation(description = "Event Topic name")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String name;

        @Documentation(description = "Task name")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String task;

        @Documentation(description = "Task stage")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String stage;

        @Documentation(description = "Task substage")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String substage;

        @Documentation(description = "Blocking or asynchronous flag")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public Boolean blockable;

        @Documentation(description = "Notification payload.")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String notificationPayload;

        @Documentation(description = "Reply payload.")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String replyPayload;

        @Documentation(description = "Reply payload.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String description;

    }

    public EventTopicService() {
        super(EventTopicState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleCreate(Operation create) {
        validate(create);
        super.handleCreate(create);
    }

    @Override
    public void handlePost(Operation post) {
        validate(post);
        super.handlePost(post);
    }

    private void validate(Operation post) {
        EventTopicState body = post.getBody(EventTopicState.class);

        if (body.name == null) {
            post.fail(new Throwable("'name' is required."));
        }

        if (body.task == null || body.task.isEmpty()) {
            post.fail(new Throwable("'Task' is required."));
        }

        if (body.stage == null) {
            post.fail(new Throwable("'Stage' is required."));
        }

        if (body.substage == null) {
            post.fail(new Throwable("'SubStage' is required."));
        }

        if (body.blockable == null) {
            post.fail(new Throwable("'Blocking' is required."));
        }

        if (body.notificationPayload == null) {
            post.fail(new Throwable("'Notification payload' is required."));
        }

    }

}
