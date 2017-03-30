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

package com.vmware.admiral.request.graph;

import java.util.Map;
import java.util.Set;

import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerGraphService.TaskServiceStageWithLink;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.xenon.common.Utils;

public class NetworkRequestVisitor implements ComponentRequestVisitor {
    private Map<String, TaskServiceStageWithLink> allStages;

    public static class NetworkRequestInfo {
        public String type = ResourceType.NETWORK_TYPE.getContentType();
        public Set<String> resourceLinks;
        public String resourceDescriptionLink;
    }

    private NetworkRequestInfo info;

    public NetworkRequestInfo visit(TaskServiceStageWithLink lastStage,
            Map<String, TaskServiceStageWithLink> allStages) {
        this.allStages = allStages;
        this.info = new NetworkRequestInfo();
        visitStage(lastStage);
        return this.info;
    }

    public boolean accepts(TaskServiceStageWithLink stage) {
        if (stage.documentSelfLink.startsWith(RequestBrokerFactoryService.SELF_LINK)
                && stage.taskSubStage.equals(RequestBrokerState.SubStage.COMPLETED.name())) {
            RequestBrokerState state = Utils.fromJson(stage.properties,
                    RequestBrokerState.class);
            return ResourceType.NETWORK_TYPE.getName().equals(state.resourceType);
        }

        return false;
    }

    private void visitStage(TaskServiceStageWithLink stage) {
        if (stage == null ||
                (stage.documentSelfLink.startsWith(RequestBrokerFactoryService.SELF_LINK)
                        && stage.taskSubStage.equals(RequestBrokerState.SubStage.CREATED.name()))) {
            return;
        }

        if (stage.documentSelfLink.startsWith(RequestBrokerFactoryService.SELF_LINK)) {
            visitRequestTaskStage(stage);
        }

        if (stage.transitionSource != null) {
            stage = allStages.get(ComponentRequestVisitor.getStageId(stage.transitionSource));
        } else {
            stage = null;
        }

        visitStage(stage);
    }

    private void visitRequestTaskStage(TaskServiceStageWithLink stage) {
        if (stage.taskSubStage.equals(RequestBrokerState.SubStage.COMPLETED.name())) {
            RequestBrokerState state = Utils.fromJson(stage.properties, RequestBrokerState.class);
            info.resourceLinks = state.resourceLinks;
            info.resourceDescriptionLink = state.resourceDescriptionLink;
        }
    }
}