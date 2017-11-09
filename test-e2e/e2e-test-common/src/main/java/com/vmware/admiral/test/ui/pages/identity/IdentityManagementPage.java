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

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class IdentityManagementPage
        extends BasicPage<IdentityManagementPage, IdentityManagementPageValidator> {

    private final By USERS_AND_GROUPS_BUTTON = By.cssSelector("#identityUsersGroupsTab .btn");

    protected UsersAndGroupsTab usersAndGroupsTab;

    private IdentityManagementPageValidator validator;

    public UsersAndGroupsTab navigateToUsersAndGroupsTab() {
        $(USERS_AND_GROUPS_BUTTON).click();
        if (Objects.isNull(usersAndGroupsTab)) {
            usersAndGroupsTab = new UsersAndGroupsTab();
        }
        return usersAndGroupsTab;
    }

    @Override
    public IdentityManagementPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new IdentityManagementPageValidator();
        }
        return validator;
    }

    @Override
    public IdentityManagementPage getThis() {
        return this;
    }

}
