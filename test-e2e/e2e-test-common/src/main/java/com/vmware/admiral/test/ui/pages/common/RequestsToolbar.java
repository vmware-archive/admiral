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

package com.vmware.admiral.test.ui.pages.common;

import static com.codeborne.selenide.Selenide.Wait;

import java.time.Duration;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

public class RequestsToolbar extends BasicClass<RequestsToolbarLocators> {

    public RequestsToolbar(By[] iFrameLocators, RequestsToolbarLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

    private final int WAIT_AFTER_REFRESH_ON_FAIL_SECONDS = 5;

    public void waitForLastRequestToSucceed(int timeout) {
        waitForLastRequestRequest(timeout, true);
    }

    public void waitForLastRequestToFail(int timeout) {
        waitForLastRequestRequest(timeout, false);
    }

    public void clickLastRequest() {
        LOG.info("Clicking on the last request");
        int retries = 3;
        while (retries > 0) {
            try {
                pageActions().click(locators().lastRequest());
                Wait().withTimeout(Duration.ofSeconds(3))
                        .until(d -> !element(locators().lastRequest()).is(Condition.visible));
                return;
            } catch (TimeoutException e) {
                LOG.info("Clicking on last the last request failed, retrying...");
                retries--;
            }
        }
        throw new RuntimeException("Could not click on the last request.");
    }

    private void waitForLastRequestRequest(int timeout, boolean shouldSucceed) {
        String expectedState = shouldSucceed ? "succeed" : "fail";
        LOG.info(
                String.format("Waiting for [%d] seconds for the last request to %s", timeout,
                        expectedState));
        waitForSpinner();
        try {
            if (shouldSucceed) {
                waitForLastToSucceed(timeout);
            } else {
                waitForLastToFail(timeout);
            }
        } catch (TimeoutException e) {
            // TODO maybe should not be necessary?
            LOG.warning(
                    "Timeout expired, refreshing requests to verify the request is not finished...");
            pageActions().click(locators().refreshButton());
            LOG.info(String.format(
                    "Waiting for additional [%s] seconds for the request to finish...",
                    WAIT_AFTER_REFRESH_ON_FAIL_SECONDS));
            try {
                if (shouldSucceed) {
                    waitForLastToSucceed(WAIT_AFTER_REFRESH_ON_FAIL_SECONDS);
                    LOG.info("Request has succeeded, proceeding...");
                } else {
                    waitForLastToFail(WAIT_AFTER_REFRESH_ON_FAIL_SECONDS);
                    LOG.info("Request has failed, proceeding...");
                }
            } catch (TimeoutException e1) {
                throw e;
            }
        }

    }

    private void waitForLastToSucceed(int timeout) {
        Wait().withTimeout(Duration.ofSeconds(timeout))
                .pollingEvery(Duration.ofSeconds(1))
                .until(f -> {
                    String text = pageActions().getText(locators().lastRequestProgress());
                    if (text.contains("FAILED")) {
                        throw new AssertionError(
                                "Last request failed, but was expected to succeed");
                    }
                    return text.contains("FINISHED")
                            && text.contains("COMPLETED");
                });
    }

    private void waitForLastToFail(int timeout) {
        Wait().withTimeout(Duration.ofSeconds(timeout))
                .pollingEvery(Duration.ofSeconds(1))
                .until(f -> {
                    String text = pageActions().getText(locators().lastRequestProgress());
                    if (text.contains("FINISHED") && text.contains("COMPLETED")) {
                        throw new AssertionError(
                                "Last request succeeded, but was expected to fail");
                    }
                    return text.contains("FAILED")
                            && text.contains("ERROR");
                });
    }

    public void clickRequestsButton() {
        waitForElementToSettle(locators().requestsButton());
        pageActions().click(locators().requestsButton());
    }

}
