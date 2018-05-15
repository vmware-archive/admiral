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

package com.vmware.admiral.vic.test.ui.pages.internalrepos;

import org.openqa.selenium.By;

public class BuiltInRepositoriesCardPageLocators extends BuiltInRepositoriesCommonPageLocators {

    private final String REPOSITORY_CARD_BY_NAME_XPATH = "//span[contains(concat(' ', @class, ' '), ' card-item ')]//span[text()='%s']/ancestor::span[contains(concat(' ', @class, ' '), ' card-item ')]";
    private final String CARD_RELATIVE_DEPLOY_BUTTON_XPATH = "//button[text()='DEPLOY']";
    private final String CARD_RELATIVE_CONTEXT_MENU_XPATH = "//button[contains(concat(' ', @class, ' '), ' dropdown-toggle ')]";
    private final String CARD_RELATIVE_DELETE_BUTTON_XPATH = "//clr-dropdown-menu//button[2]";
    private final String CARD_RELATIVE_ADDITIONAL_INFO_BUTTON_XPATH = "//clr-dropdown-menu//button[1]";

    public By repositoryCardByName(String name) {
        return By.xpath(String.format(REPOSITORY_CARD_BY_NAME_XPATH, name));
    }

    public By deployButtonByRepositoryName(String name) {
        return By.xpath(String.format(REPOSITORY_CARD_BY_NAME_XPATH, name)
                + CARD_RELATIVE_DEPLOY_BUTTON_XPATH);
    }

    public By cardContextMenuByName(String name) {
        return By.xpath(String.format(REPOSITORY_CARD_BY_NAME_XPATH, name)
                + CARD_RELATIVE_CONTEXT_MENU_XPATH);
    }

    public By cardDeleteButtonByName(String name) {
        return By.xpath(String.format(REPOSITORY_CARD_BY_NAME_XPATH, name)
                + CARD_RELATIVE_DELETE_BUTTON_XPATH);
    }

    public By cardAdditionalInfoButtonByName(String name) {
        return By.xpath(String.format(REPOSITORY_CARD_BY_NAME_XPATH, name)
                + CARD_RELATIVE_ADDITIONAL_INFO_BUTTON_XPATH);
    }

}
