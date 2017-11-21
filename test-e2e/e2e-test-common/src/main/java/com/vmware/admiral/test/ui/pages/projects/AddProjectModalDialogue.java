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

package com.vmware.admiral.test.ui.pages.projects;

import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ModalDialogue;

public class AddProjectModalDialogue
        extends ModalDialogue<AddProjectModalDialogue, AddProjectModalDialogueValidator> {

    private final String MODAL_BASE = ".modal-content";
    private final By NAME_INPUT_FIELD = By.id("name");
    private final By DESCRIPTION_INPUT_FIELD = By.id("description");
    private final By SAVE_BUTTON = By.cssSelector(MODAL_BASE + " .btn.btn-primary");
    private final By CANCEL_BUTTON = By.cssSelector(MODAL_BASE + " .btn.btn-outline");
    private final By PUBLIC_CHECKBOX = By
            .cssSelector(
                    ".modal-content .tooltip.tooltip-validation.tooltip-sm[for*=\"isPublic\"]");

    private AddProjectModalDialogueValidator validator;

    public AddProjectModalDialogue setName(String name) {
        LOG.info(String.format("Setting name: [%s]", name));
        $(NAME_INPUT_FIELD).clear();
        $(NAME_INPUT_FIELD).sendKeys(name);
        return this;
    }

    public AddProjectModalDialogue setDescription(String description) {
        LOG.info(String.format("Setting description: [%s]", description));
        $(DESCRIPTION_INPUT_FIELD).clear();
        $(DESCRIPTION_INPUT_FIELD).sendKeys(description);
        return this;
    }

    public AddProjectModalDialogue setIsPublic(boolean isPublic) {
        LOG.info(String.format("Setting public: [%s]", isPublic));
        $(PUBLIC_CHECKBOX).setSelected(isPublic);
        return this;
    }

    @Override
    public void cancel() {
        LOG.info("Cancelling...");
        $(CANCEL_BUTTON).shouldBe(Condition.enabled).click();
    }

    @Override
    protected void confirmDialogue() {
        LOG.info("Submitting...");
        $(SAVE_BUTTON).click();
    }

    @Override
    protected AddProjectModalDialogueValidator getValidator() {
        if (Objects.isNull(validator)) {
            validator = new AddProjectModalDialogueValidator();
        }
        return validator;
    }

    @Override
    protected AddProjectModalDialogue getThis() {
        return this;
    }

}
