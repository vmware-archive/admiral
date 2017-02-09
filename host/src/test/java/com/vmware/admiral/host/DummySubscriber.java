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

import java.net.URI;

import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.host.DummyService.DummyServiceTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Task that subscribes to {@link DummyService}.
 */
public class DummySubscriber extends StatelessService {

    public static final String SELF_LINK = "dummy-subscriber";

    @Override
    public void handlePost(Operation post) {

        DummyServiceTaskState body = post.getBody(DummyServiceTaskState.class);

        ContainerState containerState = new ContainerState();
        containerState.name = DummySubscriber.class.getSimpleName();
        URI containersUri = UriUtils.buildUri(getHost(), body.containerState.documentSelfLink);

        Operation.createPatch(containersUri)
                .setBody(containerState)
                .setReferer(getHost().getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        post.fail(e);
                        return;
                    }
                    post.complete();
                }).sendWith(getHost());
        ;
    }

}
