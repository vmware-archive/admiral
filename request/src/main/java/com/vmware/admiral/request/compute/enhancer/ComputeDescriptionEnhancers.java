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

package com.vmware.admiral.request.compute.enhancer;

import java.util.LinkedList;
import java.util.function.BiConsumer;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.StatefulService;

/**
 * Composition of all Compute description enhancers used to enhance the ComputeDescription during
 * request.
 */
public class ComputeDescriptionEnhancers implements ComputeDescriptionEnhancer {

    private final LinkedList<ComputeDescriptionEnhancer> enhancers;

    private ComputeDescriptionEnhancers() {
        this.enhancers = new LinkedList<>();
    }

    private void initialize(StatefulService sender) {
        this.enhancers.add(new CloudConfigComputeDescriptionEnhancer());
        this.enhancers.add(new GuestCredentialsComputeDescriptionEnhancer(sender));
        this.enhancers.add(new ServerCertComputeDescriptionEnhancer(sender));
    }

    public static ComputeDescriptionEnhancers build(StatefulService sender) {
        ComputeDescriptionEnhancers enhancers = new ComputeDescriptionEnhancers();
        enhancers.initialize(sender);
        return enhancers;
    }

    @Override
    public void enhance(ComputeDescription resource,
            BiConsumer<ComputeDescription, Throwable> callback) {
        ComputeDescriptionEnhancer enhancer = enhancers.poll();

        if (enhancer == null) {
            callback.accept(resource, null);
            return;
        }

        enhancer.enhance(resource, (cd, t) -> {
            if (t != null) {
                callback.accept(resource, t);
                return;
            }
            enhance(resource, callback);
        });
    }
}
