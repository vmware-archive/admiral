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

import java.net.URI;
import java.util.function.Consumer;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceSubscriptionState.ServiceSubscriber;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ReliableSubscriptionService;

public class SubscriptionUtils {

    /**
     * Helper routine to subscribe to notifications
     * @param host service host to invoke the operation
     * @param onSuccessConsumer consumer callback to invoke on notification
     * @param onFailureConsumer consumer callback to invoke on failure
     * @param taskLink link to the task to subscribe to
     */
    public static void subscribeToNotifications(ServiceHost host,
            Consumer<Operation> onSuccessConsumer,
            Consumer<Throwable> onFailureConsumer,
            String taskLink) {
        ServiceSubscriber subscribeBody = new ServiceSubscriber();
        subscribeBody.replayState = true;
        subscribeBody.usePublicUri = true;
        Operation subscribeOp = Operation
                .createPost(host, taskLink)
                .setReferer(host.getUri())
                .setCompletion((regOp, regEx) -> {
                    if (regEx != null) {
                        onFailureConsumer.accept(regEx);
                    }
                });
        ReliableSubscriptionService notificationTarget = ReliableSubscriptionService.create(
                subscribeOp, subscribeBody, onSuccessConsumer);
        host.startSubscriptionService(subscribeOp, notificationTarget, subscribeBody);
    }

    /**
     * Unsubscribe notifications.
     *
     * @param host service host to invoke the operation
     * @param publisherLink the notification publisher link
     * @param notificationTarget the notification target link
     */
    public static void unsubscribeNotifications(ServiceHost host, String publisherLink,
            URI notificationTarget) {
        host.stopSubscriptionService(
                Operation.createDelete(host, publisherLink).setReferer(host.getUri()),
                notificationTarget);
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
