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

    private static final String FINISHED_STAGE = "FINISHED";
    private static final String FAILED_STAGE = "FAILED";
    private static final String COMPLETED_SUBSTAGE = "(COMPLETED)";

    public RequestsToolbar(By[] iFrameLocators, RequestsToolbarLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

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
        Wait().withTimeout(Duration.ofSeconds(timeout))
                .pollingEvery(Duration.ofSeconds(1))
                .until(f -> pageActions()
                        .getAttribute("aria-valuenow", locators().lastRequestProgress())
                        .equals("100")
                        || pageActions().getText(locators().lastRequestStatus())
                                .startsWith("FAILED"));
        String fullStatus = pageActions().getText(locators().lastRequestStatus());
        String stage = fullStatus.split(" ")[0];
        String substage = fullStatus.split(" ")[1];
        if (!stage.equals(FINISHED_STAGE) && !stage.equals(FAILED_STAGE)) {
            throw new AssertionError(
                    "Unexpected request stage: " + stage);
        }
        if (shouldSucceed) {
            if (stage.equals(FAILED_STAGE)) {
                throw new AssertionError(
                        "Last request failed, but was expected to succeed");
            }

            if (!substage.equals(COMPLETED_SUBSTAGE)) {
                // TODO uncomment after https://jira.eng.vmware.com/browse/VBV-1757 has been fixed
                // throw new AssertionError(
                // "Last request succeeded but did not reach (COMPLETED) substage");
                LOG.warning("Last request succeeded but did not reach (COMPLETED) substage");
            }
        } else {
            if (stage.equals(FINISHED_STAGE)) {
                throw new AssertionError(
                        "Last request succeeded, but was expected to fail");
            }
        }
    }

    public void clickRequestsButton() {
        waitForElementToSettle(locators().requestsButton());
        pageActions().click(locators().requestsButton());
    }

}
