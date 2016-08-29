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

package com.vmware.admiral.common.util;

import java.util.Set;
import java.util.function.Consumer;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceSubscriptionState.ServiceSubscriber;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ReliableSubscriptionService;

public class SubscriptionUtils {

    public static void subscribeToNotifications(StatefulService service,
            Consumer<Operation> notificationConsumer, String taskLink) {
        ServiceSubscriber subscribeBody = new ServiceSubscriber();
        subscribeBody.replayState = true;
        subscribeBody.usePublicUri = true;
        Operation subscribeOp = Operation
                .createPost(service, taskLink)
                .setReferer(service.getUri())
                .setCompletion(
                        (regOp, regEx) -> {
                            if (regEx != null) {
                                SubscriptionUtils.sendFailureSelfPatch(service, regEx);
                                return;
                            }
                        });
        ReliableSubscriptionService notificationTarget = ReliableSubscriptionService.create(
                subscribeOp, subscribeBody, notificationConsumer);
        service.getHost().startSubscriptionService(subscribeOp,
                notificationTarget, subscribeBody);
    }

    /**
     * handle subscriptions from multiple services
     * 1. Mark operation as complete
     * 2. If the operation is not PUT or POST, return
     * 3. If the task has failed, send failure patch to service
     * 4. Update the list of services from which we have received notification
     * 5. Unsubscribe from notifications, optionally delete the task that raised the notification
     * 6. If we have received the expected number of notifications, patch the next state back
     *
     * @param service Stateful provisioning service
     * @param update Notification operation
     * @param notificationTaskLink Self link of the task that raised the notification
     * @param opTaskState TaskState of the service that raised the notification
     * @param expectedNotificationCount Expected number of notifications
     * @param returnState The next state for the StatefulService
     * @param finishedTaskLinks Set of self links for services from which we have received notification
     * @param deleteTask flag to delete the service that raised the notification
     */
    public static void handleSubscriptionNotifications(StatefulService service, Operation update,
            String notificationTaskLink, TaskState opTaskState,
            int expectedNotificationCount, Object returnState, Set<String> finishedTaskLinks,
            boolean deleteTask) {
        int finishedTaskCount;

        update.complete();

        if ((update.getAction() != Action.PATCH && update.getAction() != Action.PUT)) {
            return;
        }

        // Fail if task was cancelled or has failed
        if (TaskState.isCancelled(opTaskState)
                || TaskState.isFailed(opTaskState)) {
            SubscriptionUtils.sendFailureSelfPatch(service,
                    new IllegalStateException("Operation failed:"
                            + Utils.toJsonHtml(update)));
            if (deleteTask) {
                service.sendRequest(Operation
                        .createDelete(
                                service, notificationTaskLink)
                        .setBody(new ServiceDocument()));
            }
            return;
        }

        // Ignore if task has not finished yet
        if (!TaskState.isFinished(opTaskState)) {
            return;
        }

        // Ignore if task has already been seen
        synchronized (finishedTaskLinks) {
            if (!finishedTaskLinks.add(notificationTaskLink)) {
                return;
            }

            // Retrieve size in synchronized block to prevent racing
            finishedTaskCount = finishedTaskLinks.size();
        }

        Operation deleteOp = Operation.createDelete(service, notificationTaskLink)
                .setReferer(service.getUri())
                .setCompletion(
                        (delOp, delEx) -> {
                            if (delEx != null) {
                                service.logWarning("Stopping subscriber failed %s",
                                        Utils.toString(delEx));
                                return;
                            }
                        });
        service.getHost().stopSubscriptionService(deleteOp,
                UriUtils.buildPublicUri(service.getHost(), update.getUri().getPath()));

        if (deleteTask) {
            service.sendRequest(Operation
                    .createDelete(
                            service, notificationTaskLink)
                    .setBody(new ServiceDocument()));
        }

        //patch service with return state when we have seen all tasks finish
        if (finishedTaskCount == expectedNotificationCount) {
            // Patch back the return state
            SubscriptionUtils.sendPatch(service, returnState);
        }
    }

    /**
     * Send a failure patch to the specified service
     *
     * @param service service to send the patch to
     * @param e Exception
     */
    private static void sendFailureSelfPatch(StatefulService service, Throwable e) {
        StatefulTaskDocument body = new StatefulTaskDocument();
        body.taskInfo = new TaskState();
        body.taskInfo.stage = TaskStage.FAILED;
        body.taskInfo.failure = Utils.toServiceErrorResponse(e);
        service.logWarning("Operation failed: %s", Utils.toString(e));
        sendPatch(service, body);
    }

    private static class StatefulTaskDocument extends ServiceDocument {
        public TaskState taskInfo;
    }

    /**
     * Helper method to send a patch to the specified service
     *
     * @param service sevice to send the patch to
     * @param body patch body
     */
    private static void sendPatch(StatefulService service, Object body) {
        Operation patch = Operation
                .createPatch(service.getUri())
                .setBody(body)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                service.logWarning("Self patch failed: %s", Utils.toString(ex));
                            }
                        });
        service.sendRequest(patch);
    }

}
