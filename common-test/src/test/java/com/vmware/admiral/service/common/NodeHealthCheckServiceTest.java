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

package com.vmware.admiral.service.common;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class NodeHealthCheckServiceTest extends BaseTestCase {

    private Set<String> unavailableServicesPaths = new HashSet<>(
            Arrays.asList("dummy-test-service"));

    private Set<String> availableServicesPaths = new HashSet<>();

    private int RETRIES_COUNT = 4;

    private long TIME_BETWEEN_RETIES_IN_MS = 3000;

    @Before
    public void setUp() throws Throwable {
        host.startServiceAndWait(ConfigurationFactoryService.class,
                ConfigurationFactoryService.SELF_LINK);

        host.startServiceAndWait(NodeHealthCheckService.class, NodeHealthCheckService.SELF_LINK);

        availableServicesPaths.addAll(Arrays.asList(ConfigurationFactoryService.SELF_LINK));
    }

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    @Test
    public void testHealthCheckPositiveScenario() {

        registerServicesForMonitoring(availableServicesPaths);

        this.host.testStart(1);

        Operation get = Operation.createGet(UriUtils.buildUri(host.getUri(),
                NodeHealthCheckService.SELF_LINK));

        retryHealthCheck(RETRIES_COUNT, get);

        this.host.testWait();
    }

    private void retryHealthCheck(int retries, Operation get) {

        get.setCompletion((o, e) -> {
            if (e != null) {
                if (retries == 0) {
                    this.host.failIteration(e);
                } else {
                    host.schedule(() -> {
                        retryHealthCheck(retries - 1, get);
                    }, TIME_BETWEEN_RETIES_IN_MS, TimeUnit.MILLISECONDS);
                }
                return;
            }

            this.host.completeIteration();
        });

        this.host.send(get);
    }

    @Test
    public void testHealthCheckForNotStartedServices() {

        registerServicesForMonitoring(unavailableServicesPaths);

        this.host.testStart(1);

        Operation get = Operation.createGet(UriUtils.buildUri(host.getUri(),
                NodeHealthCheckService.SELF_LINK));

        get.setCompletion((o, e) -> {
            if (e != null) {
                unavailableServicesPaths.forEach(service -> {
                    try {
                        Assert.assertTrue(e.getMessage().contains(
                                String.format("Unavailable services: %s", service)));
                    } catch (Throwable t) {
                        this.host.failIteration(t);
                    }
                });
                this.host.completeIteration();
                return;
            }
            this.host.failIteration(e);
        });

        this.host.send(get);
        this.host.testWait();
    }

    private void registerServicesForMonitoring(Set<String> services) {

        NodeHealthCheckService healthCheck = new NodeHealthCheckService();
        healthCheck.setSelfLink(NodeHealthCheckService.SELF_LINK);
        healthCheck.services = services;

        this.host.testStart(1);
        Operation patch = Operation.createPatch(UriUtils.buildUri(host.getUri(),
                NodeHealthCheckService.SELF_LINK));
        patch.setBody(healthCheck);
        patch.setCompletion((o, e) -> {
            if (e != null) {
                this.host.failIteration(e);
                return;
            }

            this.host.completeIteration();
        });
        this.host.send(patch);
        this.host.testWait();
    }

}
