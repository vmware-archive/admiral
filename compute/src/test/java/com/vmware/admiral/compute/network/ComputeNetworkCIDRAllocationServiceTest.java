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

package com.vmware.admiral.compute.network;

import static org.hamcrest.core.Is.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest.allocationRequest;
import static com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest.deallocationRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationState;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationServiceTest.ComputeNetworkCIDRAllocationEndToEndTest;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationServiceTest.MiscServiceTest;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for {@link ComputeNetworkCIDRAllocationService} class.
 */
@RunWith(ComputeNetworkCIDRAllocationServiceTest.class)
@SuiteClasses({ ComputeNetworkCIDRAllocationEndToEndTest.class,
        MiscServiceTest.class,
        ComputeNetworkCIDRAllocationServiceTest.ConcurrentTest.class })
public class ComputeNetworkCIDRAllocationServiceTest extends Suite {
    private static final String NETWORK_LINK = "/myNetworkLink";
    // 4 subnets possible, each with 2 IP addresses.
    // Network 1111.1111.1111.1111.1111.1111.1111.1000
    // Subnets 1111.1111.1111.1111.1111.1111.1111.1110
    private static final String NETWORK_ADDRESS = "192.168.0.0";
    private static final int NETWORK_CIDR_PREFIX_LENGTH = 29;
    private static final String NETWORK_CIDR = NETWORK_ADDRESS + "/" + NETWORK_CIDR_PREFIX_LENGTH;
    private static final int SUBNET_CIDR_PREFIX_LENGTH = 31;
    private static final int MAX_SUBNETS = 1 << (SUBNET_CIDR_PREFIX_LENGTH - NETWORK_CIDR_PREFIX_LENGTH);
    private static final String[] SUBNET_CIDRS = {
            "192.168.0.0/31",
            "192.168.0.2/31",
            "192.168.0.4/31",
            "192.168.0.6/31", };
    private static final SubnetUtils NETWORK_UTILS = new SubnetUtils(NETWORK_CIDR);
    private static final SubnetInfo NETWORK_INFO = NETWORK_UTILS.getInfo();

    static {
        NETWORK_UTILS.setInclusiveHostCount(true);
    }

    public ComputeNetworkCIDRAllocationServiceTest(Class<?> klass, RunnerBuilder builder)
            throws Throwable {
        super(klass, builder);
    }

    /**
     * Enhance BaseModelTest with helper methods required by this family of tests.
     */
    static class ComputeNetworkCIDRAllocationBaseTest
            extends com.vmware.admiral.compute.container.ComputeBaseTest {

        @Before
        public void setUp() throws Throwable {
            waitForServiceAvailability(ComputeNetworkCIDRAllocationService.FACTORY_LINK);
        }

        String createNetworkCIDRAllocationState() throws Throwable {
            NetworkState network = new NetworkState();
            network.subnetCIDR = NETWORK_CIDR;
            network.name = "IsolatedNetwork";
            network.instanceAdapterReference = UriUtils.buildUri("/instance-adapter-reference");
            network.resourcePoolLink = "/dummy-resource-pool-link";
            network.regionId = "dummy-region-id";
            network = doPost(network, NetworkService.FACTORY_LINK);

            return createNetworkCIDRAllocationState(network.documentSelfLink);
        }

        String createNetworkCIDRAllocationState(String networkLink) throws Throwable {
            ComputeNetworkCIDRAllocationState state = new ComputeNetworkCIDRAllocationState();
            state.networkLink = networkLink;
            state.subnetCIDRPrefixLength = SUBNET_CIDR_PREFIX_LENGTH;
            state = doPost(state, ComputeNetworkCIDRAllocationService.FACTORY_LINK);

            return state.documentSelfLink;
        }

        SubnetState createSubnet(int index) throws Throwable {
            SubnetState subnet = new SubnetState();
            subnet.id = UUID.randomUUID().toString();
            subnet.name = "isolatedSubnet" + index;
            subnet.networkLink = NETWORK_LINK;
            subnet.subnetCIDR = "0.0.0.0/16";

            subnet = doPost(subnet, SubnetService.FACTORY_LINK);

            return subnet;
        }
    }

