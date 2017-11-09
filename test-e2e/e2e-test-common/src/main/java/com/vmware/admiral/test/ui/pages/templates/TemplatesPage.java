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
import static com.codeborne.selenide.Selenide.actions;

import java.util.Objects;

import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.HomeTabAdvancedPage;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;

public class TemplatesPage extends HomeTabAdvancedPage<TemplatesPage, TemplatesPageValidator> {

    private final By CREATE_TEMPLATE_BUTTON = By
            .cssSelector(".btn.btn-link.create-resource-btn");
    private final By IMPORT_TEMPLATE_BUTTON = By
            .cssSelector(".btn.btn-link[href*=\"import-template\"]");
    private final By REFRESH_BUTTON = By.cssSelector(".fa.fa-refresh");
    private final By CARD_RELATIVE_DELETE_BUTTON = By.cssSelector(".fa.fa-trash");
    private final By CARD_RELATIVE_DELETE_CONFIRMATION_BUTTON = By
            .cssSelector(".delete-inline-item-confirmation-confirm>div>a");
    private final String TEMPLATE_NAME_CSS_SELECTOR = ".grid-item .title.truncateText";

    private TemplatesPageValidator validator;
    private ImportTemplatePage importTemplatePage;
    private CreateTemplatePage createTemplatePage;

    @Override
    public TemplatesPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new TemplatesPageValidator();
        }
        return validator;
    }

    public ImportTemplatePage importTemplate() {
        executeInFrame(0, () -> {
            $(IMPORT_TEMPLATE_BUTTON).click();
            waitForElementToStopMoving($(HomeTabSelectors.CHILD_PAGE_SLIDE));
        });
        if (Objects.isNull(importTemplatePage)) {
            importTemplatePage = new ImportTemplatePage();
        }
        return importTemplatePage;
    }

    public CreateTemplatePage createTemplate() {
        executeInFrame(0, () -> {
            $(CREATE_TEMPLATE_BUTTON).click();
            waitForElementToStopMoving($(HomeTabSelectors.CHILD_PAGE_SLIDE));
        });
        if (Objects.isNull(createTemplatePage)) {
            createTemplatePage = new CreateTemplatePage();
        }
        return createTemplatePage;
    }

    public TemplatesPage deleteTemplate(String name) {
        executeInFrame(0, () -> {
            SelenideElement card = getTemplateCard(name);
            actions().moveToElement(card)
                    .moveToElement(card.$(CARD_RELATIVE_DELETE_BUTTON))
                    .click()
                    .moveToElement(card.$(CARD_RELATIVE_DELETE_CONFIRMATION_BUTTON))
                    .click()
                    .build().perform();
        });
        return this;
    }

    SelenideElement getTemplateCard(String name) {
        return $(By.cssSelector(TEMPLATE_NAME_CSS_SELECTOR + "[title=\"" + name + "\"]")).parent()
                .parent().parent();
    }

    @Override
    public TemplatesPage refresh() {
        executeInFrame(0, () -> $(REFRESH_BUTTON).click());
        return this;
    }

    @Override
    public TemplatesPage getThis() {
        return this;
    }

}
