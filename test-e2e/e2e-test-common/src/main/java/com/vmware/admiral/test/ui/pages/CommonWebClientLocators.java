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

package com.vmware.admiral.test.ui.pages;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class CommonWebClientLocators extends PageLocators {

    private final By USERNAME_INPUT = By.cssSelector(".username");
    private final By PASSWORD_INPUT = By.cssSelector(".password");
    private final By SUBMIT_BUTTON = By.cssSelector(".btn.btn-primary");
    private final By ERROR_RESPONSE = By.cssSelector(".error.active");

    public By loginUsernameInput() {
        return USERNAME_INPUT;
    }

    public By loginPasswordInput() {
        return PASSWORD_INPUT;
    }

    public By loginSubmitButton() {
        return SUBMIT_BUTTON;
    }

    public By errorResponse() {
        return ERROR_RESPONSE;
    }

}
