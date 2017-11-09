/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.vic.test.ui.pages.projectrepos;

import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.HomeTabPage;

public class ProjectRepositoriesPage
        extends HomeTabPage<ProjectRepositoriesPage, ProjectRepositoriesPageValidator> {

    private final By REFRESH_BUTTON = By.cssSelector(".refresh-btn");

    private ProjectRepositoriesPageValidator validator;

    @Override
    public ProjectRepositoriesPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new ProjectRepositoriesPageValidator();
        }
        return validator;
    }

    @Override
    public ProjectRepositoriesPage refresh() {
        $(REFRESH_BUTTON).click();
        return this;
    }

    @Override
    public ProjectRepositoriesPage getThis() {
        return this;
    }

}
