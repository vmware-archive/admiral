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

package com.vmware.admiral.test.ui.pages;

import static com.codeborne.selenide.Selenide.close;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
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

    private final String PROPERTIES = "vic-ui-test.properties";
    private final String BROWSER_PROPERTY = "browser";
    private final String WAIT_FOR_ELEMENT_TIMEOUT = "locate.element.timeout.miliseconds";
    private final String BROWSER_CLOSE_TIMEOUT = "browser.close.timeout.miliseconds";
    private final String POLLING_INTERVAL = "polling.interval.miliseconds";
    private final String SCREENSHOTS_FOLDER = "screenshots.folder";
    private final String USE_BROWSER_ANNOTATIONS = "use.run.with.browser.annotaions";

    private String lastBrowser = null;

    public SelenideClassRunner(Class<?> klass) throws InitializationError {
        super(klass);
        loadProperties(PROPERTIES);
        Configuration.timeout = Integer.parseInt(getProperty(WAIT_FOR_ELEMENT_TIMEOUT));
        Configuration.closeBrowserTimeoutMs = Integer.parseInt(getProperty(BROWSER_CLOSE_TIMEOUT));
        Configuration.browser = getProperty(BROWSER_PROPERTY);
        Configuration.pollingInterval = Integer.parseInt(getProperty(POLLING_INTERVAL));
        Configuration.reportsFolder = getProperty(SCREENSHOTS_FOLDER);
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
            } else if (!Objects.isNull(supportedBrowsers)) {
                throw new UnsupportedOperationException(
                        "@RunWithBrowser and @SupportedBrowsers cannot be used together");
            } else if (Boolean.valueOf(getProperty(USE_BROWSER_ANNOTATIONS))) {
                return describeWithBrowsers(method, runWithBrowser);
            }
        } else if (!Objects.isNull(supportedBrowsers)) {
            if (supportedBrowsers.value().length == 0) {
                throw new IllegalArgumentException(
                        "Must specify at least one browser in @RunWithBrowsers");
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
        if (!browser.toString().equalsIgnoreCase(getProperty(BROWSER_PROPERTY))) {
            return Description.createTestDescription(getTestClass().getJavaClass(),
                    "[" + browser.toString() + "]" + testName(method));
        }
        return super.describeChild(method);
    }

    private Description describeWithSupportedBrowsers(FrameworkMethod method,
            SupportedBrowsers supportedBrowsers) {
        List<String> browsers = Arrays.asList(supportedBrowsers.value()).stream()
                .map(b -> b.toString().toLowerCase()).collect(Collectors.toList());
        if (!browsers.contains(getProperty(BROWSER_PROPERTY))) {
            return Description.createTestDescription(getTestClass().getJavaClass(),
                    UNSUPPORTED_BROWSER_PREFIX + testName(method));
        }
        return super.describeChild(method);
    }

    @Override
    protected void runChild(final FrameworkMethod method, RunNotifier notifier) {
        Description description = describeChild(method);
        if (!Objects.isNull(method.getAnnotation(RunWithBrowsers.class))) {
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
            if (!Objects.isNull(lastBrowser) && !lastBrowser.equalsIgnoreCase(browser)) {
                close();
            }
            lastBrowser = browser;
            Configuration.browser = browser;
            runLeaf(statement, desc, notifier);
            Configuration.browser = getProperty(BROWSER_PROPERTY);
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
        Configuration.browser = getProperty(BROWSER_PROPERTY);
    }

    private static void loadProperties(String propertiesFile) {
        File file = new File(propertiesFile);
        String charsetName = "UTF-8";
        Properties properties = new Properties();
        try {
            InputStreamReader isr;
            if (file.exists()) {
                isr = new InputStreamReader(new FileInputStream(propertiesFile), charsetName);
            } else {
                isr = new InputStreamReader(
                        SelenideClassRunner.class.getResourceAsStream("/" + propertiesFile),
                        charsetName);
            }
            properties.load(new BufferedReader(isr));
        } catch (final NullPointerException | IOException e) {
            throw new RuntimeException("Error while reading file " + propertiesFile, e);
        }
        for (Entry<Object, Object> propertyPair : properties.entrySet()) {
            System.setProperty((String) propertyPair.getKey(), (String) propertyPair.getValue());
        }
    }

    private static String getProperty(String key) {
        String prop = System.getProperty(key);
        if (Objects.isNull(prop)) {
            throw new RuntimeException(String.format("Property with key [%s] not found", key));
        }
        return prop;
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
