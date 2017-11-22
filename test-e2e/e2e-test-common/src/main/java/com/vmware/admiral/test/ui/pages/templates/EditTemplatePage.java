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

package com.vmware.admiral.test.ui.pages.templates;

import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;
import com.vmware.admiral.test.ui.pages.common.PageProxy;
import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;
import com.vmware.admiral.test.ui.pages.templates.EditTemplatePage.EditTemplatePageValidator;

public class EditTemplatePage extends BasicPage<EditTemplatePage, EditTemplatePageValidator> {

    private static final By BACK_BUTTON = By.cssSelector(".fa.fa-chevron-circle-left");

    private EditTemplatePageValidator validator;
    private PageProxy parentProxy;

    public EditTemplatePage(PageProxy parentProxy) {
        this.parentProxy = parentProxy;
    }

    @Override
    public EditTemplatePageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new EditTemplatePageValidator();
        }
        return validator;
    }

    public void navigateBack() {
        LOG.info("Navigating back...");
        executeInFrame(0, () -> {
            waitForElementToStopMoving(BACK_BUTTON).click();
        });
        parentProxy.waitToLoad();
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        executeInFrame(0, () -> waitForElementToStopMoving(HomeTabSelectors.CHILD_PAGE_SLIDE));
        waitForSpinner();
    }

    @Override
    public EditTemplatePage getThis() {
        return this;
    }

    public static class EditTemplatePageValidator extends PageValidator {

        private final By PAGE_TITLE = By.cssSelector(".title.truncateText");

        @Override
        public PageValidator validateIsCurrentPage() {
            executeInFrame(0, () -> $(HomeTabSelectors.CHILD_PAGE_SLIDE).$(PAGE_TITLE)
                    .shouldHave(Condition.text("Edit Template")));
            return this;
        }

    }

}
