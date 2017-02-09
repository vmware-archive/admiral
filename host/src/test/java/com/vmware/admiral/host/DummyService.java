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

        /**
         * Container that will be manipulated by subscriber for DummyService.
         */
        public ContainerState containerState;

        public enum SubStage {
            CREATED, FILTER, COMPLETED, ERROR;

            static final Set<SubStage> SUBSCRIPTION_SUB_STAGES = new HashSet<>(
                    Arrays.asList(CREATED, COMPLETED));
        }
    }

    public DummyService() {
        super(DummyServiceTaskState.class, SubStage.class, DISPLAY_NAME);
        super.subscriptionSubStages = EnumSet.copyOf(SubStage.SUBSCRIPTION_SUB_STAGES);
    }

    @Override
    protected void handleStartedStagePatch(DummyServiceTaskState state) {
        complete();
    }
}