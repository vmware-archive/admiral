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

package com.vmware.admiral.host;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.host.DummyService.DummyServiceTaskState;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionCallbackService.ExtensibilitySubscriptionCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

/**
 * Task that subscribes to {@link DummyService}.
 */
public class DummySubscriber extends StatelessService {

    public static final String SELF_LINK = "dummy-subscriber";

    @Override
    public void handlePost(Operation post) {

        ExtensibilitySubscriptionCallback callback = post
                .getBody(ExtensibilitySubscriptionCallback.class);

        if (callback.notificationPayload != null) {
            // If subscription is blocking than change task's name in order to validate that
            // subscriber will modify it afterwards.
            changeTaskName(callback, post);
        } else {
            // If subscription is not blocking just change the name of resource when task is
            // finished.
            changeContainerName(post);
        }

    }

    private void changeContainerName( Operation post) {

        DummyServiceTaskState body =  post.getBody(DummyServiceTaskState.class);

        ContainerState containerState = new ContainerState();
        containerState.name = DummySubscriber.class.getSimpleName();
        containerState.documentSelfLink = body.containerState.documentSelfLink;

        // Patch Container's name.
        TestContext context = new TestContext(1, Duration.ofSeconds(20));
        Operation.createPatch(UriUtils.buildUri(getHost(), containerState.documentSelfLink))
                .setBody(containerState)
                .setReferer(getHost().getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        post.fail(e);
                        context.fail(e);
                        return;
                    }

                }).sendWith(getHost());

        context.await();

    }

    private void changeTaskName(ExtensibilitySubscriptionCallback callback, Operation post) {

        Map<String, String> response = new HashMap<>();
        response.put("name", SELF_LINK);

        TestContext context = new TestContext(1, Duration.ofSeconds(20));
        Operation.createPost(callback.serviceCallback)
                .setReferer(getHost().getUri())
                .setBody(response)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        post.fail(e);
                        context.fail(e);
                        return;
                    }
                    context.completeIteration();
                }).sendWith(getHost());
        context.await();
    }

}
