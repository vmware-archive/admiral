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

import java.util.function.Consumer;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceSubscriptionState.ServiceSubscriber;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
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
