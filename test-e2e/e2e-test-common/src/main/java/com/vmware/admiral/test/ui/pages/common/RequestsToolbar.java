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

package com.vmware.admiral.test.ui.pages.common;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.Wait;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

import com.vmware.admiral.test.ui.pages.AdmiralWebClientConfiguration;

public class RequestsToolbar extends BasicClass {

    private final int WAIT_AFTER_REFRESH_ON_FAIL_SECONDS = 3;

    private final By RIGHT_PANEL = By.cssSelector(".right-context-panel .content .requests-list");
    private final By REQUESTS_BUTTON = By
            .cssSelector(".toolbar .toolbar-item:nth-child(1) .btn");
    private final By LAST_REQUEST = By
            .cssSelector(".requests-list #all .request-item-holder:first-child");
    private final By REFRESH_BUTTON = By.cssSelector(".right-context-panel .fa.fa-refresh");

    private static RequestsToolbar instance;

    private RequestsToolbar() {
    }

    public static RequestsToolbar getInstance() {
        if (Objects.isNull(instance)) {
            instance = new RequestsToolbar();
        }
        return instance;
    }

    public void waitForLastRequestToSucceed(int timeout) {
        LOG.info(
                String.format("Waiting for [%d] seconds for the last request to succeed", timeout));
        executeInFrame(0, () -> {
            waitForElementToStopMoving($(REQUESTS_BUTTON));
            try {
                waitToSucceed(timeout);
            } catch (TimeoutException e) {
                $(REFRESH_BUTTON).click();
                // TODO maybe should not be necessary?
                try {
                    waitToSucceed(WAIT_AFTER_REFRESH_ON_FAIL_SECONDS);
                } catch (TimeoutException e1) {
                    throw e;
                }
            }
        });
    }

    private void waitToSucceed(int timeout) {
        Wait().pollingEvery(AdmiralWebClientConfiguration.getRequestPollingIntervalMiliseconds(),
                TimeUnit.MILLISECONDS)
                .withTimeout(timeout, TimeUnit.SECONDS).until(f -> {
                    expandRequestsIfNotExpanded();
                    SelenideElement lastRequest = $(LAST_REQUEST);
                    return lastRequest.has(Condition.text("FINISHED"))
                            && lastRequest.has(Condition.text("completed"));
                });
    }

    private void expandRequestsIfNotExpanded() {
        if (!$(RIGHT_PANEL).isDisplayed()) {
            $(REQUESTS_BUTTON).click();
            waitForElementToStopMoving($(REQUESTS_BUTTON));
        }
    }

}
