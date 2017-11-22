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

package com.vmware.admiral.test.ui.pages.publicrepos;

import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.HomeTabAdvancedPage;

public class PublicRepositoriesPage
        extends HomeTabAdvancedPage<PublicRepositoriesPage, PublicRepositoriesPageValidator> {

    private final By REFRESH_BUTTON = By.cssSelector(".fa.fa-refresh");

    private PublicRepositoriesPageValidator validator;

    @Override
    public PublicRepositoriesPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new PublicRepositoriesPageValidator();
        }
        return validator;
    }

    @Override
    public PublicRepositoriesPage refresh() {
        LOG.info("Refreshing...");
        executeInFrame(0, () -> $(REFRESH_BUTTON).click());
        return this;
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

    @Override
    public PublicRepositoriesPage getThis() {
        return this;
    }

}
