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

package com.vmware.admiral.test.ui.pages.volumes;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class CreateVolumePageLocators extends PageLocators {

    private final By NAME_INPUT = By.cssSelector(".form-group.volume-name .form-control");
    private final By EXISTING_NAME_INPUT = By
            .cssSelector(".form-group.volume-name-search .form-control .tt-input");
    private final By DRIVER_INPUT = By.cssSelector(".form-group.volume-driver .form-control");
    private final By SELECT_HOST_DROPDOWN_BUTTON = By.cssSelector(
            ".form-group:not(.ipam-config):not(.custom-properties):not(.network-name):not([style]) .multicolumn-input .dropdown-select.dropdown-search-menu button.dropdown-toggle");
    private final String HOST_SELECTOR_BY_NAME = ".host-picker-item-primary[title$='(%s)']";
    private final By SUBMIT_BUTTON = By.cssSelector(".btn.btn-primary");
    private final By BACK_BUTTON = By.cssSelector(
            ".closable-view.slide-and-fade-transition .fa.fa-chevron-circle-left");
    private final By PAGE_TITLE = By
            .cssSelector(".closable-view.slide-and-fade-transition .title");
    private final By ADVANCED_CHECKBOX = By
            .cssSelector(".form-group:nth-child(5) .checkbox-control");
    private final By ADD_DRIVER_OPTIONS_BUTTON = By
            .cssSelector(
                    ".driver-options .multicolumn-input:last-child .btn.fa-plus");
    private final By LAST_DRIVER_OPTION_KEY_INPUT = By.cssSelector(
            ".driver-options .multicolumn-input:last-child .inline-input.form-control[name='key']");
    private final By LAST_DRIVER_OPTION_VALUE_INPUT = By.cssSelector(
            ".driver-options .multicolumn-input:last-child .inline-input.form-control[name='value']");
    private final By EXISTING_CHECKBOX = By
            .cssSelector(".form-group:nth-child(3) .checkbox-control");

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By nameInput() {
        return NAME_INPUT;
    }

    public By driverInput() {
        return DRIVER_INPUT;
    }

    public By selectHostDropdown() {
        return SELECT_HOST_DROPDOWN_BUTTON;
    }

    public By hostSelectorByName(String name) {
        return By.cssSelector(String.format(HOST_SELECTOR_BY_NAME, name));
    }

    public By submitButton() {
        return SUBMIT_BUTTON;
    }

    public By backButton() {
        return BACK_BUTTON;
    }

    public By advancedCheckbox() {
        return ADVANCED_CHECKBOX;
    }

    public By addDriverOptionsInputButton() {
        return ADD_DRIVER_OPTIONS_BUTTON;
    }

    public By lastDriverOptionsKeyInput() {
        return LAST_DRIVER_OPTION_KEY_INPUT;
    }

    public By lastDriverOptionsValueInput() {
        return LAST_DRIVER_OPTION_VALUE_INPUT;
    }

    public By existingCheckbox() {
        return EXISTING_CHECKBOX;
    }

    public By existingNameInput() {
        return EXISTING_NAME_INPUT;
    }
}
