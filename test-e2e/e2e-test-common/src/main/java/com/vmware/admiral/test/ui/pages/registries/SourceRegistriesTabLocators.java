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

public class SourceRegistriesTabLocators extends GlobalRegistriesPageLocators {

    private final By ADD_REGISTRY_BUTTON = By.cssSelector(".registry-view .new-item");
    private final String ROW_SELECTOR_BY_ADDRESS_BASE_XPATH = "//tbody/tr/td[@title='%s']/..";
    private final String ROW_RELATIVE_DISABLE_BUTTON_XPATH = "//td[@class='table-actions']//a[@title='Disable']";
    private final String ROW_RELATIVE_ENABLE_BUTTON_XPATH = "//td[@class='table-actions']//a[@title='Enable']";
    private final String ROW_RELATIVE_EDIT_BUTTON_XPATH = "//a[contains(concat(' ', @class, ' '), ' item-edit ')]";
    private final String ROW_RELATIVE_DELETE_BUTTON_XPATH = "//a[contains(concat(' ', @class, ' '), ' item-delete ')]";

    public By addRegistryButton() {
        return ADD_REGISTRY_BUTTON;
    }

    public By registryRowByAddress(String address) {
        return By.xpath(String.format(ROW_SELECTOR_BY_ADDRESS_BASE_XPATH, address));
    }

    public By registryDisableButtonByAddress(String address) {
        return By.xpath(String.format(ROW_SELECTOR_BY_ADDRESS_BASE_XPATH, address)
                + ROW_RELATIVE_DISABLE_BUTTON_XPATH);
    }

    public By registryEnableButtonByAddress(String address) {
        return By.xpath(String.format(ROW_SELECTOR_BY_ADDRESS_BASE_XPATH, address)
                + ROW_RELATIVE_ENABLE_BUTTON_XPATH);
    }

    public By registryEditButtonByAddress(String address) {
        return By.xpath(String.format(ROW_SELECTOR_BY_ADDRESS_BASE_XPATH, address)
                + ROW_RELATIVE_EDIT_BUTTON_XPATH);
    }

    public By registryDeleteButtonByAddress(String address) {
        return By.xpath(String.format(ROW_SELECTOR_BY_ADDRESS_BASE_XPATH, address)
                + ROW_RELATIVE_DELETE_BUTTON_XPATH);
    }

}