    @RunWith(Parameterized.class)
    public static class ComputeNetworkCIDRAllocationEndToEndTest
            extends ComputeNetworkCIDRAllocationBaseTest {

        @Rule
        public ExpectedException expectedException = ExpectedException.none();

        Config config;

        // Test configuration data.
        private static class Config {
            int numberOfAllocationRequests;
            int numberOfDeallocationRequests;
            int numberOfSecondaryAllocationRequests;
            Class<? extends RuntimeException> expectedExceptionClass;

            Config(int numberOfAllocationRequests, int numberOfDeallocationRequests,
                    int numberOfSecondaryAllocationRequests,
                    Class<? extends RuntimeException> expectedExceptionClass) {

                this.numberOfAllocationRequests = numberOfAllocationRequests;
                this.numberOfDeallocationRequests = numberOfDeallocationRequests;
                this.numberOfSecondaryAllocationRequests = numberOfSecondaryAllocationRequests;
                this.expectedExceptionClass = expectedExceptionClass;

            }
        }

        // Run the same test using different COMPLETE adapters
        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(new Object[][] {
                    { "[Positive] Allocate max subnets.",
                            new Config(MAX_SUBNETS, 0, 0, null) },
                    { "[Positive] Allocate max subnets, deallocate 1, allocate 1 again.",
                            new Config(MAX_SUBNETS, 1, 1, null) },
                    { "[Negative] Allocate max subnets + 1, Expect: IllegalStateException.",
                            new Config(MAX_SUBNETS + 1, 1, 1, IllegalStateException.class) }
            });
        }

        public ComputeNetworkCIDRAllocationEndToEndTest(String testCaseName /* ignored here */,
                Config config) {
            this.config = config;
            if (config.expectedExceptionClass != null) {
                this.expectedException.expectCause(isA(config.expectedExceptionClass));
            }
        }

