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

package com.vmware.admiral.vic.test.ui.pages.internalrepos;

import static com.codeborne.selenide.Selenide.Wait;

import java.time.Duration;

import com.codeborne.selenide.Condition;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

public class BuiltInRepositoriesCardPage extends
        BuiltInRepositoriesCommonPage<BuiltInRepositoriesCardPageValidator, BuiltInRepositoriesCardPageLocators> {

    public BuiltInRepositoriesCardPage(By[] iFrameLocators,
            BuiltInRepositoriesCardPageValidator validator,
            BuiltInRepositoriesCardPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void provisionRepository(String name) {
        LOG.info(String.format("Provisioning container from repository [%s]", name));
        pageActions().click(locators().deployButtonByRepositoryName(name));
    }

    public void provisionRepositoryWithAdditionalInfo(String name) {
        LOG.info(String.format("Provisioning container from repository [%s] with additional info",
                name));
        pageActions().click(locators().cardContextMenuByName(name));
        pageActions().click(locators().cardAdditionalInfoButtonByName(name));
    }

    public void deleteRepository(String name) {
        LOG.info(String.format("Deleting repository [%s]", name));
        int retries = 3;
        do {
            pageActions().click(locators().cardContextMenuByName(name));
            try {
                Wait().withTimeout(Duration.ofSeconds(5))
                        .until(d -> element(locators().cardDeleteButtonByName(name))
                                .is(Condition.visible));
                pageActions().click(locators().cardDeleteButtonByName(name));
                return;
            } catch (TimeoutException e) {
                retries--;
            }
        } while (retries > 0);
        throw new RuntimeException(String
                .format("Cloud not delete repository [%s], the dropdown menu did not show", name));
    }

    public void refresh() {
        pageActions().click(locators().refreshButton());
        waitForSpinner();
    }

}
