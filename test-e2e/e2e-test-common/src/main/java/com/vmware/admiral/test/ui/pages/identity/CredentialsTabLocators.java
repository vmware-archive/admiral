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

public class CredentialsTabLocators extends PageLocators {

    private final By ADD_CREDENTIAL_BUTTON = By.cssSelector(".btn.new-item");
    private final String CREDENTIALS_ROW_BY_NAME_XPATH = "//table//tbody//tr//td[1][@title='%s']/..";
    private final String DELETE_CREDENTIAL_BUTTON_BY_NAME_XPATH = CREDENTIALS_ROW_BY_NAME_XPATH
            + "/td[4]//a[contains(concat(' ', @class, ' '), ' item-delete ')]";
    private final String DELETE_CREDENTIALS_CONFIRMATION_BUTTON_BY_NAME_XPATH = CREDENTIALS_ROW_BY_NAME_XPATH
            + "/td[4]//div[contains(concat(' ', @class, ' '), ' delete-inline-item-confirmation-confirm ')]";
    private final String ROW_ACTIONS_BY_NAME = CREDENTIALS_ROW_BY_NAME_XPATH
            + "/td[contains(concat(' ', @class, ' '), ' table-actions ')]";

    public By addCredentialButton() {
        return ADD_CREDENTIAL_BUTTON;
    }

    public By credentialsRowByName(String credentialsName) {
        return By.xpath(String.format(ROW_ACTIONS_BY_NAME, credentialsName));
    }

    public By deleteCredentialsButtonByName(String credentialsName) {
        return By.xpath(String.format(DELETE_CREDENTIAL_BUTTON_BY_NAME_XPATH, credentialsName));
    }

    public By deleteCredentialsConfirmationButtonByName(String credentialsName) {
        return By.xpath(String.format(DELETE_CREDENTIALS_CONFIRMATION_BUTTON_BY_NAME_XPATH,
                credentialsName));
    }

}
