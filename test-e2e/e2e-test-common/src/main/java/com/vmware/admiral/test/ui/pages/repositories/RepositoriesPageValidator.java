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

package com.vmware.admiral.test.ui.pages.repositories;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;

public class RepositoriesPageValidator extends PageValidator<RepositoriesPageLocators> {

    public RepositoriesPageValidator(By[] iFrameLocators,
            RepositoriesPageLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

    @Override
    public void validateIsCurrentPage() {
        element(locators().pageTitle()).shouldHave(Condition.text("Popular Repositories"));
    }

    public void validateRepositoryExistsWithName(String name) {
        element(locators().cardByExactTitle(name)).should(Condition.exist);
    }

    public void validateRepositoryDoesNotExistWithName(String name) {
        element(locators().cardByExactTitle(name)).shouldNot(Condition.exist);
    }

    public void validateRegistryExistsWithName(String name) {
        pageActions().click(locators().selectRepositoryDropdown());
        element(locators().repositoryByName(name)).should(Condition.exist);
        pageActions().click(locators().selectRepositoryDropdown());
    }

    public void validateRegistryDoesNotExistWithName(String name) {
        pageActions().click(locators().selectRepositoryDropdown());
        element(locators().repositoryByName(name)).shouldNot(Condition.exist);
        pageActions().click(locators().selectRepositoryDropdown());
    }

}
