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

public class ProjectsPageLocators extends PageLocators {

    private final By PAGE_TITLE = By.cssSelector(".title > div");
    private final By ADD_PROJECT_BUTTON = By
            .cssSelector("app-projects .toolbar .row button.btn-secondary");
    private final String CARD_CONTEXT_MENU_BUTTON = "//*[contains(concat(' ', @class, ' '), ' dropdown-toggle ')]";
    private final String CARD_DETAILS_BUTTON = "//*[contains(concat(' ', @class, ' '), ' dropdown-item ')][1]";
    private final String CARD_DELETE_BUTTON = "//*[contains(concat(' ', @class, ' '), ' dropdown-item ')][2]";
    private final String PROJECT_CARD_BY_NAME_SELECTOR = "//span[contains(concat(' ', @class, ' '), ' card-item ')]//div[contains(concat(' ', @class, ' '), ' card-header ')]/div/text()[normalize-space()='%s']/ancestor::span[contains(concat(' ', @class, ' '), ' card-item ')]";

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public String projectCardByNameXpath(String name) {
        return String.format(PROJECT_CARD_BY_NAME_SELECTOR, name);
    }

    public By projectCardByName(String name) {
        return By.xpath(projectCardByNameXpath(name));
    }

    public By projectContextMenuButtonByName(String name) {
        return By.xpath(projectCardByNameXpath(name) + CARD_CONTEXT_MENU_BUTTON);
    }

    public By projectDeleteButtonByName(String name) {
        return By.xpath(projectCardByNameXpath(name) + CARD_DELETE_BUTTON);
    }

    public By projectDetailsButtonByName(String name) {
        return By.xpath(projectCardByNameXpath(name) + CARD_DETAILS_BUTTON);
    }

    public By addProjectButton() {
        return ADD_PROJECT_BUTTON;
    }

}
