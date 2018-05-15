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

package com.vmware.admiral.test.ui.pages.registries;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class AddGlobalRegistryFormLocators extends PageLocators {

    private By ADDRESS_INPUT = By.cssSelector("#hostname>input");
    private By NAME_INPUT = By.cssSelector("#name>input");
    private By SAVE_BUTTON = By.cssSelector(".registryRowEdit-save");
    private By VERIFY_BUTTON = By.cssSelector(".registryRowEdit-verify");
    private By CANCEL_BUTTON = By.cssSelector(".inline-edit-cancel");

    public By addressInput() {
        return ADDRESS_INPUT;
    }

    public By nameInput() {
        return NAME_INPUT;
    }

    public By saveButton() {
        return SAVE_BUTTON;
    }

    public By verifyButton() {
        return VERIFY_BUTTON;
    }

    public By cancelButton() {
        return CANCEL_BUTTON;
    }

}
