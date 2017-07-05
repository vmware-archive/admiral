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

package com.vmware.admiral.host;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.host.DummyService.DummyServiceTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;

/**
 * Service that is meant to be used for test purposes related to subscriptions. Currently it
 * provides DefaultSubStage, but will be extended in next changes.
 *
 */
public class DummyService
        extends
        AbstractTaskStatefulService<DummyService.DummyServiceTaskState, DummyService.DummyServiceTaskState.SubStage> {

    public static final String SELF_LINK = "dummy-service";

    private static final String DISPLAY_NAME = "test-dummy-service";

    public static class DummyServiceTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<DummyServiceTaskState.SubStage> {

        public static enum SubStage {
            CREATED, FILTER, COMPLETED, ERROR;

            static final Set<SubStage> SUBSCRIPTION_SUB_STAGES = new HashSet<>(
                    Arrays.asList(FILTER, COMPLETED));

        }

        /**
         * Container that will be manipulated by subscriber for DummyService.
         */
        public ContainerState containerState;

        /** (Internal) Set by task with DummyServiceTaskState name. */
        @Documentation(description = "Set by task with DummyServiceTaskState name.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String name;

        /** (Internal) If flag is provided blocking subscription will handle it. */
        @Documentation(description = "Set by task with DummyServiceTaskState name.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Boolean blocking;

    }

    public DummyService() {
        super(DummyServiceTaskState.class, SubStage.class, DISPLAY_NAME);
        super.subscriptionSubStages = EnumSet.copyOf(SubStage.SUBSCRIPTION_SUB_STAGES);
    }

    @Override
    protected void handleStartedStagePatch(DummyServiceTaskState state) {

        switch (state.taskSubStage) {
        case CREATED:
            proceedTo(SubStage.FILTER, s -> {
                s.name = SELF_LINK;
            });
            break;
        case FILTER:
            proceedTo(SubStage.COMPLETED);
            break;
        case COMPLETED:
            complete();
            break;
        case ERROR:
            completeWithError();
            break;
        default:
            break;
        }
    }

    @Override
    protected BaseExtensibilityCallbackResponse notificationPayload(DummyServiceTaskState state) {
        return new CallbackCompleteResponse();
    }

    @Override
    protected ServiceTaskCallbackResponse replyPayload(DummyServiceTaskState state) {
        return new CallbackCompleteResponse();
    }

    protected static class CallbackCompleteResponse extends BaseExtensibilityCallbackResponse {
        String name;
        Boolean blocking;
        ContainerState containerState;
    }

}