/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages.templates.create;

import static com.codeborne.selenide.Selenide.Wait;

import java.time.Duration;

import com.codeborne.selenide.Condition;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class SelectImagePage extends BasicPage<SelectImagePageValidator, SelectImagePageLocators> {

    public SelectImagePage(By[] iFrameLocators, SelectImagePageValidator validator,
            SelectImagePageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        waitForElementToSettle(locators().childPageSlide());
    }

    public void searchForImage(String image) {
        LOG.info(String.format("Searching for image [%s]", image));
        pageActions().clear(locators().searchImageInput());
        pageActions().sendKeys(image + "\n", locators().searchImageInput());
        waitForSpinner();
    }

    public void selectImageByName(String name) {
        LOG.info(String.format("Selecting image [%s]", name));
        int retries = 3;
        while (retries > 0) {
            pageActions().click(locators().selectImageButtonByName(name));
            try {
                Wait().withTimeout(Duration.ofSeconds(3))
                        .until(d -> element(locators().searchImageInput()).is(Condition.hidden));
                return;
            } catch (TimeoutException e) {
                retries--;
            }
        }
        throw new RuntimeException("Could not select image...");
    }

}
