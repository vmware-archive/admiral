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

import com.vmware.admiral.test.ui.pages.common.CreateResourcePage;
import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.templates.ImportTemplatePage.ImportTemplatePageValidator;

public class ImportTemplatePage
        extends CreateResourcePage<ImportTemplatePage, ImportTemplatePageValidator> {

    private final By BACK_BUTTON = By.cssSelector(".fa.fa-chevron-circle-left");
    private final By SUBMIT_BUTTON = By.cssSelector(".templateImport.content .btn.btn-primary");

    private ImportTemplatePageValidator validator;
    private ImportTemplateValidator importValidator;

    @Override
    public ImportTemplatePageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new ImportTemplatePageValidator();
        }
        return validator;
    }

    @Override
    public void cancel() {
        executeInFrame(0, () -> $(BACK_BUTTON).click());
    }

    @Override
    public ImportTemplateValidator submit() {
        executeInFrame(0, () -> $(SUBMIT_BUTTON).click());
        if (Objects.isNull(importValidator)) {
            importValidator = new ImportTemplateValidator();
        }
        return importValidator;
    }

    @Override
    public ImportTemplatePage getThis() {
        return this;
    }

    public static class ImportTemplatePageValidator extends PageValidator {

        private final By PAGE_TITLE = By.cssSelector(".templateImport-header .title");

        @Override
        public ImportTemplatePageValidator validateIsCurrentPage() {
            executeInFrame(0, () -> $(PAGE_TITLE).shouldHave(Condition.text("Import Template")));
            return this;
        }

    }

}
