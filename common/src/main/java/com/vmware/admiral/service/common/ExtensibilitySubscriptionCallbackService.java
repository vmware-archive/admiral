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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * {@code ExtensibilitySubscriptionCallbackService} is used for resuming blocked task service.
 * <p>
 * When client has received blocking notification, it need to send a post request to this service
 * to resume the blocked task service.
 *
 * @see ExtensibilitySubscriptionManager
 */
public class ExtensibilitySubscriptionCallbackService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.EXTENSIBILITY_CALLBACKS;

    private static final int RESUME_RETRY_COUNT = Integer.getInteger(
            "com.vmware.admiral.service.extensibility.resume.retries", 3);
    private static final int RESUME_RETRY_WAIT = Integer.getInteger(
            "com.vmware.admiral.service.extensibility.resume.wait", 15);
    private static final int PROCESSED_NOTIFICATION_EXPIRE_TIME = Integer.getInteger(
            "com.vmware.admiral.service.extensibility.expiration.processed", 60);

    public static class ExtensibilitySubscriptionCallback<T extends TaskServiceDocument<?>>
            extends MultiTenantDocument {

        public enum Status {
            BLOCKED, RESUME, DONE;
        }

        @Documentation(description = "Callback address")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public URI serviceCallback;

        @Documentation(description = "Task state json")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String taskStateJson;

        @Documentation(description = "State status")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Status status;

        @Documentation(description = "Resume counter")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public int retryCounter;

        @Documentation(description = "Custom properties")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, String> customProperties;

        public String taskStateClassName;
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

        ExtensibilitySubscriptionCallback<?> body = getBody(post);

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

        ExtensibilitySubscriptionCallback<?> currentState = getState(post);
        ExtensibilitySubscriptionCallback<?> body = getBody(post);

        if (validatePost(currentState, body, post)) {
            return;
        }

        syncTaskStates(currentState, body);
        setState(post, currentState);
        post.complete();

        resumeTaskService(currentState);
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

        ExtensibilitySubscriptionCallback<?> patchBody = getBody(patch);
        ExtensibilitySubscriptionCallback<?> state = getState(patch);
        if (validatePatch(patchBody, state, patch)) {
            return;
        }

        PropertyUtils.mergeServiceDocuments(state, patchBody);

        patch.complete();
    }

    private boolean validatePost(
            ExtensibilitySubscriptionCallback<?> currentState,
            ExtensibilitySubscriptionCallback<?> body, Operation post) {
        if (currentState.status.equals(ExtensibilitySubscriptionCallback.Status.DONE)) {
            post.fail(new IllegalStateException("Notification has already been processed."));
            return true;
        }

        return false;
    }

    private boolean validatePatch(
            ExtensibilitySubscriptionCallback<?> patchBody,
            ExtensibilitySubscriptionCallback<?> state, Operation patch) {

        if (patchBody.retryCounter < state.retryCounter) {
            patch.fail(new IllegalArgumentException("Decrease retry counter is not allowed"));
            return true;
        }

        if (ExtensibilitySubscriptionCallback.Status.DONE.equals(state.status)
                && !ExtensibilitySubscriptionCallback.Status.DONE.equals(patchBody.status)) {
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
        ExtensibilitySubscriptionCallback<?> body = getBody(post);
        if (ExtensibilitySubscriptionCallback.Status.RESUME.equals(body.status)) {
            resumeTaskService(body);
        }
        post.complete();
    }

    /**
     * Resumes task service by sending patch to it. Support retrying in case of
     * an error. After successful patch marks the extensibility callback state
     * as processed.
     *
     * @param state {@code LifecycleExtensibilityCallbackState}
     */
    private void resumeTaskService(ExtensibilitySubscriptionCallback<?> state) {
        @SuppressWarnings("rawtypes")
        ExtensibilitySubscriptionCallback<?> patch = new ExtensibilitySubscriptionCallback();
        patch.retryCounter = ++state.retryCounter;
        sendRequest(Operation.createPatch(this, state.documentSelfLink)
                .setBody(patch)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Self patch to [%s] failed: %s",
                                state.documentSelfLink, Utils.toString(e));
                        return;
                    }

                    Operation.createPatch(state.serviceCallback)
                            .setBody(state.taskStateJson)
                            .setReferer(ExtensibilitySubscriptionManager.SELF_LINK)
                            .setCompletion((op, ex) -> {
                                if (ex != null) {
                                    logFine("Cannot resume task [%s] : %s",
                                            state.serviceCallback, ex.getMessage());
                                    if (state.retryCounter >= RESUME_RETRY_COUNT) {
                                        logSevere("Cannot resume task [%s] : %s",
                                                state.serviceCallback, ex.getMessage());
                                    } else {
                                        getHost().schedule(() -> resumeTaskService(state),
                                                RESUME_RETRY_WAIT, TimeUnit.SECONDS);
                                    }
                                    return;
                                }

                                markDone();
                            })
                            .sendWith(getHost());
                }));
    }

    /**
     * Self-update with status done and sets expiration time.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
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

    /**
     * Merges received task state with the persisted one.
     *
     * @param currentState  existing state
     * @param receivedState received state from the client
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void syncTaskStates(
            ExtensibilitySubscriptionCallback currentState,
            ExtensibilitySubscriptionCallback receivedState) {

        currentState.status = ExtensibilitySubscriptionCallback.Status.RESUME;

        if (receivedState != null && receivedState.taskStateJson != null) {
            mergeTaskState(currentState, receivedState);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private <T extends TaskServiceDocument<?>> void mergeTaskState(
            ExtensibilitySubscriptionCallback currentState,
            ExtensibilitySubscriptionCallback receivedState) {
        try {
            Class taskClass = Class.forName(currentState.taskStateClassName);

            T currentTask = (T) Utils.fromJson(currentState.taskStateJson, taskClass);
            T receivedTask = (T) Utils.fromJson(receivedState.taskStateJson, taskClass);

            PropertyUtils.mergeServiceDocuments(currentTask, receivedTask);

            currentState.taskStateJson = Utils.toJson(currentTask);
        } catch (Exception e) {
            logSevere("Unable to merge extensibility response for [%s] : %s",
                    getSelfLink(), Utils.toString(e));
            throw new RuntimeException(e);
        }
    }

}