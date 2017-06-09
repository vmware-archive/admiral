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

import java.net.URI;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * ExtensibilitySubscriptionService is service for registering callbacks for stage and substage for
 * given task.
 *
 * <p/>
 * It is used for persistence, check {@link ExtensibilitySubscriptionManager}
 */
public class ExtensibilitySubscriptionService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.EXTENSIBILITY_SUBSCRIPTION;

    public static class ExtensibilitySubscription extends MultiTenantDocument {

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
        public Boolean blocking;

        @Documentation(description = "Callback address, when the specified task reaches the stage "
                + "and substage, a POST request with the task state will be sent to the subscriber")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public URI callbackReference;

    }

    public ExtensibilitySubscriptionService() {
        super(ExtensibilitySubscription.class);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleCreate(Operation post) {
        validateState(post);
        post.complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

    static String constructKey(ExtensibilitySubscription state) {
        return String.format("%s:%s:%s", state.task, state.stage, state.substage);
    }

    private void validateState(Operation o) {
        ExtensibilitySubscription state = getBody(o);

        Utils.validateState(getStateDescription(), state);
    }
}