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

import static java.time.temporal.ChronoUnit.MILLIS;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.management.ServiceNotFoundException;

import com.google.gson.annotations.Since;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
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

    public static final String EXTENSIBILITY_ERROR_MESSAGE = "extensibilityErrorMessage";

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

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @Documentation(description = "Status message")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String errorMessage;

        @Documentation(description = "Resume counter")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public int retryCounter;

        @Documentation(
                description = "Defines Task fields which will be sent to client for information about the task.")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String notificationPayload;

        @Documentation(
                description = "Defines Task fields which will be merged when subscriber return response.")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public ServiceTaskCallbackResponse replyPayload;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @Documentation(description = "Due time of the reply.")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public LocalDateTime due;

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
    public void handleStart(Operation post) {
        ExtensibilitySubscriptionCallback body = getBody(post);
        long millisToDue = LocalDateTime.now().until(body.due, MILLIS);
        String timeoutMessage = getTimeoutMessage(body);

        if (millisToDue <= 0) {
            markDone(timeoutMessage);
        } else if (ExtensibilitySubscriptionCallback.Status.RESUME.equals(body.status)) {
            markDone(null);
        } else {
            getHost().schedule(() -> markDone(timeoutMessage), millisToDue,
                    TimeUnit.MILLISECONDS);
        }
        post.complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
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

        String error = getErrorMessage(post);

        syncTaskStates(currentState, post);

        setState(post, currentState);

        post.setBody(currentState).complete();

        if (currentState.status == ExtensibilitySubscriptionCallback.Status.RESUME) {
            markDone(error);
        }
    }

    @Override
    public void handlePut(Operation put) {
        Operation.failActionNotSupported(put);
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

        state.errorMessage = patchBody.errorMessage;

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

    /**
     * Self-update with status done and sets expiration time.
     */
    private void markDone(String statusMessage) {
        ExtensibilitySubscriptionCallback patch = new ExtensibilitySubscriptionCallback();
        patch.status = ExtensibilitySubscriptionCallback.Status.DONE;
        patch.errorMessage = statusMessage;
        patch.documentExpirationTimeMicros = ServiceUtils.getExpirationTimeFromNowInMicros(
                TimeUnit.MINUTES.toMicros(PROCESSED_NOTIFICATION_EXPIRE_TIME));

        sendRequest(Operation.createPatch(getUri())
                .setBody(patch)
                .setCompletion((o, e) -> {
                    if (e != null && !(e instanceof ServiceNotFoundException)) {
                        logSevere("Self patch [%s] failed: %s", getSelfLink(), Utils.toString(e));
                    }
                }));
    }

    private void notifyParentTask(ExtensibilitySubscriptionCallback body) {

        body.replyPayload.customProperties = body.replyPayload.customProperties != null ?
                body.replyPayload.customProperties : new HashMap<>();

        if (body.errorMessage != null && !body.errorMessage.isEmpty()) {
            body.replyPayload.customProperties
                    .put(EXTENSIBILITY_ERROR_MESSAGE, body.errorMessage);
        }

        // Put key to prevent sending event more than once in case of self patching in
        // enahnceExtensibilityResponse(after response from client has been received) stage.
        body.replyPayload.customProperties
                .put(constructExtensibilityKey(body), Boolean.TRUE.toString());

        sendRequest(Operation
                .createPatch(UriUtils.buildUri(getHost(), body.serviceTaskCallback.serviceSelfLink))
                .setReferer(getHost().getUri())
                .setBody(body.replyPayload)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        getHost().log(Level.SEVERE,
                                String.format("Notification to [%s] failed. Error: %s",
                                        body.serviceTaskCallback.serviceSelfLink, e.getMessage()));
                    }

                    getHost().log(Level.INFO,
                            String.format(
                                    "Task [%s] has been resumed. Deleting ExtensibilitySubscriptionCallback [%s].",
                                    body.serviceTaskCallback.serviceSelfLink, getSelfLink()));
                    sendRequest(Operation.createDelete(getUri()));
                }));
    }

    /**
     * Merges received task state with the persisted one.
     *
     * @param currentState existing state
     * @param op           the current service operation
     */
    private void syncTaskStates(
            ExtensibilitySubscriptionCallback currentState, Operation op) {

        currentState.status = ExtensibilitySubscriptionCallback.Status.RESUME;

        // Original callback to task which has to be resumed.
        ServiceTaskCallbackResponse serviceTaskCallbackResponse = currentState.serviceTaskCallback
                .getFinishedResponse();
        // Every service task which supports extensibility should provide it's own
        // 'extensibilityCallbackResponse' which will define suitable for modification fields, once
        // the response from subscriber is received. Here fields are merged from response to
        // callback.
        ServiceTaskCallbackResponse extensibilityResponse = op
                .getBody(currentState.replyPayload.getClass());
        // Save the properties that came back from the response to support update
        Map<String, String> customProperties = extensibilityResponse.customProperties;
        // Inherit original callback in order to be aware which task stage should be resumed.
        extensibilityResponse.copy(serviceTaskCallbackResponse);
        extensibilityResponse.customProperties = customProperties;

        // Store extensibility callback in order to be used as finished callback response.
        currentState.replyPayload = extensibilityResponse;
    }

    private static String getTimeoutMessage(ExtensibilitySubscriptionCallback state) {
        return "Timeout: due was '" + state.due + "' but expired";
    }

    private static String getErrorMessage(Operation op) {
        ExtensibilitySubscriptionCallback extensibilityResponse = op
                .getBody(ExtensibilitySubscriptionCallback.class);
        return extensibilityResponse.errorMessage;
    }

    private String constructExtensibilityKey(ExtensibilitySubscriptionCallback document) {
        return String.format("%s:%s:%s", document.taskStateClassName,
                document.replyPayload.taskInfo.stage.name(),
                document.replyPayload.taskSubStage.toString());
    }
}