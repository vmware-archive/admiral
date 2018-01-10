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

package com.vmware.admiral.common.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.service.common.CommonInitialBootService;
import com.vmware.admiral.service.common.UniquePropertiesService;
import com.vmware.admiral.service.common.UniquePropertiesService.UniquePropertiesState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;

public class UniquePropertiesUtilTest extends BaseTestCase {

    private TestService testService;
    private UniquePropertiesState testState;

    private static class TestService extends StatelessService {
    }

    @Before
    public void setup() throws Throwable {
        startServices(host);
        waitForServiceAvailability(UniquePropertiesService.FACTORY_LINK);
        waitForInitialBootServiceToBeSelfStopped(CommonInitialBootService.SELF_LINK);
        testService = new TestService();
        testService.setHost(host);

        UniquePropertiesState state = new UniquePropertiesState();
        state.documentSelfLink = "test-service";

        testState = doPost(state, UniquePropertiesService.FACTORY_LINK);
    }

    private void startServices(VerificationHost host) {
        HostInitCommonServiceConfig.startServices(host);
    }

    @Test
    public void testClaimProperty() throws Throwable {
        String testEntry = "test-entry";
        String propertiesId = Service.getId(testState.documentSelfLink);

        TestContext ctx = testCreate(1);
        UniquePropertiesUtil.claimProperty(testService, propertiesId, testEntry)
                .whenComplete((isUsed, ex) -> {
                    if (ex != null) {
                        ctx.fail(ex);
                        return;
                    }
                    try {
                        assertFalse(isUsed);
                    } catch (Throwable err) {
                        ctx.fail(err);
                        return;
                    }
                    ctx.completeIteration();
                });

        ctx.await();
    }

    @Test
    public void testClaimAlreadyUsedProperty() {
        String testEntry = "test-entry";
        String propertiesId = Service.getId(testState.documentSelfLink);

        TestContext ctx = testCreate(1);
        UniquePropertiesUtil.claimProperty(testService, propertiesId, testEntry)
                .whenComplete((isUsed, ex) -> {
                    if (ex != null) {
                        ctx.fail(ex);
                        return;
                    }
                    ctx.completeIteration();
                });

        ctx.await();

        TestContext ctx1 = testCreate(1);
        UniquePropertiesUtil.claimProperty(testService, propertiesId, testEntry)
                .whenComplete((isUsed, ex) -> {
                    if (ex != null) {
                        ctx1.fail(ex);
                        return;
                    }
                    try {
                        assertTrue(isUsed);
                    } catch (Throwable err) {
                        ctx1.fail(err);
                        return;
                    }
                    ctx1.completeIteration();
                });
        ctx1.await();
    }

    @Test
    public void testFreeClaimedProperty() throws Throwable {
        String testEntry = "test-entry";
        String propertiesId = Service.getId(testState.documentSelfLink);

        TestContext ctx = testCreate(1);
        UniquePropertiesUtil.claimProperty(testService, propertiesId, testEntry)
                .whenComplete((isUsed, ex) -> {
                    if (ex != null) {
                        ctx.fail(ex);
                        return;
                    }
                    ctx.completeIteration();
                });

        ctx.await();

        TestContext ctx1 = testCreate(1);
        UniquePropertiesUtil.freeProperty(testService, propertiesId, testEntry)
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        ctx1.fail(ex);
                        return;
                    }
                    ctx1.completeIteration();
                });
        ctx1.await();

        UniquePropertiesState state = getDocumentNoWait(UniquePropertiesState.class,
                testState.documentSelfLink);

        assertTrue(state.uniqueProperties.size() == 0);
    }

    @Test
    public void testUpdateClaimedProperty() throws Throwable {
        String testEntry = "test-entry";
        String propertiesId = Service.getId(testState.documentSelfLink);

        TestContext ctx = testCreate(1);
        UniquePropertiesUtil.claimProperty(testService, propertiesId, testEntry)
                .whenComplete((isUsed, ex) -> {
                    if (ex != null) {
                        ctx.fail(ex);
                        return;
                    }
                    ctx.completeIteration();
                });

        ctx.await();

        String newTestEntry = "test-entry-new";

        TestContext ctx1 = testCreate(1);
        UniquePropertiesUtil
                .updateClaimedProperty(testService, propertiesId, newTestEntry, testEntry)
                .whenComplete((isUsed, ex) -> {
                    if (ex != null) {
                        ctx1.fail(ex);
                        return;
                    }
                    ctx1.completeIteration();
                });

        ctx1.await();

        UniquePropertiesState state = getDocumentNoWait(UniquePropertiesState.class,
                testState.documentSelfLink);

        assertTrue(state.uniqueProperties.size() == 1);
        assertTrue(state.uniqueProperties.contains(newTestEntry));
    }

}
