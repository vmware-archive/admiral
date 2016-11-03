/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;

import java.util.EnumSet;

import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.ResourcePlacementReservationRequest;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState.TaskStage;

/**
 * Task implementing the removal/free up of the previously reserved resource placements.
 */
public class ReservationRemovalTaskService
        extends
        AbstractTaskStatefulService<ReservationRemovalTaskService.ReservationRemovalTaskState, DefaultSubStage> {

    public static final String DISPLAY_NAME = "Reservation Removal";

    public static class ReservationRemovalTaskState extends TaskServiceDocument<DefaultSubStage> {
        private static final String FIELD_NAME_RESOURCE_DESC_LINK = "resourceDescriptionLink";
        private static final String FIELD_NAME_RESOURCE_COUNT = "resourceCount";
        private static final String FIELD_NAME_GROUP_RESOURCE_POLICY_LINK = "groupResourcePlacementLink";

        /** (Required) The description that defines the requested resource. */
        public String resourceDescriptionLink;

        /** (Required) Number of resources to provision. */
        public long resourceCount;

        /** (Required) The {@link GroupResourcePlacementState} to release the placements. */
        public String groupResourcePlacementLink;
    }

    public ReservationRemovalTaskService() {
        super(ReservationRemovalTaskState.class, DefaultSubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = DefaultSubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ReservationRemovalTaskState state) {
        getGroupResourcePlacements(state);
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            ReservationRemovalTaskState patchBody, ReservationRemovalTaskState currentState) {
        currentState.groupResourcePlacementLink = mergeProperty(currentState.groupResourcePlacementLink,
                patchBody.groupResourcePlacementLink);

        return false;
    }

    @Override
    protected void validateStateOnStart(ReservationRemovalTaskState state) {
        assertNotEmpty(state.resourceDescriptionLink, "resourceDescriptionLink");
        assertNotEmpty(state.groupResourcePlacementLink, "groupResourcePlacementLink");

        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
        }
    }

    private void getGroupResourcePlacements(ReservationRemovalTaskState state) {
        logInfo("Retrieving group resource placement: %s", state.groupResourcePlacementLink);
        sendRequest(Operation.createGet(this, state.groupResourcePlacementLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retriving group placement", e);
                        return;
                    }
                    GroupResourcePlacementState groupPlacementState = o
                            .getBody(GroupResourcePlacementState.class);
                    releaseResourcePlacement(state, groupPlacementState);
                }));

    }

    private void releaseResourcePlacement(ReservationRemovalTaskState state,
            GroupResourcePlacementState groupPlacementState) {

        ResourcePlacementReservationRequest reservationRequest = new ResourcePlacementReservationRequest();
        reservationRequest.resourceCount = -state.resourceCount;
        reservationRequest.resourceDescriptionLink = state.resourceDescriptionLink;
        reservationRequest.referer = getSelfLink();

        logInfo("Releasing placement instances: %d for descLink: %s and groupPlacementId: %s",
                reservationRequest.resourceCount, reservationRequest.resourceDescriptionLink,
                Service.getId(groupPlacementState.documentSelfLink));

        sendRequest(Operation.createPatch(this, groupPlacementState.documentSelfLink)
                .setBody(reservationRequest)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure releasing group placement", e);
                        return;
                    }
                    state.taskInfo.stage = TaskStage.FINISHED;
                    state.taskSubStage = DefaultSubStage.COMPLETED;
                    sendSelfPatch(state);
                }));
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();

        setDocumentTemplateIndexingOptions(template, EnumSet.of(PropertyIndexingOption.STORE_ONLY),
                ReservationRemovalTaskState.FIELD_NAME_RESOURCE_DESC_LINK,
                ReservationRemovalTaskState.FIELD_NAME_RESOURCE_COUNT,
                ReservationRemovalTaskState.FIELD_NAME_GROUP_RESOURCE_POLICY_LINK);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.SINGLE_ASSIGNMENT),
                ReservationRemovalTaskState.FIELD_NAME_RESOURCE_DESC_LINK,
                ReservationRemovalTaskState.FIELD_NAME_RESOURCE_COUNT,
                ReservationRemovalTaskState.FIELD_NAME_GROUP_RESOURCE_POLICY_LINK);

        return template;
    }
}
