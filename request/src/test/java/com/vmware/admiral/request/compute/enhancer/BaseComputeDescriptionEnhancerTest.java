/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.compute.enhancer;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.request.compute.enhancer.Enhancer.EnhanceContext;
import com.vmware.admiral.service.common.AbstractInitialBootService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.test.TestContext;

/**
 * Base test class for compute description enhancer's test
 */
public abstract class BaseComputeDescriptionEnhancerTest extends BaseTestCase {

    protected ComputeDescription cd;
    protected EnhanceContext context;

    public static class TestInitialBootService extends AbstractInitialBootService {
        public static final String SELF_LINK = ManagementUriParts.CONFIG + "/test-initial-boot";

        @Override
        public void handlePost(Operation post) {
            ArrayList<ServiceDocument> states = new ArrayList<>();
            states.addAll(ProfileService.getAllDefaultDocuments());
            initInstances(post, false, true, states.toArray(new ServiceDocument[states.size()]));
        }
    }

    /**
     * Helper method traversing 'cd.diskDescLinks' and asserting DiskStates.
     */
    protected void assertDiskStates(Consumer<DiskService.DiskState> assertion) {

        assertNotNull("ComputeDescriptor.diskDescLinks", cd.diskDescLinks);

        List<Operation> getOps = cd.diskDescLinks.stream()
                .map(diskLink -> Operation.createGet(host, diskLink).setReferer(host.getReferer()))
                .collect(Collectors.toList());

        TestContext ctx = testCreate(1);

        OperationJoin.create(getOps).setCompletion((ops, exs) -> {
            if (exs != null && !exs.isEmpty()) {
                ctx.failIteration(new Throwable(exs.toString()));
                return;
            }

            for (Operation op: ops.values()) {
                DiskState diskState = op.getBody(DiskService.DiskState.class);
                try {
                    assertion.accept(diskState);
                } catch (Throwable t) {
                    ctx.failIteration(t);
                    return;
                }
            }

            ctx.completeIteration();

        }).sendWith(host);

        ctx.await();
    }


    /**
     * Blocking version of
     * {@link ComputeDescriptionEnhancer#enhance(EnhanceContext, ResourceState)}
     */
    protected void enhance(ComputeDescriptionEnhancer enhancer) {

        DeferredResult<ComputeDescription> result = enhancer.enhance(context, cd);

        TestContext ctx = testCreate(1);

        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });

        ctx.await();
    }
}
