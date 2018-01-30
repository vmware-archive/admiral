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

package com.vmware.admiral.vic.test.ui.pages.projectrepos;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class ProjectRepositoriesPageLocators extends PageLocators {

    private final By REFRESH_BUTTON = By.cssSelector(".refresh-btn");
    private final By PAGE_TITLE = By.cssSelector("div.title");
    private final String ROW_BY_REPOSITORY_VALUE_XPATH = "//clr-dg-cell[./text()='%s']/ancestor::clr-dg-row[contains(concat(' ', @class, ' '), ' datagrid-row ')]";
    private final String ROW_RELATIVE_CONTEXT_MENU_XPATH = "//clr-icon[contains(concat(' ', @class, ' '), ' datagrid-action-toggle ')]";
    private final String ROW_RELATIVE_DELETE_TUTTON_XPATH = "//button[contains(concat(' ', @class, ' '), ' action-item ')]";

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By refreshButton() {
        return REFRESH_BUTTON;
    }

    public By rowByRepositoryName(String name) {
        return By.xpath(String.format(ROW_BY_REPOSITORY_VALUE_XPATH, name));
    }

    public By rowContextButtonByRepositoryName(String name) {
        return By.xpath(String.format(ROW_BY_REPOSITORY_VALUE_XPATH, name)
                + ROW_RELATIVE_CONTEXT_MENU_XPATH);
    }

    public By rowDeleteButtonByRepositoryName(String name) {
        return By.xpath(String.format(ROW_BY_REPOSITORY_VALUE_XPATH, name)
                + ROW_RELATIVE_DELETE_TUTTON_XPATH);
    }
}
