/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.xenon.rdbms.test;

import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.ExampleService;
import com.vmware.xenon.services.common.TaskService;

public class PostgresBasicReusableHostTestCase extends BasicReusableHostTestCase {

    private static final int MAINTENANCE_INTERVAL_MILLIS = 250;

    private static VerificationHost HOST;

    @BeforeClass
    public static void setUpOnce() throws Exception {
        startHost(false);
    }

    private static void startHost(boolean enableAuth) throws Exception {
        HOST = PostgresVerificationHost.create(0);
        HOST.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS
                .toMicros(MAINTENANCE_INTERVAL_MILLIS));
        CommandLineArgumentParser.parseFromProperties(HOST);
        HOST.setStressTest(HOST.isStressTest);
        HOST.setAuthorizationEnabled(enableAuth);
        try {
            HOST.start();
            HOST.waitForServiceAvailable(ExampleService.FACTORY_LINK);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @Before
    public void setUpPerMethod() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);
        this.host = HOST;
        this.host.toggleDebuggingMode(this.detailedLogging);
        this.sender = this.host.getTestRequestSender();
        if (HOST.isStressTest()) {
            Utils.setTimeDriftThreshold(TimeUnit.SECONDS.toMicros(120));
        }
        if (this.enableAuth) {

            if (!this.host.isAuthorizationEnabled()) {
                this.host.log("Restarting host to enable authorization");
                tearDownOnce();
                startHost(true);
                this.host = HOST;
            }

            this.host.log("Auth is enabled. Creating users");
            setUpAuthUsers();
            switchToAuthUser();
        }
    }

    protected TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            HOST.log("Running test: " + description.getMethodName());
        }
    };

    @Rule
    public TestRule chain = RuleChain.outerRule(this.watcher);

    @AfterClass
    public static void tearDownOnce() {
        HOST.tearDownInProcessPeers();
        HOST.tearDown();
        Utils.setTimeDriftThreshold(Utils.DEFAULT_TIME_DRIFT_THRESHOLD_MICROS);
    }

    @After
    public void tearDownPerMethod() {
        if (this.enableAuth) {
            clearAuthorization();
        }
    }

    protected void clearAuthorization() {
        this.host.resetAuthorizationContext();
    }

    /**
     * @see VerificationHost#getSafeHandler(CompletionHandler)
     * @param handler
     * @return
     */
    public static CompletionHandler getSafeHandler(CompletionHandler handler) {
        return HOST.getSafeHandler(handler);
    }

    /**
     * @see VerificationHost#sendFactoryPost(Class, ServiceDocument, Operation.CompletionHandler)
     */
    public static <T extends ServiceDocument> void sendFactoryPost(Class<? extends Service> service,
            T state, CompletionHandler handler) throws Throwable {
        HOST.sendFactoryPost(service, state, handler);
    }

    /** @see VerificationHost#getCompletionWithSelflink(String[]) */
    public static CompletionHandler getCompletionWithSelfLink(String[] storedLink) {
        return HOST.getCompletionWithSelflink(storedLink);
    }

    /** @see VerificationHost#getExpectedFailureCompletionReturningThrowable(Throwable[]) */
    public static CompletionHandler getExpectedFailureCompletionReturningThrowable(
            Throwable[] storeException) {
        return HOST.getExpectedFailureCompletionReturningThrowable(storeException);
    }

    /** @see VerificationHost#waitForFinishedTask(Class, String) */
    public static <T extends TaskService.TaskServiceState> T waitForFinishedTask(Class<T> type,
            String taskUri) throws Throwable {
        return HOST.waitForFinishedTask(type, taskUri);
    }

    /** @see VerificationHost#waitForFailedTask(Class, String) */
    public static <T extends TaskService.TaskServiceState> T waitForFailedTask(Class<T> type,
            String taskUri) throws Throwable {
        return HOST.waitForFailedTask(type, taskUri);
    }

    /** @see VerificationHost#waitForTask(Class, String, TaskState.TaskStage) */
    public static <T extends TaskService.TaskServiceState> T waitForTask(Class<T> type, String taskUri,
            TaskState.TaskStage expectedStage) throws Throwable {
        return HOST.waitForTask(type, taskUri, expectedStage);
    }

    /** @see VerificationHost#waitForTask(Class, String, TaskState.TaskStage, boolean) */
    public static <T extends TaskService.TaskServiceState> T waitForTask(Class<T> type, String taskUri,
            TaskState.TaskStage expectedStage, boolean useQueryTask) throws Throwable {
        return HOST.waitForTask(type, taskUri, expectedStage, useQueryTask);
    }
}
