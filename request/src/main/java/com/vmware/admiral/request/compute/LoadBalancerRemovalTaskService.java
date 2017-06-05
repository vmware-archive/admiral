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

package com.vmware.admiral.request.compute;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.DefaultSubStage;

/**
 * Task implementing the removal of a load balancer.
 */
public class LoadBalancerRemovalTaskService extends
        AbstractTaskStatefulService<LoadBalancerRemovalTaskService.LoadBalancerRemovalTaskState, DefaultSubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_LOAD_BALANCER_REMOVAL_TASKS;

    public static final String DISPLAY_NAME = "Load Balancer Removal";

    public static class LoadBalancerRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<DefaultSubStage> {
        // TODO
    }

    public LoadBalancerRemovalTaskService() {
        super(LoadBalancerRemovalTaskState.class, DefaultSubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(LoadBalancerRemovalTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            proceedTo(DefaultSubStage.COMPLETED);
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
}
