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

package com.vmware.admiral.test.ui.pages.templates.create;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class CreateTemplatePageLocators extends PageLocators {

    private final By PAGE_TITLE = By
            .cssSelector(".closable-view.slide-and-fade-transition .title");
    private final By BACK_BUTTON = By.cssSelector(".fa.fa-chevron-circle-left");
    private final By TEMPLATE_NAME_INPUT = By.id("createTemplateNameInput");
    private final By PROCEED_BUTTON = By.cssSelector(".btn.btn-primary.create-template-btn");

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By backButton() {
        return BACK_BUTTON;
    }

    public By nameInput() {
        return TEMPLATE_NAME_INPUT;
    }

    public By proceedButton() {
        return PROCEED_BUTTON;
    }

}
