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

package com.vmware.admiral.test.ui.pages.identity;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class UsersAndGroupsTab
        extends BasicPage<UsersAndGroupsTabValidator, UsersAndGroupsTabLocators> {

    public UsersAndGroupsTab(By[] iFrameLocators, UsersAndGroupsTabValidator validator,
            UsersAndGroupsTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void assignCloudAdminRole(String userId) {
        LOG.info(String.format("Assigning cloud administrator role to: [%s]", userId));
        pageActions().clear(locators().searchField());
        pageActions().sendKeys(userId + "\n", locators().searchField());
        waitForSpinner();
        pageActions().click(locators().firstRowCheckbox());
        if (pageActions().getText(locators().assignAdminRoleButton())
                .equalsIgnoreCase("UNASSIGN ADMIN ROLE")) {
            throw new IllegalArgumentException(
                    "User " + userId + " has admin role already assigned.");
        }
        pageActions().click(locators().assignAdminRoleButton());
        element(locators().assignAdminRoleButton())
                .shouldHave(Condition.exactText("UNASSIGN ADMIN ROLE"));
    }

    public void unassignCloudAdminRole(String userId) {
        LOG.info(String.format("Unassigning cloud administrator role to: [%s]", userId));
        pageActions().clear(locators().searchField());
        pageActions().sendKeys(userId + "\n", locators().searchField());
        waitForSpinner();
        pageActions().click(locators().firstRowCheckbox());
        if (pageActions().getText(locators().assignAdminRoleButton())
                .equalsIgnoreCase("ASSIGN ADMIN ROLE")) {
            throw new IllegalArgumentException(
                    "User " + userId + " is not assigned an admin role.");
        }
        pageActions().click(locators().assignAdminRoleButton());
        element(locators().assignAdminRoleButton())
                .shouldHave(Condition.exactText("ASSIGN ADMIN ROLE"));
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

}
