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

package com.vmware.admiral.test.ui.pages.repositories;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ResourcePageLocators;

public class RepositoriesPageLocators extends ResourcePageLocators {

    private final By SPINNER = By.cssSelector(".grid-container .spinner");
    private final By SELECT_REPOSITORY_DROPDOWN = By
            .cssSelector("#searchTagsSelector .dropdown-toggle");
    private final String SELECT_REPOSITORY_BY_NAME_CSS = "#searchTagsSelector .dropdown-menu .dropdown-options a[data-name='%s']";
    private final By SEARCH_INPUT = By
            .cssSelector(".images-view input[name='searchGridInput']");

    public By selectRepositoryDropdown() {
        return SELECT_REPOSITORY_DROPDOWN;
    }

    public By repositoryByName(String name) {
        return By.cssSelector(String.format(SELECT_REPOSITORY_BY_NAME_CSS, name));
    }

    public By searchInput() {
        return SEARCH_INPUT;
    }

    @Override
    public By spinner() {
        return SPINNER;
    }

}
