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

package com.vmware.admiral.test.ui.pages.projects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class AddProjectPageLocators extends PageLocators {

    private final String PAGE_BASE = "app-project-create";

    private final By NAME_INPUT_FIELD = By.id("name");
    private final By PAGE_TITLE = By.cssSelector(PAGE_BASE + " .title");
    private final By DESCRIPTION_INPUT_FIELD = By.id("description");
    private final By PUBLIC_CHECKBOX = By
            .cssSelector(PAGE_BASE + " .tooltip-sm[for='isPublic']");
    private final By ALERT_TEXT = By.cssSelector(PAGE_BASE + " .alert-text");
    private final By ALERT_CLOSE_BUTTON = By
            .cssSelector(PAGE_BASE + " .alert.alert-danger .close");
    private final By CREATE_BUTTON = By.cssSelector(PAGE_BASE + " .btn-primary");

    public By nameInput() {
        return NAME_INPUT_FIELD;
    }

    public By descriptionInput() {
        return DESCRIPTION_INPUT_FIELD;
    }

    public By publicAccessCheckbox() {
        return PUBLIC_CHECKBOX;
    }

    public By alertText() {
        return ALERT_TEXT;
    }

    public By alertCloseButton() {
        return ALERT_CLOSE_BUTTON;
    }

    public By createButton() {
        return CREATE_BUTTON;
    }

    public By pageTitle() {
        return PAGE_TITLE;
    }

}
