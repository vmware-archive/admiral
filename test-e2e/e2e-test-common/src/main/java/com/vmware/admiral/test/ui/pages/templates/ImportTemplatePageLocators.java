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

package com.vmware.admiral.test.ui.pages.templates;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class ImportTemplatePageLocators extends PageLocators {

    private final By BACK_BUTTON = By.cssSelector(".fa.fa-chevron-circle-left");
    private final By SUBMIT_BUTTON = By.cssSelector(".templateImport.content .btn.btn-primary");
    private final By TEMPLATE_TEXT_INPUT = By.cssSelector(".template-input");
    private final By IMPORT_FROM_FILE_BUTTON = By.cssSelector(".template-import-option .upload");
    private final By PAGE_TITLE = By.cssSelector(".templateImport-header .title");
    private final By ALERT_MESSAGE_HOLDER = By.cssSelector(".alert.alert-danger.alert-dismissible");
    private final By CLOSE_ALERT_BUTTON = By.cssSelector(".fa.fa-close");

    public By backButton() {
        return BACK_BUTTON;
    }

    public By submitButton() {
        return SUBMIT_BUTTON;
    }

    public By templateTextInput() {
        return TEMPLATE_TEXT_INPUT;
    }

    public By importFromFileButton() {
        return IMPORT_FROM_FILE_BUTTON;
    }

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By alertMessage() {
        return ALERT_MESSAGE_HOLDER;
    }

    public By alertCloseButton() {
        return CLOSE_ALERT_BUTTON;
    }

}
