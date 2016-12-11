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