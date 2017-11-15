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

package com.vmware.admiral.test.ui;

import static com.codeborne.selenide.Selenide.close;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.codeborne.selenide.Configuration;

import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

public class SelenideClassRunner extends BlockJUnit4ClassRunner {

    private final String UNSUPPORTED_BROWSER_PREFIX = "[UNSUPPORTED BROWSER]";
    private final String BROWSER_PROPERTY = "browser";
    private final String DEFAULT_BROWSER = "chrome";
    private final String SYSTEM_DEFINED_BROWSER;
    private final String USE_RUN_WITH_BROWSER_ANNOTATIONS_PROPERTY = "use.run.with.browser.annotations";
    private final boolean USE_RUN_WITH_BROWSER_ANNOTATIONS;
    private final boolean USE_RUN_WITH_BROWSER_ANNOTATIONS_DEFAULT = true;

    private String lastBrowser = null;

    public SelenideClassRunner(Class<?> klass) throws InitializationError {
        super(klass);
        String browser = System.getProperty(BROWSER_PROPERTY);
        if (!Objects.isNull(browser)) {
            SYSTEM_DEFINED_BROWSER = browser;
        } else {
            SYSTEM_DEFINED_BROWSER = DEFAULT_BROWSER;
        }
        Configuration.browser = SYSTEM_DEFINED_BROWSER;
        String runWithBrowserValue = System.getProperty(USE_RUN_WITH_BROWSER_ANNOTATIONS_PROPERTY);
        if (!Objects.isNull(runWithBrowserValue)) {
            if (runWithBrowserValue.equalsIgnoreCase("false")) {
                USE_RUN_WITH_BROWSER_ANNOTATIONS = false;
            } else if (runWithBrowserValue.equalsIgnoreCase("true")) {
                USE_RUN_WITH_BROWSER_ANNOTATIONS = true;
            } else {
                USE_RUN_WITH_BROWSER_ANNOTATIONS = USE_RUN_WITH_BROWSER_ANNOTATIONS_DEFAULT;
            }
        } else {
            USE_RUN_WITH_BROWSER_ANNOTATIONS = USE_RUN_WITH_BROWSER_ANNOTATIONS_DEFAULT;
        }
    }

    @Override
    protected Description describeChild(FrameworkMethod method) {
        if (isIgnored(method)) {
            return super.describeChild(method);
        }
        RunWithBrowsers runWithBrowser = method.getAnnotation(RunWithBrowsers.class);
        SupportedBrowsers supportedBrowsers = method.getAnnotation(SupportedBrowsers.class);
        if (!Objects.isNull(runWithBrowser)) {
            if (runWithBrowser.value().length == 0) {
                throw new IllegalArgumentException(
                        "Must specify at least one browser in @RunWithBrowsers");
            }
            if (!Objects.isNull(supportedBrowsers)) {
                throw new UnsupportedOperationException(
                        "@RunWithBrowser and @SupportedBrowsers cannot be used together");
            }
            if (USE_RUN_WITH_BROWSER_ANNOTATIONS == false) {
                return super.describeChild(method);
            }
            if (USE_RUN_WITH_BROWSER_ANNOTATIONS_DEFAULT) {
                return describeWithBrowsers(method, runWithBrowser);
            }
        } else if (!Objects.isNull(supportedBrowsers)) {
            if (supportedBrowsers.value().length == 0) {
                throw new IllegalArgumentException(
                        "Must specify at least one browser in @SupportedBrowsers");
            }
            return describeWithSupportedBrowsers(method, supportedBrowsers);
        }
        return super.describeChild(method);
    }

    private Description describeWithBrowsers(FrameworkMethod method,
            RunWithBrowsers runWithBrowser) {
        List<Browser> browsers = new ArrayList<>();
        browsers.addAll(new HashSet<>(Arrays.asList(runWithBrowser.value())));
        if (browsers.size() == 1) {
            return describeWithOneBrowser(method, runWithBrowser, browsers.get(0));
        }
        Description description = Description.createSuiteDescription(
                testName(method), method.getAnnotations());
        for (Browser browser : browsers) {
            description.addChild(Description.createTestDescription(
                    getTestClass().getJavaClass(), "[" + browser.toString() + "]"
                            + testName(method)));
        }
        return description;
    }

    private Description describeWithOneBrowser(FrameworkMethod method,
            RunWithBrowsers runWithBrowser, Browser browser) {
        if (!browser.toString().equalsIgnoreCase(SYSTEM_DEFINED_BROWSER)) {
            return Description.createTestDescription(getTestClass().getJavaClass(),
                    "[" + browser.toString() + "]" + testName(method));
        }
        return super.describeChild(method);
    }

    private Description describeWithSupportedBrowsers(FrameworkMethod method,
            SupportedBrowsers supportedBrowsers) {
        List<String> browsers = Arrays.asList(supportedBrowsers.value()).stream()
                .map(b -> b.toString().toLowerCase()).collect(Collectors.toList());
        if (!browsers.contains(SYSTEM_DEFINED_BROWSER)) {
            return Description.createTestDescription(getTestClass().getJavaClass(),
                    UNSUPPORTED_BROWSER_PREFIX + testName(method));
        }
        return super.describeChild(method);
    }

    @Override
    protected void runChild(final FrameworkMethod method, RunNotifier notifier) {
        Description description = describeChild(method);
        if (!Objects.isNull(method.getAnnotation(RunWithBrowsers.class))
                && USE_RUN_WITH_BROWSER_ANNOTATIONS) {
            if (description.getChildren().isEmpty()) {
                String browser = method.getAnnotation(RunWithBrowsers.class).value()[0].toString()
                        .toLowerCase();
                runMethod(methodBlock(method), description, notifier, browser);
                return;
            }
            runWithBrowsers(methodBlock(method), description, notifier);
            return;
        }
        if (isIgnored(method)
                || description.getMethodName().startsWith(UNSUPPORTED_BROWSER_PREFIX)) {
            notifier.fireTestIgnored(description);
            return;
        }
        super.runChild(method, notifier);
    }

    private void runWithBrowsers(Statement statement, Description description,
            RunNotifier notifier) {
        for (Description desc : description.getChildren()) {
            String browser = desc.getMethodName().substring(1,
                    desc.getMethodName().lastIndexOf(']')).toLowerCase();
            runMethod(statement, desc, notifier, browser);
        }
    }

    private void runMethod(Statement statement, Description description,
            RunNotifier notifier, String browser) {
        if (!Objects.isNull(lastBrowser) && !lastBrowser.equalsIgnoreCase(browser)) {
            close();
        }
        lastBrowser = browser;
        Configuration.browser = browser;
        runLeaf(statement, description, notifier);
        Configuration.browser = SYSTEM_DEFINED_BROWSER;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public static @interface RunWithBrowsers {
        public Browser[] value();

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public static @interface SupportedBrowsers {
        public Browser[] value();

    }

    public static enum Browser {
        FIREFOX, CHROME;
    }
}
