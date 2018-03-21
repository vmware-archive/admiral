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

public class UsernameCredentialFormLocators extends PageLocators {

    private final By USERNAME_INPUT = By.cssSelector(".inline-edit-passwordInputs .username-input");
    private final By PASSWORD_INPUT = By.cssSelector(".inline-edit-passwordInputs .password-input");
    private final By PRIVATE_KEY_INPUT = By
            .cssSelector(".inline-edit-passwordInputs .private-key-input");
    private final By USE_PRIVATE_KEY_BUTTON = By
            .cssSelector(".inline-edit-passwordInputs .usePrivateKey");
    private final By USE_PASSWORD_BUTTON = By
            .cssSelector(".inline-edit-passwordInputs .usePassword");

    public By usernameInput() {
        return USERNAME_INPUT;
    }

    public By passwordInput() {
        return PASSWORD_INPUT;
    }

    public By privateKeyInput() {
        return PRIVATE_KEY_INPUT;
    }

    public By usePrivateKeyButton() {
        return USE_PRIVATE_KEY_BUTTON;
    }

    public By usePasswordButton() {
        return USE_PASSWORD_BUTTON;
    }

}
