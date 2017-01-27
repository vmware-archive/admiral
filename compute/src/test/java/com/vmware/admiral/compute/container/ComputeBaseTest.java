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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.host.CompositeComponentNotificationProcessingChain;
import com.vmware.admiral.host.ComputeInitialBootService;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.service.common.AbstractInitialBootService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public abstract class ComputeBaseTest extends BaseTestCase {

    protected static final Long MIN_MEMORY = 4_194_304L;

    private List<Runnable> staticFieldValuesResetRunners = new ArrayList<>();

    @Before
    public void beforeForComputeBase() throws Throwable {
        startServices(host);
        waitForServiceAvailability(ComputeInitialBootService.SELF_LINK);
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
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    @Override
    protected void customizeChains(
            Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains) {
        CompositeComponentNotificationProcessingChain.registerOperationProcessingChains(chains);
    }

    private static void startServices(ServiceHost serviceHost) throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
        HostInitPhotonModelServiceConfig.startServices(serviceHost);
        HostInitTestDcpServicesConfig.startServices(serviceHost);
        HostInitCommonServiceConfig.startServices(serviceHost);
        HostInitComputeServicesConfig.startServices(serviceHost, false);
    }

    protected void startInitialBootService(
            Class<? extends AbstractInitialBootService> serviceClass,
            String bootServiceSelfLink) throws Throwable {
        //simulate a restart of the service host
        host.startServiceAndWait(serviceClass, bootServiceSelfLink);

        TestContext ctx = testCreate(1);
        //start initialization of system documents
        host.sendRequest(Operation.createPost(
                UriUtils.buildUri(host, serviceClass))
                .setReferer(host.getUri())
                .setBody(new ServiceDocument())
                .setCompletion(ctx.getCompletion()));
        ctx.await();
    }

    protected void setFinalStatic(Class<?> clazz, String fieldName, Object newValue)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        Field modifiers = field.getClass().getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        Object oldValue = field.get(null);

        staticFieldValuesResetRunners.add(() -> {
            try {
                field.set(null, oldValue);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        field.set(null, newValue);
    }
}
