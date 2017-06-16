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
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerDescriptionService;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerDescriptionService.ContainerLoadBalancerDescription;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerFrontendDescription;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerHealthConfig;
import com.vmware.admiral.compute.container.loadbalancers.ContainerLoadBalancerDescriptionServiceTest.ConstructorTest;
import com.vmware.admiral.compute.container.loadbalancers.ContainerLoadBalancerDescriptionServiceTest.HandlePatchTest;
import com.vmware.admiral.compute.container.loadbalancers.ContainerLoadBalancerDescriptionServiceTest.HandleStartTest;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link ContainerLoadBalancerDescription} class.
 */
@RunWith(ContainerLoadBalancerDescriptionServiceTest.class)
@Suite.SuiteClasses({ ConstructorTest.class, HandleStartTest.class, HandlePatchTest.class })
public class ContainerLoadBalancerDescriptionServiceTest extends Suite {

    public static class ConstructorTest {
        private ContainerLoadBalancerDescriptionService loadBalancerDescriptionService;

        @Before
        public void setupTest() {
            this.loadBalancerDescriptionService = new ContainerLoadBalancerDescriptionService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION);
            assertThat(this.loadBalancerDescriptionService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends ComputeBaseTest {
        @Test
        public void testValidDescription() throws Throwable {
            ContainerLoadBalancerDescription description = createValidDescription();
            ContainerLoadBalancerDescription result = doPost(description,
                    ContainerLoadBalancerDescriptionService.FACTORY_LINK);

            assertNotNull(result);
            assertThat(result.id, is(description.id));
            assertThat(result.name, is(description.name));
            assertThat(result.frontends.size(), is(description.frontends.size()));
            assertThat(result.networks.get(0), is(description.networks.get(0)));
            assertThat(result.tenantLinks.get(0),
                    is(description.tenantLinks.get(0)));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            ContainerLoadBalancerDescription description = createValidDescription();
            ContainerLoadBalancerDescription result = doPost(description,
                    ContainerLoadBalancerDescriptionService.FACTORY_LINK);

            assertNotNull(result);
            assertThat(description.name, is(result.name));
            description.name = "new-name";
            result = doPost(description, ContainerLoadBalancerDescriptionService.FACTORY_LINK);
            assertThat(description.name, is(result.name));
        }

        @Test(expected = java.lang.IllegalArgumentException.class)
        public void testInvalidValues() throws Throwable {
            ContainerLoadBalancerDescription missingFrontends = createValidDescription();
            ContainerLoadBalancerDescription missingBackends = createValidDescription();
            ContainerLoadBalancerDescription missingLinks = createValidDescription();
            ContainerLoadBalancerDescription invalidLinks = createValidDescription();
            ContainerLoadBalancerDescription invalidFrontendPort = createValidDescription();
            ContainerLoadBalancerDescription invalidBackendPort = createValidDescription();

            missingFrontends.frontends = null;
            missingBackends.frontends.get(0).backends = null;
            missingLinks.links = null;
            invalidLinks.links = new String[] { "no-link-in-backends" };
            invalidFrontendPort.frontends.get(0).port = ContainerLoadBalancerDescriptionService
                    .MIN_PORT_NUMBER - 1;
            invalidBackendPort.frontends.get(0).backends.get(0).port =
                    ContainerLoadBalancerDescriptionService
                            .MAX_PORT_NUMBER + 1;

            ContainerLoadBalancerDescription[] states = { missingFrontends, missingBackends,
                    invalidFrontendPort, invalidBackendPort };
            for (ContainerLoadBalancerDescription state : states) {
                doPost(state, UriUtils.buildUri(host, ContainerLoadBalancerDescriptionService
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
            ContainerLoadBalancerDescription description = doPost(createValidDescription(),
                    ContainerLoadBalancerDescriptionService.FACTORY_LINK);

            ContainerLoadBalancerDescription patched = new ContainerLoadBalancerDescription();
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
            frontend.healthConfig.path = "/test_patch";
            frontend.healthConfig.protocol = "http";
            patched.frontends.add(frontend);
            patched.networks = new ArrayList<>();
            ServiceNetwork network = new ServiceNetwork();
            network.name = "patch_test_network";
            patched.networks.add(network);
            patched.tenantLinks = new ArrayList<>();
            patched.tenantLinks.add("patch_tenant-linkA");
            patched.links = new String[] { "service-patch" };
            doPatch(patched, description.documentSelfLink);
            ContainerLoadBalancerDescription result = getDocument
                    (ContainerLoadBalancerDescription.class, description.documentSelfLink);

            assertThat(result.name, is(patched.name));
            assertThat(result.frontends.get(0), is(patched.frontends.get(0)));
            assertThat(result.networks.get(0), is(patched.networks.get(0)));
            assertThat(result.tenantLinks.get(0),
                    is(patched.tenantLinks.get(0)));
        }
    }

    public ContainerLoadBalancerDescriptionServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    public static ContainerLoadBalancerDescription createValidDescription() {
        ContainerLoadBalancerDescription description = new ContainerLoadBalancerDescription();
        description.id = UUID.randomUUID().toString();
        description.frontends = new ArrayList<>();
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
        description.frontends.add(frontend);
        description.networks = new ArrayList<>();
        ServiceNetwork network = new ServiceNetwork();
        network.name = "test_network";
        description.networks.add(network);
        description.tenantLinks = new ArrayList<>();
        description.tenantLinks.add("tenant-linkA");
        description.links = new String[] { "service" };
        return description;
    }
}
