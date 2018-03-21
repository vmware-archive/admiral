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

public class NewCredentialFormLocators extends PageLocators {

    private final By NAME_INPUT = By.cssSelector(".name-input");
    private final By USERNAME_RADIO_BUTTON = By
            .cssSelector(".radio-label[for='credentialTypePassword']");
    private final By CERTIFICATE_RADIO_BUTTON = By
            .cssSelector(".radio-label[for='credentialTypeCertificate']");
    private final By SAVE_BUTTON = By.cssSelector(".btn.inline-edit-save");
    private final By CANCEL_BUTTON = By.cssSelector(".btn.inline-edit-cancel");
    private final By ADD_PROPERTY_ROW_BUTTON = By
            .cssSelector(".custom-properties .multicolumn-input:last-child .fa-plus");
    private final By LAST_PROPERTY_NAME_INPUT = By
            .cssSelector(".custom-properties .multicolumn-input:last-child input[name='name']");
    private final By LAST_PROPERTY_VALUE_INPUT = By
            .cssSelector(".custom-properties .multicolumn-input:last-child input[name='value']");

    public By nameInput() {
        return NAME_INPUT;
    }

    public By usernameRadioButton() {
        return USERNAME_RADIO_BUTTON;
    }

    public By certificateRadioButton() {
        return CERTIFICATE_RADIO_BUTTON;
    }

    public By saveButton() {
        return SAVE_BUTTON;
    }

    public By cancelButton() {
        return CANCEL_BUTTON;
    }

    public By addPropertyRowButton() {
        return ADD_PROPERTY_ROW_BUTTON;
    }

    public By lastPropertyNameInput() {
        return LAST_PROPERTY_NAME_INPUT;
    }

    public By lastPropertyValueInput() {
        return LAST_PROPERTY_VALUE_INPUT;
    }

}
