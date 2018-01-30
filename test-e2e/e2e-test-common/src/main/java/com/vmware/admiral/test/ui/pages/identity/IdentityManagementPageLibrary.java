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

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLibrary;

public class IdentityManagementPageLibrary extends PageLibrary {

    private IdentityManagementPage identityPage;
    private UsersAndGroupsTab usersTab;

    public IdentityManagementPage identityPage() {
        if (Objects.isNull(identityPage)) {
            IdentityManagementPageLocators locators = new IdentityManagementPageLocators();
            IdentityManagementPageValidator validator = new IdentityManagementPageValidator(
                    getFrameLocators(), locators);
            identityPage = new IdentityManagementPage(getFrameLocators(), validator, locators);
        }
        return identityPage;
    }

    public UsersAndGroupsTab usersTab() {
        if (Objects.isNull(usersTab)) {
            UsersAndGroupsTabLocators locators = new UsersAndGroupsTabLocators();
            UsersAndGroupsTabValidator validator = new UsersAndGroupsTabValidator(
                    getFrameLocators(), locators);
            usersTab = new UsersAndGroupsTab(getFrameLocators(), validator, locators);
        }
        return usersTab;
    }

    @Override
    protected By[] getFrameLocators() {
        return null;
    }

}
