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

package com.vmware.admiral.compute.container;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.host.CompositeComponentInterceptor;
import com.vmware.admiral.host.ComputeInitialBootService;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.host.interceptor.OperationInterceptorRegistry;
import com.vmware.admiral.service.common.AbstractInitialBootService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public abstract class ComputeBaseTest extends BaseTestCase {

    public static final String REGION_ID = "us-east-1";
    public static final List<String> TENANT_LINKS = Arrays.asList("Dev");
    protected static final Long MIN_MEMORY = 4_194_304L;

    private List<Runnable> staticFieldValuesResetRunners = new ArrayList<>();

    @Before
    public void beforeForComputeBase() throws Throwable {
        host.addPrivilegedService(ContainerHostDataCollectionService.class);
        startServices(host);
        waitForInitialBootServiceToBeSelfStopped(ComputeInitialBootService.SELF_LINK);
    }

    @After
    public void afterForComputeBase() {
        for (Runnable r : staticFieldValuesResetRunners) {
            r.run();
        }

        staticFieldValuesResetRunners.clear();
    }

    @Override
    protected void registerInterceptors(OperationInterceptorRegistry registry) {
        CompositeComponentInterceptor.register(registry);
    }

    private static void startServices(ServiceHost serviceHost) throws Throwable {
        HostInitPhotonModelServiceConfig.startServices(serviceHost);
        HostInitTestDcpServicesConfig.startServices(serviceHost);
        HostInitCommonServiceConfig.startServices(serviceHost);
        HostInitComputeServicesConfig.startServices(serviceHost, false);
    }

    protected void startInitialBootService(
            Class<? extends AbstractInitialBootService> serviceClass,
            String bootServiceSelfLink) throws Throwable {
        // simulate a restart of the service host
        host.startServiceAndWait(serviceClass, bootServiceSelfLink);

        TestContext ctx = testCreate(1);
        // start initialization of system documents
        host.sendRequest(Operation.createPost(
                UriUtils.buildUri(host, serviceClass))
                .setReferer(host.getUri())
                .setBody(new ServiceDocument())
                .setCompletion(ctx.getCompletion()));
        ctx.await();
    }
}
