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

package com.vmware.admiral.service.common;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * {@code ExtensibilitySubscriptionCallbackService} is used for resuming blocked task service.
 * <p>
 * When client has received blocking notification, it need to send a post request to this service to
 * resume the blocked task service.
 *
 * @see ExtensibilitySubscriptionManager
 */
public class ExtensibilitySubscriptionCallbackService extends StatefulService {

    public static final String DISPLAY_NAME = "Extensibility Callback";

    public static final String FACTORY_LINK = ManagementUriParts.EXTENSIBILITY_CALLBACKS;

    public static final String EXTENSIBILITY_RESPONSE = "extensibilityResponse";

    private static final int PROCESSED_NOTIFICATION_EXPIRE_TIME = Integer.getInteger(
            "com.vmware.admiral.service.extensibility.expiration.processed", 60);

    public static class ExtensibilitySubscriptionCallback
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<DefaultSubStage> {

        public enum Status {
            BLOCKED, RESUME, DONE;
        }

        @Documentation(description = "Callback address")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public URI serviceCallback;

        @Documentation(description = "Task state json")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String taskStateJson;

        @Documentation(description = "Task class name")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String taskStateClassName;

        @Documentation(description = "State status")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Status status;

        @Documentation(description = "Resume counter")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public int retryCounter;

        @Documentation(description = "Defines Task fields which will be sent to client for information about the task.")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public ServiceTaskCallbackResponse notificationPayload;

        @Documentation(description = "Defines Task fields which will be merged when subscriber return response.")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public ServiceTaskCallbackResponse replayPayload;

    }

    public ExtensibilitySubscriptionCallbackService() {
        super(ExtensibilitySubscriptionCallback.class);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleCreate(Operation post) {
        if (!checkForBody(post)) {
            return;
        }

        ExtensibilitySubscriptionCallback body = getBody(post);

        body.status = ExtensibilitySubscriptionCallback.Status.BLOCKED;
        body.retryCounter = 0;

        setState(post, body);
        post.setBody(body);
        post.complete();
    }

    @Override
    public void handlePost(Operation post) {
        if (!checkForBody(post)) {
            return;
        }

        ExtensibilitySubscriptionCallback currentState = getState(post);

        if (validatePost(currentState, post)) {
            return;
        }

        syncTaskStates(currentState, post);

        setState(post, currentState);

        post.setBody(currentState).complete();

        if (currentState.status == ExtensibilitySubscriptionCallback.Status.RESUME) {
            markDone();
        }
    }

    @Override
    public void handlePut(Operation put) {
        getHost().failRequestActionNotSupported(put);
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!checkForBody(patch)) {
            return;
        }

        ExtensibilitySubscriptionCallback patchBody = getBody(patch);
        ExtensibilitySubscriptionCallback state = getState(patch);
        if (validatePatch(patchBody, state, patch)) {
            return;
        }

        if (patchBody.status != null) {
            state.status = patchBody.status;
        }

        if (patchBody.taskInfo != null) {
            state.taskInfo = patchBody.taskInfo;
        }

        if (patchBody.taskSubStage != null) {
            state.taskSubStage = patchBody.taskSubStage;
        }

        patch.complete();

        if (patchBody.status == ExtensibilitySubscriptionCallback.Status.DONE) {
            notifyParentTask(state);
        }
    }

    private boolean validatePost(
            ExtensibilitySubscriptionCallback currentState, Operation post) {
        if (currentState.status.equals(ExtensibilitySubscriptionCallback.Status.DONE)) {
            post.fail(new IllegalStateException("Notification has already been processed."));
            return true;
        }

        return false;
    }

    private boolean validatePatch(
            ExtensibilitySubscriptionCallback patchBody,
            ExtensibilitySubscriptionCallback state, Operation patch) {

        if (patchBody.retryCounter < state.retryCounter
                && !ExtensibilitySubscriptionCallback.Status.DONE.equals(patchBody.status)) {
            patch.fail(new IllegalArgumentException("Decrease retry counter is not allowed"));
            return true;
        }

        // Once task has been marked as 'Done' it's status shouldn't be changed.
        if ((patchBody.status != null)
                && (ExtensibilitySubscriptionCallback.Status.DONE.equals(state.status)
                        && !ExtensibilitySubscriptionCallback.Status.DONE
                                .equals(patchBody.status))) {
            patch.fail(new IllegalArgumentException("Changing status is not allowed"));
            return true;
        }

        if (patchBody.serviceCallback != null) {
            patch.fail(new IllegalArgumentException("Set callback address is not allowed"));
            return true;
        }

        return false;
    }

    @Override
    public void handleStart(Operation post) {
        ExtensibilitySubscriptionCallback body = getBody(post);
        if (ExtensibilitySubscriptionCallback.Status.RESUME.equals(body.status)) {
            markDone();
        }
        post.complete();
    }

    /**
     * Self-update with status done and sets expiration time.
     */
    private void markDone() {
        ExtensibilitySubscriptionCallback patch = new ExtensibilitySubscriptionCallback();
        patch.status = ExtensibilitySubscriptionCallback.Status.DONE;
        patch.documentExpirationTimeMicros = ServiceUtils.getExpirationTimeFromNowInMicros(
                TimeUnit.MINUTES.toMicros(PROCESSED_NOTIFICATION_EXPIRE_TIME));

        sendRequest(Operation.createPatch(getUri())
                .setBody(patch)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Self patch [%s] failed: %s", getSelfLink(), Utils.toString(e));
                    }
                }));
    }

    private void notifyParentTask(ExtensibilitySubscriptionCallback body) {
        sendRequest(Operation
                .createPatch(UriUtils.buildUri(getHost(), body.serviceTaskCallback.serviceSelfLink))
                .setReferer(getHost().getUri())
                .setBody(body.replayPayload)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        getHost().log(Level.SEVERE,
                                String.format("Notification to [%s] failed. Error: %s",
                                        body.serviceTaskCallback.serviceSelfLink, e.getMessage()));
                    }
                }));
    }

    /**
     * Merges received task state with the persisted one.
     *
     * @param currentState
     *            existing state
     * @param receivedState
     *            received state from the client
     */
    private void syncTaskStates(
            ExtensibilitySubscriptionCallback currentState, Operation op) {

        currentState.status = ExtensibilitySubscriptionCallback.Status.RESUME;

        // Original callback to task which has to be resumed.
        ServiceTaskCallbackResponse serviceTaskCallbackResponse = currentState.serviceTaskCallback
                .getFinishedResponse();
        // Set custom property which defines that request has been sent from Extensibility client.
        serviceTaskCallbackResponse.addProperty(EXTENSIBILITY_RESPONSE, Boolean.TRUE.toString());

        // Every service task which supports extensibility should provide it's own
        // 'extensibilityCallbackRespons' which will define suitable for modification fields, once
        // the response from subscriber is received. Here fields are merged from response to
        // callback.
        ServiceTaskCallbackResponse extensibilityResponse = op
                .getBody(currentState.replayPayload.getClass());
        // Inherit original callback in order to be aware which task stage should be resumed.
        extensibilityResponse.copy(serviceTaskCallbackResponse);
        // Store extensibility callback in order to be used as finished callback response.
        currentState.replayPayload = extensibilityResponse;

    }

}