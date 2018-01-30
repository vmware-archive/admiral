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

package com.vmware.admiral.test.ui.pages.networks;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class CreateNetworkPageLocators extends PageLocators {

    private final By BACK_BUTTON = By.cssSelector(
            ".closable-view.slide-and-fade-transition .fa.fa-chevron-circle-left");
    private final By NAME_INPUT = By.cssSelector(".form-group.network-name .form-control");
    private final By ADD_HOST_BUTTON = By
            .cssSelector(".multicolumn-input-add:not([style]):not([href])");
    private final String LAST_HOST_INPUT_DIV = ".form-group:not(.ipam-config):not(.custom-properties) .multicolumn-input:last-child";
    private final String ROW_RELATIVE_DROPDOWN = " .dropdown-toggle";
    private final By SUBMIT_BUTTON = By.cssSelector(".btn-primary");
    private final By PAGE_TITLE = By
            .cssSelector(".create-network.closable-view.slide-and-fade-transition .title");

    private final String ROW_RELATIVE_HOST_SELECTOR_BY_NAME = " a[role='menuitem'][data-name$='(%s)']";

    public By backButton() {
        return BACK_BUTTON;
    }

    public By nameInput() {
        return NAME_INPUT;
    }

    public By addHostButton() {
        return ADD_HOST_BUTTON;
    }

    public By lastHostDropdown() {
        return By.cssSelector(LAST_HOST_INPUT_DIV + ROW_RELATIVE_DROPDOWN);
    }

    public By hostSelectorbyName(String name) {
        return By.cssSelector(
                LAST_HOST_INPUT_DIV + String.format(ROW_RELATIVE_HOST_SELECTOR_BY_NAME, name));
    }

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By submitButton() {
        return SUBMIT_BUTTON;
    }

}
