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

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class UsersAndGroupsTabLocators extends PageLocators {

    private By HEADER_TEXT = By
            .cssSelector("#identityUsersGroupsContent>section>app-identity-usersgroups>p");
    private final By SEARCH_FIELD = By.id("searchField");
    private final By FIRST_ROW_CHECKBOX = By
            .cssSelector(".datagrid-row .datagrid-row-flex clr-checkbox");
    private final By ASSIGN_ADMIN_ROLE_BUTTON = By
            .cssSelector(".btn.btn-sm.btn-secondary:nth-child(2)");

    public By headerText() {
        return HEADER_TEXT;
    }

    public By searchField() {
        return SEARCH_FIELD;
    }

    public By firstRowCheckbox() {
        return FIRST_ROW_CHECKBOX;
    }

    public By assignAdminRoleButton() {
        return ASSIGN_ADMIN_ROLE_BUTTON;
    }

}
