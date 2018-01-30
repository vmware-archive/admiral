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

import com.vmware.admiral.test.ui.pages.main.HomeTabValidator;

public class VICHomeTabValidator extends HomeTabValidator {

    public VICHomeTabValidator(By[] iFrameLocators, VICHomeTabLocators pageLocators) {
        super(iFrameLocators, pageLocators);
        this.locators = pageLocators;
    }

    private VICHomeTabLocators locators;

    public void validateProjectRepositoriesAvailable() {
        element(locators().projectRepositoriesButton()).shouldBe(Condition.visible);
    }

    public void validateProjectRepositoriesNotAvailable() {
        element(locators().projectRepositoriesButton()).shouldNotBe(Condition.visible);
    }

    public void validateAllHomeTabsAreAvailable() {
        validateApplicationsAvailable();
        validateContainersAvailable();
        validateNetworksAvailable();
        validateVolumesAvailable();
        validateTemplatesAvailable();
        validateProjectRepositoriesAvailable();
        validatePublicRepositoriesAvailable();
        validateContainerHostsAvailable();
    }

    @Override
    protected VICHomeTabLocators locators() {
        return locators;
    }

}