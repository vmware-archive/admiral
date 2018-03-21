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

import java.awt.Dimension;
import java.awt.Point;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;

public abstract class BasicClass<L extends PageLocators> {

    private final int WAIT_FOR_MOVING_ELEMENT_CHECK_INTERVAL_MILISECONDS = 150;

    protected final Logger LOG = Logger.getLogger(getClass().getName());
    private final PageActions pageActions;
    private final By[] iframeLocators;
    private final L pageLocators;

    public BasicClass(By[] iframeLocators, L pageLocators) {
        if (Objects.nonNull(iframeLocators)) {
            this.iframeLocators = iframeLocators.clone();
        } else {
            this.iframeLocators = null;
        }
        this.pageLocators = pageLocators;
        this.pageActions = new PageActions(this.iframeLocators);
    }

    protected PageActions pageActions() {
        return pageActions;
    }

    protected CheckCondition element(By selector) {
        return new CheckCondition(selector, getFrameLocators());
    }

    protected By[] getFrameLocators() {
        return iframeLocators;
    }

    protected L locators() {
        return pageLocators;
    }

    protected void waitForSpinner() {
        pageActions().waitForElementToAppearAndDisappear(locators().spinner());
    }

    protected void waitForElementToSettle(By locator) {
        final int TOTAL_COUNT = 3;
        AtomicInteger count = new AtomicInteger(TOTAL_COUNT);
        Wait().pollingEvery(100, TimeUnit.MILLISECONDS)
                .withTimeout(10, TimeUnit.SECONDS)
                .ignoring(StaleElementReferenceException.class)
                .until((f) -> {
                    Point initialPos = pageActions().getCoordinates(locator);
                    Dimension initialSize = pageActions().getDimension(locator);
                    try {
                        Thread.sleep(WAIT_FOR_MOVING_ELEMENT_CHECK_INTERVAL_MILISECONDS);
                    } catch (InterruptedException e) {
                    }
                    if (pageActions().getCoordinates(locator).equals(initialPos)
                            && pageActions().getDimension(locator).equals(initialSize)) {
                        if (count.decrementAndGet() == 0) {
                            return true;
                        }
                    } else {
                        count.set(TOTAL_COUNT);
                    }
                    return false;
                });
    }

}
