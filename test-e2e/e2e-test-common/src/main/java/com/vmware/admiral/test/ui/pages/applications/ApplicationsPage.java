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

package com.vmware.admiral.test.ui.pages.applications;

import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.HomeTabAdvancedPage;
import com.vmware.admiral.test.ui.pages.common.PageProxy;

public class ApplicationsPage
        extends HomeTabAdvancedPage<ApplicationsPage, ApplicationsPageValidator> {

    private CreateApplicationPage createApplicationPage;
    private ApplicationsPageValidator validator;

    public CreateApplicationPage createTemplate() {
        LOG.info("Creating template");
        if (Objects.isNull(createApplicationPage)) {
            createApplicationPage = new CreateApplicationPage(new PageProxy(this));
        }
        executeInFrame(0, () -> $(CREATE_RESOURCE_BUTTON).click());
        createApplicationPage.waitToLoad();
        return createApplicationPage;
    }

    By getApplicationCardSelector(String name) {
        return By.xpath(String.format(CARD_SELECTOR_BY_NAME_PREFIX_XPATH, name));
    }

    @Override
    public ApplicationsPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new ApplicationsPageValidator(this);
        }
        return validator;
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        executeInFrame(0, () -> waitForSpinner());
    }

    @Override
    public ApplicationsPage getThis() {
        return this;
    }

}
