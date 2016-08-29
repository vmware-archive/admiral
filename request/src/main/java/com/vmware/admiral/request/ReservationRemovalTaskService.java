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

import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.ResourcePolicyReservationRequest;
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
 * Task implementing the removal/free up of the previously reserved resource policies.
 */
public class ReservationRemovalTaskService
        extends
        AbstractTaskStatefulService<ReservationRemovalTaskService.ReservationRemovalTaskState, DefaultSubStage> {

    public static final String DISPLAY_NAME = "Reservation Removal";

    public static class ReservationRemovalTaskState extends TaskServiceDocument<DefaultSubStage> {
        private static final String FIELD_NAME_RESOURCE_DESC_LINK = "resourceDescriptionLink";
        private static final String FIELD_NAME_RESOURCE_COUNT = "resourceCount";
        private static final String FIELD_NAME_GROUP_RESOURCE_POLICY_LINK = "groupResourcePolicyLink";

        /** (Required) The description that defines the requested resource. */
        public String resourceDescriptionLink;

        /** (Required) Number of resources to provision. */
        public long resourceCount;

        /** (Required) The {@link GroupResourcePolicyState} to release the policies. */
        public String groupResourcePolicyLink;
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
        getGroupResourcePolicies(state);
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            ReservationRemovalTaskState patchBody, ReservationRemovalTaskState currentState) {
        currentState.groupResourcePolicyLink = mergeProperty(currentState.groupResourcePolicyLink,
                patchBody.groupResourcePolicyLink);

        return false;
    }

    @Override
    protected void validateStateOnStart(ReservationRemovalTaskState state) {
        assertNotEmpty(state.resourceDescriptionLink, "resourceDescriptionLink");
        assertNotEmpty(state.groupResourcePolicyLink, "groupResourcePolicyLink");

        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
        }
    }

    private void getGroupResourcePolicies(ReservationRemovalTaskState state) {
        logInfo("Retrieving group resource policy: %s", state.groupResourcePolicyLink);
        sendRequest(Operation.createGet(this, state.groupResourcePolicyLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retriving group policy", e);
                        return;
                    }
                    GroupResourcePolicyState groupPolicyState = o
                            .getBody(GroupResourcePolicyState.class);
                    releaseResourcePolicy(state, groupPolicyState);
                }));

    }

    private void releaseResourcePolicy(ReservationRemovalTaskState state,
            GroupResourcePolicyState groupPolicyState) {

        ResourcePolicyReservationRequest reservationRequest = new ResourcePolicyReservationRequest();
        reservationRequest.resourceCount = -state.resourceCount;
        reservationRequest.resourceDescriptionLink = state.resourceDescriptionLink;

        logInfo("Releasing policy instances: %d for descLink: %s and groupPolicyId: %s",
                reservationRequest.resourceCount, reservationRequest.resourceDescriptionLink,
                Service.getId(groupPolicyState.documentSelfLink));

        sendRequest(Operation.createPatch(this, groupPolicyState.documentSelfLink)
                .setBody(reservationRequest)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure releasing group policy", e);
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
