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

import java.util.Set;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;

/**
 * Task implementing the provision of a load balancer.
 */
public class LoadBalancerProvisionTaskService extends
        AbstractTaskStatefulService<LoadBalancerProvisionTaskService.LoadBalancerProvisionTaskState, DefaultSubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_LOAD_BALANCER_PROVISION_TASKS;

    public static final String DISPLAY_NAME = "Load Balancer Provision";

    public static class LoadBalancerProvisionTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<DefaultSubStage> {
        /**
         * (Required) Links to already allocated resources that are going to be provisioned.
         */
        @Documentation(
                description = "Links to already allocated resources that are going to be provisioned.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public Set<String> resourceLinks;

        // TODO
    }

    public LoadBalancerProvisionTaskService() {
        super(LoadBalancerProvisionTaskState.class, DefaultSubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(LoadBalancerProvisionTaskState state) {
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
