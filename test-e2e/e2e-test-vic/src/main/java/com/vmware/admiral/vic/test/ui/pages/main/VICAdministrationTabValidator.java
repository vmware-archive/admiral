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

package com.vmware.admiral.vic.test.ui.pages.main;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.main.AdministrationTabValidator;

public class VICAdministrationTabValidator extends AdministrationTabValidator {

    public VICAdministrationTabValidator(By[] iFrameLocators,
            VICAdministrationTabLocators pageLocators) {
        super(iFrameLocators, pageLocators);
        this.locators = pageLocators;
    }

    private VICAdministrationTabLocators locators;

    public void validateConfigurationAvailable() {
        element(locators().configurationButton()).shouldBe(Condition.visible);
    }

    public void validateConfigurationNotAvailable() {
        element(locators().configurationButton()).shouldNotBe(Condition.visible);
    }

    public void validateAllAdministrationTabsAreAvailable() {
        validateIdentityManagementAvailable();
        validateProjectsAvailable();
        validateRegistriesAvailable();
        validateConfigurationAvailable();
        validateLogsAvailable();
    }

    @Override
    protected VICAdministrationTabLocators locators() {
        return locators;
    }
}