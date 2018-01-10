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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.service.common.UniquePropertiesService.UniquePropertiesRequest;
import com.vmware.admiral.service.common.UniquePropertiesService.UniquePropertiesState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

public class UniquePropertiesServiceTest extends BaseTestCase {
    private UniquePropertiesState testState;

    @Before
    public void setup() throws Throwable {
        startServices(host);
        waitForServiceAvailability(UniquePropertiesService.FACTORY_LINK);
        waitForInitialBootServiceToBeSelfStopped(CommonInitialBootService.SELF_LINK);

        UniquePropertiesState state = new UniquePropertiesState();
        state.documentSelfLink = "test-service";

        testState = doPost(state, UniquePropertiesService.FACTORY_LINK);
    }

    private void startServices(VerificationHost host) {
        HostInitCommonServiceConfig.startServices(host);
    }

    @Test
    public void testProjectNameAndIndexServicesAreCreatedOnStart() {
        String projectNamesUri = UriUtils.buildUriPath(UniquePropertiesService.FACTORY_LINK,
                UniquePropertiesService.PROJECT_NAMES_ID);
        assertDocumentExists(projectNamesUri);

        String projectIndexesUri = UriUtils.buildUriPath(UniquePropertiesService.FACTORY_LINK,
                UniquePropertiesService.PROJECT_INDEXES_ID);
        assertDocumentExists(projectIndexesUri);
    }

    @Test
    public void testPost() throws Throwable {
        assertNotNull(testState);
        assertNotNull(testState.documentSelfLink);
        assertNotNull(testState.uniqueProperties);
        assertDocumentExists(testState.documentSelfLink);
    }

    @Test
    public void testPatch() throws Throwable {
        String testEntry = "test-entry";
        UniquePropertiesRequest patch = new UniquePropertiesRequest();
        patch.toAdd = Collections.singletonList(testEntry);

        doPatch(patch, testState.documentSelfLink);

        testState = getDocumentNoWait(UniquePropertiesState.class, testState.documentSelfLink);
        assertEquals(1, testState.uniqueProperties.size());
        assertTrue(testState.uniqueProperties.contains(testEntry));

        try {
            doPatch(patch, testState.documentSelfLink);
            fail("Adding entry that already exist should fail the operation.");
        } catch (Throwable ex) {
            testState = getDocumentNoWait(UniquePropertiesState.class, testState.documentSelfLink);
            assertEquals(1, testState.uniqueProperties.size());
            assertTrue(testState.uniqueProperties.contains(testEntry));
        }

        patch = new UniquePropertiesRequest();
        patch.toRemove = Collections.singletonList(testEntry);

        doPatch(patch, testState.documentSelfLink);
        testState = getDocumentNoWait(UniquePropertiesState.class, testState.documentSelfLink);
        assertEquals(0, testState.uniqueProperties.size());
        assertTrue(!testState.uniqueProperties.contains(testEntry));
    }

    @Test
    public void testConcurrentAddingEntries() throws Throwable {
        String testEntry = "test-entry";
        UniquePropertiesRequest patch = new UniquePropertiesRequest();
        patch.toAdd = Collections.singletonList(testEntry);

        Operation patchOp = Operation.createPatch(host, testState.documentSelfLink)
                .setBody(patch);

        for (int i = 0; i < 10; i++) {
            host.send(patchOp);
        }

        Thread.sleep(1000);

        testState = getDocumentNoWait(UniquePropertiesState.class, testState.documentSelfLink);

        assertTrue(testState.uniqueProperties.size() == 1);
        assertTrue(testState.uniqueProperties.contains(testEntry));
    }
}
