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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.common.ResourceNamePrefixService.NamePrefixRequest;
import com.vmware.admiral.service.common.ResourceNamePrefixService.NamePrefixResponse;
import com.vmware.admiral.service.common.ResourceNamePrefixService.ResourceNamePrefixState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ResourceNamePrefixServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ResourceNamePrefixService.FACTORY_LINK);
    }

    @Test
    public void testResourceNamePrefixService() throws Throwable {
        verifyService(FactoryService.create(ResourceNamePrefixService.class),
                ResourceNamePrefixState.class,
                (prefix, index) -> {
                    ResourceNamePrefixState state = new ResourceNamePrefixState();
                    state.prefix = "test";
                    state.tenantLinks = Collections
                            .singletonList(String.format("%stestGroup", prefix));

                    return state;
                },
                (prefix, serviceDocument) -> {
                    ResourceNamePrefixState state = (ResourceNamePrefixState) serviceDocument;
                    assertEquals("test", state.prefix);
                    assertEquals(Collections.singletonList(String.format("%stestGroup", prefix)),
                            state.tenantLinks);
                    assertEquals(ResourceNamePrefixState.DEFAULT_NUMBER_OF_DIGITS,
                            state.numberOfDigits);
                    assertEquals(ResourceNamePrefixState.DEFAULT_NEXT_NUMBER, state.nextNumber);
                });
    }

    @Test
    public void testValidateNextNumberInResourcePrefix() throws Throwable {
        ResourceNamePrefixState state = createValidResourceNamePrefixState();
        doPost(state, ResourceNamePrefixService.FACTORY_LINK); // validate state correct

        state.nextNumber = -1;
        validateIllegalArgument(state, "nextNumber must be positive.");

        state.nextNumber = state.getMaxNumber() + 1;
        validateIllegalArgument(state, "nextNumber must be less than the max.");
    }

    @Test
    public void testValidateNumberOfDigitsInResourcePrefix() throws Throwable {
        ResourceNamePrefixState state = createValidResourceNamePrefixState();
        doPost(state, ResourceNamePrefixService.FACTORY_LINK); // validate state correct

        state.numberOfDigits = -1;
        validateIllegalArgument(state, "numberOfDigits must be positive.");

        state.numberOfDigits = ResourceNamePrefixState.MAX_NUMBER_OF_DIGITS + 1;
        validateIllegalArgument(state, "numberOfDigits must less than max.");

        state.numberOfDigits = 3;
        assertEquals(999, state.getMaxNumber());

        state.nextNumber = 990;
        assertEquals(9, state.getRange());
    }

    @Test
    public void testValidatePrefixInResourcePrefix() throws Throwable {
        ResourceNamePrefixState state = createValidResourceNamePrefixState();
        doPost(state, ResourceNamePrefixService.FACTORY_LINK); // validate state correct

        state.prefix = null;
        validateIllegalArgument(state, "prefix must not be null.");

        state.prefix = "";
        validateIllegalArgument(state, "prefix must not empty.");

        state.prefix = "ABCDEFGH";
        assertTrue(state.prefix.length() > ResourceNamePrefixState.MAX_PREFIX_LENGTH);
        validateIllegalArgument(state, "prefix must less than max.");
    }

    @Test
    public void testValidatePatchNamePrefixRequest() throws Throwable {
        ResourceNamePrefixState state = doPost(createValidResourceNamePrefixState(),
                ResourceNamePrefixService.FACTORY_LINK); // validate state correct

        NamePrefixRequest request = new NamePrefixRequest();
        request.resourceCount = -1;

        validateLocalizableException(() -> {
            patch(state, request, true);
        }, "must not be negative.");

        request.resourceCount = 0;
        validateLocalizableException(() -> {
            patch(state, request, true);
        }, "must not be zero.");

        request.resourceCount = state.getRange() + 1;
        validateLocalizableException(() -> {
            patch(state, request, true);
        }, "must be bigger than the range.");
    }

    @Test
    public void testDefaultResourcePrefixNameCreatedOnStartUp() throws Throwable {
        waitForServiceAvailability(
                ResourceNamePrefixService.DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK);
        ResourceNamePrefixState defaultNamePrefixState = getDocument(ResourceNamePrefixState.class,
                ResourceNamePrefixService.DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK);
        assertNotNull(defaultNamePrefixState);
        assertNull(defaultNamePrefixState.tenantLinks);
    }

    @Test
    public void testResourcePrefixNameRequest() throws Throwable {
        ResourceNamePrefixState state = createValidResourceNamePrefixState();
        state.numberOfDigits = 3;
        state.nextNumber = 994;
        state = doPost(state, ResourceNamePrefixService.FACTORY_LINK);

        int currentCount = 0;
        NamePrefixRequest request = new NamePrefixRequest();
        request.resourceCount = 2;

        NamePrefixResponse response = patch(state, request);
        assertEquals(request.resourceCount, response.resourceNamePrefixes.size());
        for (int i = currentCount; i < request.resourceCount; i++) {
            assertEquals(state.prefix
                    + (state.nextNumber + currentCount + i), response.resourceNamePrefixes.get(i));
        }
        currentCount += request.resourceCount;// current count 2
        request.resourceCount = 3;
        response = patch(state, request);

        assertEquals(request.resourceCount, response.resourceNamePrefixes.size());
        for (int i = currentCount; i < request.resourceCount; i++) {
            assertEquals(state.prefix
                    + (state.nextNumber + currentCount + i), response.resourceNamePrefixes.get(i));
        }

        currentCount += request.resourceCount; // current count 5
        // there is only one left before reseting the counter
        assertEquals(state.nextNumber + currentCount, state.getMaxNumber());

        request.resourceCount = 2;
        response = patch(state, request);
        assertEquals(request.resourceCount, response.resourceNamePrefixes.size());

        assertEquals(state.prefix
                + (state.nextNumber + currentCount), response.resourceNamePrefixes.get(0));

        // back to nextNumber
        assertEquals(state.prefix + state.nextNumber,
                response.resourceNamePrefixes.get(1));
    }

    @Test
    public void testResourcePrefixWithRandomGeneratedToken() throws Throwable {
        ResourceNamePrefixState state = createValidResourceNamePrefixState();
        state.numberOfDigits = 3;
        state.nextNumber = 994;
        state.addRandomToken = true;
        state = doPost(state, ResourceNamePrefixService.FACTORY_LINK);

        int currentCount = 0;
        NamePrefixRequest request = new NamePrefixRequest();
        request.resourceCount = 2;

        NamePrefixResponse response = patch(state, request);
        assertEquals(request.resourceCount, response.resourceNamePrefixes.size());
        for (int i = currentCount; i < request.resourceCount; i++) {
            String expectedNamePrefix = state.prefix
                    + (state.nextNumber + currentCount + i);
            String namePrefix = response.resourceNamePrefixes.get(i);
            int delimiterIndex = namePrefix
                    .indexOf(ResourceNamePrefixState.RANDOM_GENERATED_TOKEN_DELIMITER);
            assertEquals(expectedNamePrefix.length(), delimiterIndex);
            int minimalRandomTokenLenght = 6;
            assertTrue(namePrefix.length() - delimiterIndex >= minimalRandomTokenLenght);
            assertEquals(expectedNamePrefix, namePrefix.substring(0, delimiterIndex));
        }
    }

    private void validateIllegalArgument(ResourceNamePrefixState state, String expecation)
            throws Throwable {
        validateLocalizableException(() -> {
            doPost(state, UriUtils.buildUri(host, ResourceNamePrefixService.FACTORY_LINK), true);
        }, expecation);

    }

    private ResourceNamePrefixState createValidResourceNamePrefixState() {
        ResourceNamePrefixState state = new ResourceNamePrefixState();
        state.prefix = "ABC";
        state.numberOfDigits = 3;
        state.nextNumber = 2;
        return state;
    }

    private NamePrefixResponse patch(ResourceNamePrefixState state, NamePrefixRequest request)
            throws Throwable {
        return patch(state, request, false);
    }

    private NamePrefixResponse patch(ResourceNamePrefixState state, NamePrefixRequest request,
            boolean expectFailure) throws Throwable {
        NamePrefixResponse[] result = new NamePrefixResponse[] { null };
        final Throwable[] error = { null };
        host.testStart(1);
        host.send(Operation
                .createPatch(UriUtils.buildUri(host, state.documentSelfLink))
                .setBody(request)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                if (expectFailure) {
                                    host.log("Can't patch name prefix service. Error: %s",
                                            Utils.toString(e));
                                    error[0] = e;
                                    host.completeIteration();
                                } else {
                                    host.log("Can't patch name prefix service. Error: %s",
                                            Utils.toString(e));
                                    host.failIteration(e);
                                }
                                return;
                            } else {
                                NamePrefixResponse outState = o.getBody(NamePrefixResponse.class);
                                result[0] = outState;
                                if (expectFailure) {
                                    host.failIteration(new IllegalStateException(
                                            "ERROR: operation completed successfully but exception excepted."));
                                } else {
                                    host.completeIteration();
                                }
                            }
                        }));
        host.testWait();

        if (expectFailure) {
            Throwable ex = error[0];
            if (ex == null) {
                host.log(Level.SEVERE, "Expected exception.");
                return null;
            }
            throw ex;
        }

        NamePrefixResponse response = result[0];
        assertNotNull(response);
        return response;
    }
}
