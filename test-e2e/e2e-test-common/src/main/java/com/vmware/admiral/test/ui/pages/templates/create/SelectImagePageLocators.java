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

import com.vmware.admiral.test.ui.pages.common.CardPageLocators;

public class SelectImagePageLocators extends CardPageLocators {

    private final By PAGE_TITLE = By.cssSelector(
            ".new-container-definition-view.closable-view.slide-and-fade-transition .title");
    private final String SELECT_IMAGE_BUTTON_XPATH = "//button[contains(concat(' ', @class, ' '), ' select-image-btn ')]";

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By selectImageButtonByName(String name) {
        return By.xpath(cardByExactTitleXpath(name) + SELECT_IMAGE_BUTTON_XPATH);
    }

}
