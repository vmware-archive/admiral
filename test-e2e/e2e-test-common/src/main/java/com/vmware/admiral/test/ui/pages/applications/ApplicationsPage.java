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

import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.HomeTabAdvancedPage;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;
import com.vmware.admiral.test.ui.pages.templates.CreateTemplatePage;

public class ApplicationsPage
        extends HomeTabAdvancedPage<ApplicationsPage, ApplicationsPageValidator> {

    private final By CREATE_APPLICATION_BUTTON = By
            .cssSelector(".btn.btn-link.create-resource-btn");
    private final By REFRESH_BUTTON = By.cssSelector(".fa.fa-refresh");
    private final String APPLICATION_NAME_SELECTOR = ".grid-item .title.truncateText[title^=\"%s\"]";

    private CreateTemplatePage createTemplatePage;
    private ApplicationsPageValidator validator;

    public CreateTemplatePage createTemplate() {
        executeInFrame(0, () -> {
            $(CREATE_APPLICATION_BUTTON).click();
            waitForElementToStopMoving($(HomeTabSelectors.CHILD_PAGE_SLIDE));
        });
        if (Objects.isNull(createTemplatePage)) {
            createTemplatePage = new CreateTemplatePage();
        }
        return createTemplatePage;
    }

    @Override
    public ApplicationsPage refresh() {
        executeInFrame(0, () -> $(REFRESH_BUTTON).click());
        return getThis();
    }

    SelenideElement getApplicationCard(String name) {
        return $(By.cssSelector(String.format(APPLICATION_NAME_SELECTOR, name))).parent().parent()
                .parent();
    }

    @Override
    public ApplicationsPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new ApplicationsPageValidator(this);
        }
        return validator;
    }

    @Override
    public ApplicationsPage getThis() {
        return this;
    }

}
