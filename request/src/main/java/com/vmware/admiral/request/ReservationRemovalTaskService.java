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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.ResourcePlacementReservationRequest;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

/**
 * Task implementing the removal/free up of the previously reserved resource placements.
 */
public class ReservationRemovalTaskService
        extends
        AbstractTaskStatefulService<ReservationRemovalTaskService.ReservationRemovalTaskState, DefaultSubStage> {

    public static final String DISPLAY_NAME = "Reservation Removal";

    public static class ReservationRemovalTaskState extends TaskServiceDocument<DefaultSubStage> {
        /** (Required) The description that defines the requested resource. */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        /** (Required) Number of resources to provision. */
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public long resourceCount;

        /** (Required) The {@link GroupResourcePlacementState} to release the placements. */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED }, indexing = STORE_ONLY)
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
    protected void validateStateOnStart(ReservationRemovalTaskState state) {
        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.", "request.resource-count.zero");
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
                    complete();
                }));
    }
}
