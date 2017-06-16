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

package com.vmware.admiral.compute.container.loadbalancers;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerBackendDescription;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerDescriptionService.ContainerLoadBalancerDescription;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerFrontendDescription;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerHealthConfig;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerService;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerService.ContainerLoadBalancerState;
import com.vmware.admiral.compute.container.loadbalancers.ContainerLoadBalancerDescriptionServiceTest.ConstructorTest;
import com.vmware.admiral.compute.container.loadbalancers.ContainerLoadBalancerDescriptionServiceTest.HandlePatchTest;
import com.vmware.admiral.compute.container.loadbalancers.ContainerLoadBalancerDescriptionServiceTest.HandleStartTest;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link ContainerLoadBalancerDescription} class.
 */
@RunWith(ContainerLoadBalancerServiceTest.class)
@Suite.SuiteClasses({ ConstructorTest.class, HandleStartTest.class, HandlePatchTest.class })
public class ContainerLoadBalancerServiceTest extends Suite {

    public static class ConstructorTest {
        private ContainerLoadBalancerService loadBalancerService;

        @Before
        public void setupTest() {
            this.loadBalancerService = new ContainerLoadBalancerService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION);
            assertThat(this.loadBalancerService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends ComputeBaseTest {
        @Test
        public void testValidDescription() throws Throwable {
            ContainerLoadBalancerState state = createValidState();
            ContainerLoadBalancerState result = doPost(state,
                    ContainerLoadBalancerService.FACTORY_LINK);

            assertNotNull(result);
            assertThat(result.id, is(state.id));
            assertThat(result.name, is(state.name));
            assertThat(result.frontends.size(), is(state.frontends.size()));
            assertThat(result.networks.get(0), is(state.networks.get(0)));
            assertThat(result.tenantLinks.get(0),
                    is(state.tenantLinks.get(0)));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            ContainerLoadBalancerState state = createValidState();
            ContainerLoadBalancerState result = doPost(state,
                    ContainerLoadBalancerService.FACTORY_LINK);

            assertNotNull(result);
            assertThat(result.name, is(state.name));
            state.name = "new-name";
            result = doPost(state, ContainerLoadBalancerService.FACTORY_LINK);
            assertThat(result.name, is(state.name));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testInvalidValues() throws Throwable {
            ContainerLoadBalancerState missingFrontends = createValidState();
            ContainerLoadBalancerState missingBackends = createValidState();
            ContainerLoadBalancerState invalidFrontendPort = createValidState();
            ContainerLoadBalancerState invalidBackendPort = createValidState();

            missingFrontends.frontends = null;
            missingBackends.frontends.get(0).backends = null;
            invalidFrontendPort.frontends.get(0).port = ContainerLoadBalancerService
                    .MIN_PORT_NUMBER - 1;
            invalidBackendPort.frontends.get(0).backends.get(0).port =
                    ContainerLoadBalancerService
                            .MAX_PORT_NUMBER + 1;

            ContainerLoadBalancerState[] states = { missingFrontends, missingBackends,
                    invalidFrontendPort, invalidBackendPort };
            for (ContainerLoadBalancerState state : states) {
                doPost(state, UriUtils.buildUri(host, ContainerLoadBalancerService
                        .FACTORY_LINK), true);
            }
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends ComputeBaseTest {
        @Test
        public void testPatch() throws Throwable {
            ContainerLoadBalancerState description = doPost(createValidState(),
                    ContainerLoadBalancerService.FACTORY_LINK);

            ContainerLoadBalancerState patched = new ContainerLoadBalancerState();
            patched.name = "patchedName";
            patched.frontends = new ArrayList<>();
            ContainerLoadBalancerFrontendDescription frontend = new ContainerLoadBalancerFrontendDescription();
            frontend.port = 8081;
            frontend.backends = new ArrayList<>();
            ContainerLoadBalancerBackendDescription backend = new ContainerLoadBalancerBackendDescription();
            backend.service = "service-patch";
            backend.port = 900;
            frontend.backends.add(backend);
            frontend.healthConfig = new ContainerLoadBalancerHealthConfig();
            frontend.healthConfig.path = "/test-patch";
            frontend.healthConfig.protocol = "http";
            patched.frontends.add(frontend);
            patched.networks = new ArrayList<ServiceNetwork>();
            ServiceNetwork network = new ServiceNetwork();
            network.name = "patch_test_network";
            patched.networks.add(network);
            patched.tenantLinks = new ArrayList<>();
            patched.tenantLinks.add("patch_tenant-linkA");
            patched.links = new String[] { "service-patch" };
            doPatch(patched, description.documentSelfLink);
            ContainerLoadBalancerState result = getDocument
                    (ContainerLoadBalancerState.class, description.documentSelfLink);

            assertThat(result.name, is(patched.name));
            assertThat(result.frontends.get(0), is(patched.frontends.get(0)));
            assertThat(result.networks.get(0), is(patched.networks.get(0)));
            assertThat(result.tenantLinks.get(0),
                    is(patched.tenantLinks.get(0)));
        }
    }

    public ContainerLoadBalancerServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    public static ContainerLoadBalancerState createValidState() {
        ContainerLoadBalancerState state = new ContainerLoadBalancerState();
        state.id = UUID.randomUUID().toString();
        state.frontends = new ArrayList<>();
        ContainerLoadBalancerFrontendDescription frontend = new ContainerLoadBalancerFrontendDescription();
        frontend.port = 8080;
        frontend.backends = new ArrayList<>();
        ContainerLoadBalancerBackendDescription backend = new ContainerLoadBalancerBackendDescription();
        backend.service = "service";
        backend.port = 90;
        frontend.backends.add(backend);
        frontend.healthConfig = new ContainerLoadBalancerHealthConfig();
        frontend.healthConfig.path = "/test";
        frontend.healthConfig.protocol = "http";
        state.frontends.add(frontend);
        state.networks = new ArrayList<ServiceNetwork>();
        ServiceNetwork network = new ServiceNetwork();
        network.name = "test_network";
        state.networks.add(network);
        state.tenantLinks = new ArrayList<>();
        state.tenantLinks.add("tenant-linkA");
        state.links = new String[] { "service" };
        return state;
    }
}
