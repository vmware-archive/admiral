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

public class AddCertificateFormLocators extends PageLocators {

    private final By CERTIFICATE_INPUT = By.cssSelector(".certificate-input");
    private final By IMPORT_FROM_URL_TOGGLE = By
            .cssSelector(".certificate-import-option-toggle.btn");
    private final By URL_INPUT = By.cssSelector(".uri-input");
    private final By IMPORT_FROM_URL_BUTTON = By.cssSelector(".certificate-import-button");
    private final By IMPORT_FROM_URL_CANCEL_BUTTON = By
            .cssSelector(".certificate-import-option-cancel");
    private final By SAVE_BUTTON = By.cssSelector(".btn.inline-edit-save");
    private final By CANCEL_BUTTON = By.cssSelector(".btn.inline-edit-cancel");

    public By certificateInput() {
        return CERTIFICATE_INPUT;
    }

    public By importFromUrlToggleButton() {
        return IMPORT_FROM_URL_TOGGLE;
    }

    public By importFromUrlConfirmButton() {
        return IMPORT_FROM_URL_BUTTON;
    }

    public By urlInput() {
        return URL_INPUT;
    }

    public By importFromUrlCancelButton() {
        return IMPORT_FROM_URL_CANCEL_BUTTON;
    }

    public By saveButton() {
        return SAVE_BUTTON;
    }

    public By cancelButton() {
        return CANCEL_BUTTON;
    }

}
