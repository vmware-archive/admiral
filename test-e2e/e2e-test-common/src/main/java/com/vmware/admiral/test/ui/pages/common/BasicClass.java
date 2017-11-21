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
import static com.codeborne.selenide.Selenide.switchTo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import com.google.common.base.Supplier;

import org.openqa.selenium.By;
import org.openqa.selenium.Point;
import org.openqa.selenium.StaleElementReferenceException;

public class BasicClass {

    protected Logger LOG = Logger.getLogger(getClass().getName());

    private final int WAIT_FOR_MOVING_ELEMENT_CHECK_INTERVAL_MILISECONDS = 30;
    private final int WAIT_FOR_FLASHING_ELEMENT_MILISECONDS = 1;

    protected SelenideElement waitForElementToStopMoving(SelenideElement element) {
        final int TOTAL_COUNT = 2;
        AtomicInteger count = new AtomicInteger(TOTAL_COUNT);
        Wait().pollingEvery(1, TimeUnit.MILLISECONDS)
                .withTimeout(10, TimeUnit.SECONDS)
                .ignoring(StaleElementReferenceException.class)
                .until((f) -> {
                    Point initialPos = element.getCoordinates().inViewPort();
                    try {
                        Thread.sleep(WAIT_FOR_MOVING_ELEMENT_CHECK_INTERVAL_MILISECONDS);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(
                                "Waiting for element to stop moving was interrupted: ", e);
                    }
                    if (element.getCoordinates().inViewPort().equals(initialPos)) {
                        if (count.get() == 0) {
                            return true;
                        }
                        count.decrementAndGet();
                    } else {
                        if (count.get() < TOTAL_COUNT) {
                            count.set(TOTAL_COUNT);
                        }
                    }
                    return false;
                });
        return element;
    }

    protected void waitForElementToAppearAndDisappear(By element) {
        Wait().pollingEvery(WAIT_FOR_FLASHING_ELEMENT_MILISECONDS,
                TimeUnit.MILLISECONDS).until(d -> {
                    return $(element).is(Condition.visible);
                });
        Wait().pollingEvery(WAIT_FOR_FLASHING_ELEMENT_MILISECONDS,
                TimeUnit.MILLISECONDS).until(d -> {
                    return $(element).is(Condition.hidden);
                });
    }

    protected void executeInFrame(int frame, Runnable action) {
        switchTo().frame(frame);
        try {
            action.run();
        } finally {
            switchTo().defaultContent();
        }
    }

    protected <T> T executeInFrame(int frame, Supplier<T> action) {
        switchTo().frame(frame);
        try {
            return action.get();
        } finally {
            switchTo().defaultContent();
        }
    }
}
