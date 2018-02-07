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

public class IdentityManagementPageLocators extends PageLocators {

    private final By USERS_AND_GROUPS_BUTTON = By.cssSelector("#identityUsersGroupsTab");
    private final By CERTIFICATES_BUTTON = By.cssSelector("#identityCertificatesTab");
    private final By CREDENTIALS_BUTTON = By.cssSelector("#identityCredentialsTab");
    private final By PAGE_TITLE = By.cssSelector("div.title");

    public By usersAndGroupsTabButton() {
        return USERS_AND_GROUPS_BUTTON;
    }

    public By credentialsTabButton() {
        return CREDENTIALS_BUTTON;
    }

    public By certificatesTabButton() {
        return CERTIFICATES_BUTTON;
    }

    public By pageTitle() {
        return PAGE_TITLE;
    }

}
