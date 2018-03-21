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

import com.vmware.admiral.test.ui.pages.common.BasicClass;

public class UsernameCredentialForm extends BasicClass<UsernameCredentialFormLocators> {

    public UsernameCredentialForm(By[] iframeLocators,
            UsernameCredentialFormLocators pageLocators) {
        super(iframeLocators, pageLocators);
    }

    public void setUsername(String username) {
        pageActions().clear(locators().usernameInput());
        pageActions().sendKeys(username, locators().usernameInput());
    }

    public void setPassword(String password) {
        pageActions().clear(locators().passwordInput());
        pageActions().sendKeys(password, locators().passwordInput());
    }

    public void setPrivateKey(String privateKey) {
        pageActions().clear(locators().privateKeyInput());
        pageActions().sendKeys(privateKey, locators().privateKeyInput());
    }

    public void selectUsePassword() {
        pageActions().click(locators().usePasswordButton());
    }

    public void selectUsePrivateKey() {
        pageActions().click(locators().usePrivateKeyButton());
    }

}
