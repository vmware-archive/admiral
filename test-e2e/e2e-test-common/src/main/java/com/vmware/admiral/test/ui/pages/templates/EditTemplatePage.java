package com.vmware.admiral.test.ui.pages.templates;

import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;
import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.templates.EditTemplatePage.EditTemplatePageValidator;

public class EditTemplatePage extends BasicPage<EditTemplatePage, EditTemplatePageValidator> {

    private static final By BACK_BUTTON = By.cssSelector(".fa.fa-chevron-circle-left");

    private EditTemplatePageValidator validator;

    @Override
    public EditTemplatePageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new EditTemplatePageValidator();
        }
        return validator;
    }

    public void navigateBack() {
        executeInFrame(0, () -> waitForElementToStopMoving($(BACK_BUTTON)).click());
    }

    @Override
    public EditTemplatePage getThis() {
        return this;
    }

    public static class EditTemplatePageValidator extends PageValidator {

        private final By PAGE_TITLE = By.cssSelector(".title.truncateText");

        @Override
        public PageValidator validateIsCurrentPage() {
            $(PAGE_TITLE).shouldHave(Condition.text("Edit Template"));
            return this;
        }

    }

}
