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

package com.vmware.admiral.test.ui.pages.main;

import com.codeborne.selenide.Condition;
import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;

public class AdministrationTabValidator extends PageValidator<AdministrationTabLocators> {

    public AdministrationTabValidator(By[] iFrameLocators, AdministrationTabLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

    @Override
    public void validateIsCurrentPage() {
        element(locators().administrationTabButton()).shouldHave(Condition.cssClass("active"));
    }

    public void validateIdentityManagementNotAvailable() {
        element(locators().identityManagementButton())
                .shouldNotBe(Condition.visible);
    }

    public void validateRegistriesNotAvailable() {
        element(locators().registriesButton()).shouldNotBe(Condition.visible);
    }

    public void validateLogsNotAvailable() {
        element(locators().logsButton()).shouldNotBe(Condition.visible);
    }

    public void validateProjectsAvailable() {
        element(locators().projectsButton()).shouldBe(Condition.visible);
    }

    public void validateIdentityManagementAvailable() {
        element(locators().identityManagementButton()).shouldBe(Condition.visible);
    }

    public void validateRegistriesAvailable() {
        element(locators().registriesButton()).shouldBe(Condition.visible);
    }

    public void validateLogsAvailable() {
        element(locators().logsButton()).shouldBe(Condition.visible);
    }

}
