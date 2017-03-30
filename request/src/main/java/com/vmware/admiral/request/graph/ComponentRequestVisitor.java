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

import com.vmware.admiral.request.RequestBrokerGraphService.TaskServiceStageWithLink;
import com.vmware.admiral.request.RequestBrokerGraphService.TransitionSource;

public interface ComponentRequestVisitor {

    public boolean accepts(TaskServiceStageWithLink stage);

    public Object visit(TaskServiceStageWithLink lastStage,
            Map<String, TaskServiceStageWithLink> allStages);

    public static String getStageId(TransitionSource source) {
        return source.documentSelfLink + "_" + source.subStage + "_"
                + source.documentUpdateTimeMicros;
    }

    public static String getStageId(TaskServiceStageWithLink stage) {
        return stage.documentSelfLink + "_" + stage.taskSubStage + "_"
                + stage.documentUpdateTimeMicros;
    }
}