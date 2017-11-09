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
import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.templates.CreateTemplatePage.CreateTemplatePageValidator;

public class CreateTemplatePage extends BasicPage<CreateTemplatePage, CreateTemplatePageValidator> {

    private final By BACK_BUTTON = By.cssSelector(".fa.fa-chevron-circle-left");
    private final By TEMPLATE_NAME_INPUT = By.id("createTemplateNameInput");
    private final By PROCEED_BUTTON = By.cssSelector(".btn.btn-primary.create-template-btn");

    private CreateTemplatePageValidator validator;
    private EditTemplatePage editTemplatePage;

    public void navigateBack() {
        executeInFrame(0, () -> $(BACK_BUTTON).click());
    }

    @Override
    public CreateTemplatePageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new CreateTemplatePageValidator();
        }
        return validator;
    }

    public CreateTemplatePage setName(String name) {
        executeInFrame(0, () -> {
            $(TEMPLATE_NAME_INPUT).clear();
            $(TEMPLATE_NAME_INPUT).sendKeys(name);
        });
        return this;
    }

    public EditTemplatePage proceed() {
        executeInFrame(0, () -> $(PROCEED_BUTTON).click());
        if (Objects.isNull(editTemplatePage)) {
            editTemplatePage = new EditTemplatePage();
        }
        return editTemplatePage;
    }

    @Override
    public CreateTemplatePage getThis() {
        return this;
    }

    public static class CreateTemplatePageValidator extends PageValidator {

        private final By PAGE_TITLE = By
                .cssSelector(".closable-view.slide-and-fade-transition .title");

        @Override
        public CreateTemplatePageValidator validateIsCurrentPage() {
            executeInFrame(0, () -> $(PAGE_TITLE).shouldHave(Condition.text("Create Template")));
            return this;
        }

    }

}
