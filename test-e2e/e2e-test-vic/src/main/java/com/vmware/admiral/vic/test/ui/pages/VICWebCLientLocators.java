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

package com.vmware.admiral.vic.test.ui.pages;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.CommonWebClientLocators;

public class VICWebCLientLocators extends CommonWebClientLocators {

    private final By ERROR_RESPONSE = By.id("response");
    private final By USERNAME_INPUT = By.id("username");
    private final By PASSWORD_INPUT = By.id("password");
    private final By SUBMIT_BUTTON = By.id("submit");

    @Override
    public By loginUsernameInput() {
        return USERNAME_INPUT;
    }

    @Override
    public By loginPasswordInput() {
        return PASSWORD_INPUT;
    }

    @Override
    public By loginSubmitButton() {
        return SUBMIT_BUTTON;
    }

    @Override
    public By errorResponse() {
        return ERROR_RESPONSE;
    }

}
