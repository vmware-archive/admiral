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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.request.compute.LoadBalancerProvisionTaskService.LoadBalancerProvisionTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.tasks.ProvisionLoadBalancerTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;

/**
 * Task implementing the provision of a load balancer.
 */
public class LoadBalancerProvisionTaskService extends
        AbstractTaskStatefulService<
                LoadBalancerProvisionTaskService.LoadBalancerProvisionTaskState,
                LoadBalancerProvisionTaskService.LoadBalancerProvisionTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_LOAD_BALANCER_PROVISION_TASKS;

    public static final String DISPLAY_NAME = "Load Balancer Provision";

    public static class LoadBalancerProvisionTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<LoadBalancerProvisionTaskState.SubStage> {
        public enum SubStage {
            CREATED,
            PROVISIONING,
            COMPLETED,
            ERROR
        }

        static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                Arrays.asList(SubStage.PROVISIONING));

        /**
         * (Required) Links to already allocated resources that are going to be provisioned.
         */
        @Documentation(
                description = "Links to already allocated resources that are going to be provisioned.")
        @PropertyOptions(usage = { REQUIRED, SINGLE_ASSIGNMENT })
        public Set<String> resourceLinks;
    }

    public LoadBalancerProvisionTaskService() {
        super(LoadBalancerProvisionTaskState.class, LoadBalancerProvisionTaskState.SubStage.class,
                DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void validateStateOnStart(LoadBalancerProvisionTaskState state)
            throws IllegalArgumentException {
        if (state.resourceLinks.size() != 1) {
            throw new IllegalArgumentException(
                    "No more than one load balancer can be provisioned by this task");
        }
    }

    @Override
    protected void handleStartedStagePatch(LoadBalancerProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            provisionLoadBalancer(state).thenAccept(ignore -> {
                proceedTo(SubStage.PROVISIONING);
            });
            break;
        case PROVISIONING:
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

    private DeferredResult<Operation> provisionLoadBalancer(LoadBalancerProvisionTaskState state) {
        ServiceTaskCallback<LoadBalancerProvisionTaskState.SubStage> callback =
                ServiceTaskCallback.create(getUri());
        callback.onSuccessTo(LoadBalancerProvisionTaskState.SubStage.COMPLETED);
        callback.onErrorTo(LoadBalancerProvisionTaskState.SubStage.ERROR);

        ProvisionLoadBalancerTaskState task = new ProvisionLoadBalancerTaskState();
        task.isMockRequest = DeploymentProfileConfig.getInstance().isTest();
        task.requestType = InstanceRequestType.CREATE;
        task.serviceTaskCallback = callback;
        task.tenantLinks = state.tenantLinks;
        task.documentExpirationTimeMicros = ServiceUtils.getDefaultTaskExpirationTimeInMicros();
        task.loadBalancerLink = state.resourceLinks.iterator().next();

        return this.sendWithDeferredResult(
                Operation.createPost(this, ProvisionLoadBalancerTaskService.FACTORY_LINK)
                        .setBody(task))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        failTask("Error starting load balancer provisioning task", e);
                    }
                });
    }
}
