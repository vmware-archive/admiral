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

package com.vmware.admiral.common.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.logging.Logger;

import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/**
 * Helper Runner to run a whole test case multiple times. It will run the same test case multiple
 * times, everytime running the normal sequence of @BeforeClass, @Before, testMethod,  @After and @AfterClass
 * until some test fails. Useful for tracking down intermittent issues.
 * <br>
 * Optionally you can provide maximum number of runs without a fail after which the runner will
 * stop. You can do that by adding MaxRuns annotation over your test class.
 *
 * <br><br>
 * Example:
 *
 * <pre>
 * <code>
 * {@literal @}RunWith(RunUntilFailure.class)
 * {@literal @}RunUntilFailureMaxRuns(10)
 * public class HostAdapterServiceTest extends BaseTestCase {
 *      // ...
 * }
 * </code>
 * </pre>
 *
 */
public class RunUntilFailure extends BlockJUnit4ClassRunner {

    private final Logger logger;
    private final int maxRuns;

    public RunUntilFailure(Class<?> clazz) throws InitializationError {
        super(clazz);
        RunUntilFailureMaxRuns maxRunsAnnotation = clazz
                .getAnnotation(RunUntilFailureMaxRuns.class);
        this.maxRuns = maxRunsAnnotation != null ? maxRunsAnnotation.value() : 0;
        this.logger = Logger.getLogger(clazz.getName());
    }

    @Override
    public void run(RunNotifier notifier) {
        class L extends RunListener {
            boolean fail = false;

            public void testFailure(Failure failure) throws Exception {
                fail = true;
            }
        }

        int iteration = 0;
        L listener = new L();
        notifier.addListener(listener);
        while (!listener.fail && (maxRuns < 1 || iteration < maxRuns)) {
            iteration++;
            logger.info("Starting test iteration " + iteration);
            super.run(notifier);
            logger.info("Completed test iteration " + iteration);
        }

        if (!listener.fail) {
            logger.info("Max runs [" + maxRuns + "] reached. Stop run.");
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE })
    public @interface RunUntilFailureMaxRuns {
        int value() default 1;
    }

}