        /**
         * The test does:
         * <ol>
         * <li>Allocate numberOfAllocationRequests CIDRs and assert expected state.</li>
         * <li>Deallocate numberOfDeallocationRequests CIDRs and assert expected state.</li>
         * <li>Allocate numberOfSecondaryAllocationRequests CIDRs and assert expected state.</li>
         * </ol>
         * Where numberOfAllocationRequests, numberOfDeallocationRequests and
         * numberOfSecondaryAllocationRequests are configuration parameters of the test.
         */
        @Test
        public void testCompleteFlow() throws Throwable {
            String cidrAllocationLink = this.createNetworkCIDRAllocationState();
            String[] subnetIds = new String[config.numberOfAllocationRequests];

            // Allocate
            for (int i = 0; i < config.numberOfAllocationRequests; i++) {
                SubnetState subnet = createSubnet(i);
                subnetIds[i] = subnet.id;

                ComputeNetworkCIDRAllocationRequest request = allocationRequest(subnet.id);

                ComputeNetworkCIDRAllocationState allocation =
                        doPatch(request, ComputeNetworkCIDRAllocationState.class,
                                cidrAllocationLink);

                String lastAllocatedCIDR = allocation.allocatedCIDRs.get(request.subnetId);
                assertNotNull(lastAllocatedCIDR);
                assertTrue(NETWORK_INFO.isInRange(lastAllocatedCIDR.split("/")[0]));
                assertEquals(SUBNET_CIDRS[i], lastAllocatedCIDR);

                assertNotNull(allocation.allocatedCIDRs);
                assertEquals(i + 1, allocation.allocatedCIDRs.size());
                assertTrue(allocation.allocatedCIDRs.containsKey(request.subnetId));
            }

            // Deallocate
            if (config.numberOfDeallocationRequests > 0) {
                for (int i = 0; i < config.numberOfDeallocationRequests; i++) {
                    // Now deallocate.
                    ComputeNetworkCIDRAllocationRequest deallocationRequest =
                            deallocationRequest(subnetIds[i]);

                    ComputeNetworkCIDRAllocationState deallocation = doPatch(deallocationRequest,
                            ComputeNetworkCIDRAllocationState.class, cidrAllocationLink);

                    assertFalse(deallocation.allocatedCIDRs
                            .containsKey(deallocationRequest.subnetId));
                    assertFalse(deallocation.allocatedCIDRs.containsValue(SUBNET_CIDRS[i]));
                    assertTrue(deallocation.deallocatedCIDRs.contains(SUBNET_CIDRS[i]));
                }
            }

            if (config.numberOfSecondaryAllocationRequests > 0) {
                for (int i = 0; i < config.numberOfSecondaryAllocationRequests; i++) {
                    // Try to allocate more subnets
                    ComputeNetworkCIDRAllocationRequest request =
                            allocationRequest("/dummy-subnet" + i);

                    ComputeNetworkCIDRAllocationState allocation =
                            doPatch(request, ComputeNetworkCIDRAllocationState.class,
                                    cidrAllocationLink);
                    assertEquals(SUBNET_CIDRS[i],
                            allocation.allocatedCIDRs.get(request.subnetId));
                }
            }
        }
    }

    public static class MiscServiceTest extends ComputeNetworkCIDRAllocationBaseTest {
        @Rule
        public ExpectedException expectedException = ExpectedException.none();

        @Test
        public void testCreateComputeNetworkCIDRAllocationService() throws Throwable {
            verifyService(
                    FactoryService.create(ComputeNetworkCIDRAllocationService.class),
                    ComputeNetworkCIDRAllocationState.class,
                    (prefix, index) -> {
                        ComputeNetworkCIDRAllocationState networkCIDRAllocation =
                                new ComputeNetworkCIDRAllocationState();

                        networkCIDRAllocation.networkLink = NETWORK_LINK;
                        networkCIDRAllocation.subnetCIDRPrefixLength = SUBNET_CIDR_PREFIX_LENGTH;

                        return networkCIDRAllocation;
                    },
                    (prefix, serviceDocument) -> {
                        ComputeNetworkCIDRAllocationState networkCIDRAllocation =
                                (ComputeNetworkCIDRAllocationState) serviceDocument;

                        assertEquals(NETWORK_LINK, networkCIDRAllocation.networkLink);
                        assertNotNull(networkCIDRAllocation.allocatedCIDRs);
                        assertEquals(0, networkCIDRAllocation.allocatedCIDRs.size());
                    });
        }

        @Test
        public void testChangeCIDRPrefixLength_NoAllocatedSubnets() throws Throwable {
            String cidrAllocationLink = this.createNetworkCIDRAllocationState();

            ComputeNetworkCIDRAllocationState state = new ComputeNetworkCIDRAllocationState();
            state.subnetCIDRPrefixLength = SUBNET_CIDR_PREFIX_LENGTH - 4;

            ComputeNetworkCIDRAllocationState updatedState = doPatch(state,
                    ComputeNetworkCIDRAllocationState.class, cidrAllocationLink);

            assertEquals(state.subnetCIDRPrefixLength, updatedState.subnetCIDRPrefixLength);
        }

        @Test
        public void testChangeCIDRPrefixLength_WithAllocatedSubnets() throws Throwable {
            this.expectedException.expectCause(isA(IllegalStateException.class));

            String cidrAllocationLink = this.createNetworkCIDRAllocationState();

            // Allocate 1 subnet
            ComputeNetworkCIDRAllocationRequest request = allocationRequest("/dummySubnet");

            ComputeNetworkCIDRAllocationState allocation =
                    doPatch(request, ComputeNetworkCIDRAllocationState.class,
                            cidrAllocationLink);

            // Try to change the CIDR prefix length.
            ComputeNetworkCIDRAllocationState state = new ComputeNetworkCIDRAllocationState();
            state.subnetCIDRPrefixLength = SUBNET_CIDR_PREFIX_LENGTH - 4;

            doPatch(state, ComputeNetworkCIDRAllocationState.class, cidrAllocationLink);
        }

    }

    public static class ConcurrentTest extends ComputeNetworkCIDRAllocationBaseTest {
        private static final int CONCURRENT_REQUESTS_COUNT = 4;

        /**
         * Test that CIDRs are uniquely allocated in case of concurrent allocation requests.
         */
        @Test
        public void testCIDRAllocationConcurrency() throws Throwable {
            String cidrAllocationLink = createNetworkCIDRAllocationState();

            List<DeferredResult<ComputeNetworkCIDRAllocationState>> allocationRequests = new
                    ArrayList<>();

            // Send multiple concurrent allocation requests.
            for (int i = 0; i < CONCURRENT_REQUESTS_COUNT; i++) {

                ComputeNetworkCIDRAllocationRequest request = allocationRequest("subnet" + i);

                allocationRequests.add(
                        host.sendWithDeferredResult(
                                Operation.createPatch(host, cidrAllocationLink)
                                        .setBody(request),
                                ComputeNetworkCIDRAllocationState.class));
            }

            // Assert that all allocation requests got a unique result value.
            DeferredResult.allOf(allocationRequests)
                    .whenComplete((allocations, throwable) -> {
                        assertNull(throwable);
                        assertNotNull(allocations);

                        long distinctAllocatedCIDRs = allocations.stream()
                                .map(allocation -> allocation.allocatedCIDRs.values())
                                .distinct()
                                .count();

                        assertEquals("Not all allocated CIDRs are distinct.",
                                CONCURRENT_REQUESTS_COUNT, distinctAllocatedCIDRs);
                    });
        }
    }
}