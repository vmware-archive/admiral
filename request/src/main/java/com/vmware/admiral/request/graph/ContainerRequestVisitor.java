package com.vmware.admiral.request.graph;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.request.PlacementHostSelectionTaskService;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerGraphService.TaskServiceStageWithLink;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.xenon.common.Utils;

public class ContainerRequestVisitor implements ComponentRequestVisitor {
    private Map<String, TaskServiceStageWithLink> allStages;

    public static class ContainerRequestInfo {
        public String type = ResourceType.CONTAINER_TYPE.getContentType();
        public Set<String> resourceLinks;
        public String resourceDescriptionLink;
        public String groupResourcePlacementLink;
        public Collection<HostSelection> hostSelections;
    }

    private ContainerRequestInfo info;

    public ContainerRequestInfo visit(TaskServiceStageWithLink lastStage,
            Map<String, TaskServiceStageWithLink> allStages) {
        this.allStages = allStages;
        this.info = new ContainerRequestInfo();
        visitStage(lastStage);
        return this.info;
    }

    public boolean accepts(TaskServiceStageWithLink stage) {
        if (stage.documentSelfLink.startsWith(RequestBrokerFactoryService.SELF_LINK)
                && stage.taskSubStage.equals(RequestBrokerState.SubStage.COMPLETED.name())) {
            RequestBrokerState state = Utils.fromJson(stage.properties,
                    RequestBrokerState.class);
            return ResourceType.CONTAINER_TYPE.getName().equals(state.resourceType);
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
        } else if (stage.documentSelfLink.startsWith(ReservationTaskFactoryService.SELF_LINK)) {
            visitReservationTaskStage(stage);
        } else if (stage.documentSelfLink
                .startsWith(PlacementHostSelectionTaskService.FACTORY_LINK)) {
            visitPlacementHostSelectionTaskStage(stage);
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

    private void visitReservationTaskStage(TaskServiceStageWithLink stage) {
        if (stage.taskSubStage.equals(ReservationTaskState.SubStage.COMPLETED.name())) {
            info.groupResourcePlacementLink = Utils.fromJson(stage.properties,
                    ReservationTaskState.class).groupResourcePlacementLink;
        }
    }

    private void visitPlacementHostSelectionTaskStage(TaskServiceStageWithLink stage) {
        if (stage.taskSubStage.equals(DefaultSubStage.COMPLETED.name())) {
            info.hostSelections = Utils.fromJson(stage.properties,
                    PlacementHostSelectionTaskState.class).hostSelections;
        }
    }
}