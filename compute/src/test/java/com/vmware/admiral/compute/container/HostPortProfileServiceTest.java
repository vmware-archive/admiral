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
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class HostPortProfileServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(HostPortProfileService.FACTORY_LINK);
    }

    @Test
    public void testHostPortProfileServices() throws Throwable {
        verifyService(
                FactoryService.create(HostPortProfileService.class),
                HostPortProfileService.HostPortProfileState.class,
                (prefix, index) -> {
                    return createHostPortProfile();
                },
                (prefix, serviceDocument) -> {
                    HostPortProfileService.HostPortProfileState profile =
                            (HostPortProfileService.HostPortProfileState) serviceDocument;
                    assertNotNull(profile.hostLink);
                    assertNotNull(profile.reservedPorts);
                    assertTrue(profile.startPort >= HostPortProfileService.HostPortProfileState.PROFILE_RANGE_START_PORT
                             && profile.startPort < HostPortProfileService.HostPortProfileState.PROFILE_RANGE_START_PORT + 1000);
                    assertEquals(
                            HostPortProfileService.HostPortProfileState.PROFILE_RANGE_END_PORT,
                            profile.endPort);
                    assertNotNull(profile.documentSelfLink);
                });
    }

    @Test
    public void testUpdate() throws Throwable {
        HostPortProfileService.HostPortProfileState profile = createHostPortProfile();
        profile = doPost(profile, HostPortProfileService.FACTORY_LINK);

        profile.startPort = new Long("5000");

        HostPortProfileService.HostPortProfileState result = doPut(profile);
        assertEquals(profile.startPort, result.startPort);
    }

    @Test
    public void testPatch() throws Throwable {
        HostPortProfileService.HostPortProfileState profile = createHostPortProfile();
        profile = doPost(profile, HostPortProfileService.FACTORY_LINK);

        profile.startPort = new Long("5000");

        HostPortProfileService.HostPortProfileState result = doPatch(profile,
                profile.documentSelfLink);
        assertEquals(profile.startPort, result.startPort);
    }

    @Test
    public void testDelete() throws Throwable {
        HostPortProfileService.HostPortProfileState profile = createHostPortProfile();
        profile = doPost(profile, HostPortProfileService.FACTORY_LINK);
        doDelete(UriUtils.buildUri(host, profile.documentSelfLink), false);
    }

    @Test
    public void testPatchAllocate() throws Throwable {
        HostPortProfileService.HostPortProfileState profile = createHostPortProfile();
        profile = doPost(profile, HostPortProfileService.FACTORY_LINK);

        HostPortProfileService.HostPortProfileReservationRequest request =
                new HostPortProfileService.HostPortProfileReservationRequest();
        request.containerLink = UUID.randomUUID().toString();
        request.mode = HostPortProfileService.HostPortProfileReservationRequestMode.ALLOCATE;
        request.specificHostPorts = new HashSet<>();
        request.specificHostPorts.add(new Long(34567));
        request.additionalHostPortCount = 5;

        HostPortProfileService.HostPortProfileState result = patch(profile, request, false);
        assertEquals(6, result.reservedPorts.size());
        assertTrue(result.reservedPorts
                .entrySet()
                .stream()
                .allMatch(p -> request.containerLink.equals(p.getValue())));
        assertTrue(result.reservedPorts.containsKey(new Long(34567)));

        request.containerLink = UUID.randomUUID().toString();
        result = patch(profile, request, false);
        assertEquals(11, result.reservedPorts.size());
        assertTrue(result.reservedPorts
                .entrySet()
                .stream()
                .anyMatch(p -> p.getKey() == 34567 && request.containerLink.equals(p.getValue())));
    }

    @Test
    public void testPatchNoAvailablePorts() throws Throwable {
        HostPortProfileService.HostPortProfileState profile = createHostPortProfile();
        profile.startPort = 1;
        profile.endPort = 1;
        HostPortProfileService.HostPortProfileState result =
                doPost(profile, HostPortProfileService.FACTORY_LINK);

        HostPortProfileService.HostPortProfileReservationRequest request =
                new HostPortProfileService.HostPortProfileReservationRequest();
        request.containerLink = UUID.randomUUID().toString();
        request.mode = HostPortProfileService.HostPortProfileReservationRequestMode.ALLOCATE;
        request.additionalHostPortCount = 1;

        validateLocalizableException(() -> {
            patch(result, request, true);
        }, "There are no available ports left");
    }

    @Test
    public void testPatchRelease() throws Throwable {
        HostPortProfileService.HostPortProfileState profile = createHostPortProfile();
        profile = doPost(profile, HostPortProfileService.FACTORY_LINK);

        HostPortProfileService.HostPortProfileReservationRequest allocateRequest =
                new HostPortProfileService.HostPortProfileReservationRequest();
        allocateRequest.containerLink = UUID.randomUUID().toString();
        allocateRequest.mode = HostPortProfileService.HostPortProfileReservationRequestMode.ALLOCATE;
        allocateRequest.additionalHostPortCount = 1;

        patch(profile, allocateRequest, false);

        allocateRequest = new HostPortProfileService.HostPortProfileReservationRequest();
        allocateRequest.containerLink = UUID.randomUUID().toString();
        allocateRequest.mode = HostPortProfileService.HostPortProfileReservationRequestMode.ALLOCATE;
        allocateRequest.additionalHostPortCount = 1;

        patch(profile, allocateRequest, false);

        HostPortProfileService.HostPortProfileReservationRequest releaseRequest =
                new HostPortProfileService.HostPortProfileReservationRequest();
        releaseRequest.containerLink = allocateRequest.containerLink;
        releaseRequest.mode = HostPortProfileService.HostPortProfileReservationRequestMode.RELEASE;

        HostPortProfileService.HostPortProfileState result = patch(profile, releaseRequest, false);
        assertEquals(1, result.reservedPorts.size());
        assertTrue(result.reservedPorts
                .entrySet()
                .stream()
                .noneMatch(p -> releaseRequest.containerLink.equals(p.getValue())));
    }

    @Test
    public void testPatchUpdateAllocation() throws Throwable {
        HostPortProfileService.HostPortProfileState profile = createHostPortProfile();
        profile = doPost(profile, HostPortProfileService.FACTORY_LINK);

        HostPortProfileService.HostPortProfileReservationRequest allocateRequest =
                new HostPortProfileService.HostPortProfileReservationRequest();
        allocateRequest.containerLink = UUID.randomUUID().toString();
        allocateRequest.mode = HostPortProfileService.HostPortProfileReservationRequestMode.ALLOCATE;
        allocateRequest.additionalHostPortCount = 1;
        allocateRequest.specificHostPorts = new HashSet<>();
        allocateRequest.specificHostPorts.add(new Long(34567));

        patch(profile, allocateRequest, false);

        HostPortProfileService.HostPortProfileReservationRequest updateRequest =
                new HostPortProfileService.HostPortProfileReservationRequest();
        updateRequest.containerLink = allocateRequest.containerLink;
        updateRequest.mode = HostPortProfileService.HostPortProfileReservationRequestMode.UPDATE_ALLOCATION;
        updateRequest.specificHostPorts = allocateRequest.specificHostPorts;
        allocateRequest.specificHostPorts.add(new Long(34568));

        HostPortProfileService.HostPortProfileState result = patch(profile, updateRequest, false);
        assertEquals(2, result.reservedPorts.size());
        assertEquals(updateRequest.containerLink, result.reservedPorts.get(new Long(34567)));
        assertEquals(updateRequest.containerLink, result.reservedPorts.get(new Long(34568)));
    }

    @Test
    public void testPatchUpdateAllocationOverridePort() throws Throwable {
        HostPortProfileService.HostPortProfileState profile = createHostPortProfile();
        profile = doPost(profile, HostPortProfileService.FACTORY_LINK);

        HostPortProfileService.HostPortProfileReservationRequest allocateRequest =
                new HostPortProfileService.HostPortProfileReservationRequest();
        allocateRequest.containerLink = UUID.randomUUID().toString();
        allocateRequest.mode = HostPortProfileService.HostPortProfileReservationRequestMode.ALLOCATE;
        allocateRequest.additionalHostPortCount = 1;
        allocateRequest.specificHostPorts = new HashSet<>();
        allocateRequest.specificHostPorts.add(new Long(34567));

        patch(profile, allocateRequest, false);

        HostPortProfileService.HostPortProfileReservationRequest updateRequest =
                new HostPortProfileService.HostPortProfileReservationRequest();
        updateRequest.containerLink = UUID.randomUUID().toString();
        updateRequest.mode = HostPortProfileService.HostPortProfileReservationRequestMode.UPDATE_ALLOCATION;
        updateRequest.specificHostPorts = allocateRequest.specificHostPorts;
        allocateRequest.specificHostPorts.add(new Long(34568));

        HostPortProfileService.HostPortProfileState result = patch(profile, updateRequest, false);
        assertEquals(3, result.reservedPorts.size());
        assertEquals(updateRequest.containerLink, result.reservedPorts.get(new Long(34567)));
        assertEquals(updateRequest.containerLink, result.reservedPorts.get(new Long(34568)));
        assertTrue(result.reservedPorts.containsValue(allocateRequest.containerLink));
    }

    private HostPortProfileService.HostPortProfileState createHostPortProfile() {
        HostPortProfileService.HostPortProfileState profile =
                new HostPortProfileService.HostPortProfileState();
        profile.hostLink = UUID.randomUUID().toString();
        return profile;
    }

    private HostPortProfileService.HostPortProfileState patch(
            HostPortProfileService.HostPortProfileState state,
            HostPortProfileService.HostPortProfileReservationRequest request,
            boolean expectFailure) throws Throwable {
        HostPortProfileService.HostPortProfileState[] result =
                new HostPortProfileService.HostPortProfileState[] { null };
        final Throwable[] error = { null };
        host.testStart(1);
        host.send(Operation
                .createPatch(UriUtils.buildUri(host, state.documentSelfLink))
                .setBody(request)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                if (expectFailure) {
                                    host.log("Can't patch host port profile service. Error: %s",
                                            Utils.toString(e));
                                    error[0] = e;
                                    host.completeIteration();
                                } else {
                                    host.log("Can't patch host port profile service. Error: %s",
                                            Utils.toString(e));
                                    host.failIteration(e);
                                }
                                return;
                            } else {
                                HostPortProfileService.HostPortProfileState outState =
                                        o.getBody(HostPortProfileService.HostPortProfileState.class);
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

        HostPortProfileService.HostPortProfileState response = result[0];
        assertNotNull(response);
        return response;
    }
}
