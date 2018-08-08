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

package com.vmware.admiral.test.ui.pages.endpoints;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class EndpointsPageLocators extends PageLocators {

    private final By PAGE_TITLE = By.cssSelector("app-endpoints .title>span");
    private final By SPINNER = By.cssSelector(".table-content .datagrid-spinner");
    private final By ADD_ENDPOINT_BUTTON = By.cssSelector(".toolbar .btn-secondary:first-child");
    private final By DELETE_ENDPOINT_BUTTON = By
            .cssSelector(".toolbar .btn-secondary:nth-child(2)");
    private final String TABLE_ROW_BY_ENDPOINT_NAME_XPATH = "//div[contains(concat(' ', @class, ' '), ' datagrid-body ')]//clr-dg-row[contains(concat(' ', @class, ' '), ' datagrid-row ')]//a[./text()='%s']/../../..";
    private final String ROW_RELATIVE_CHECKBOX_XPATH = "/clr-dg-cell[contains(concat(' ', @class, ' '), ' datagrid-select ')]";

    public By pageTitle() {
        return PAGE_TITLE;
    }

    @Override
    public By spinner() {
        return SPINNER;
    }

    public By addEndpointButton() {
        return ADD_ENDPOINT_BUTTON;
    }

    public By deleteEndpointButton() {
        return DELETE_ENDPOINT_BUTTON;
    }

    public By rowCheckboxByEndpointName(String name) {
        return By.xpath(String.format(TABLE_ROW_BY_ENDPOINT_NAME_XPATH, name)
                + ROW_RELATIVE_CHECKBOX_XPATH);
    }

}
