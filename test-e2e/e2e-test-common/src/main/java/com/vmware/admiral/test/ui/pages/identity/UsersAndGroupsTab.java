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

package com.vmware.admiral.test.ui.pages.identity;

import static com.codeborne.selenide.Selenide.$;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.main.GlobalSelectors;

public class UsersAndGroupsTab extends IdentityManagementPage {

    private final By DEARCH_FIELD = By.id("searchField");
    private final By FIRST_RESULT_CHECKBOX = By
            .cssSelector(".datagrid-row .datagrid-row-flex clr-checkbox");
    private final By ASSIGN_ADMIN_ROLE_BUTTON = By
            .cssSelector(".btn.btn-sm.btn-secondary:nth-child(2)");

    public UsersAndGroupsTab assignCloudAdminRole(String userId) {
        $(DEARCH_FIELD).clear();
        $(DEARCH_FIELD).sendKeys(userId + "\n");
        waitForElementToAppearAndDisappear(GlobalSelectors.SPINNER);
        $(FIRST_RESULT_CHECKBOX).click();
        if ($(ASSIGN_ADMIN_ROLE_BUTTON).$(By.cssSelector("span")).text()
                .equalsIgnoreCase("UNASSIGN ADMIN ROLE")) {
            throw new IllegalArgumentException(
                    "User " + userId + " has admin role already assigned.");
        }
        $(ASSIGN_ADMIN_ROLE_BUTTON).click();
        return this;
    }

    public UsersAndGroupsTab unassignCloudAdminRole(String userId) {
        $(DEARCH_FIELD).clear();
        $(DEARCH_FIELD).sendKeys(userId + "\n");
        waitForElementToAppearAndDisappear(GlobalSelectors.SPINNER);
        $(FIRST_RESULT_CHECKBOX).click();
        if ($(ASSIGN_ADMIN_ROLE_BUTTON).$(By.cssSelector("span")).text()
                .equalsIgnoreCase("ASSIGN ADMIN ROLE")) {
            throw new IllegalArgumentException(
                    "User " + userId + " is not assigned an admin role.");
        }
        $(ASSIGN_ADMIN_ROLE_BUTTON).click();
        return this;
    }

}
