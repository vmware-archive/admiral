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

package com.vmware.admiral.test.ui.pages.projects.configure;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.Wait;
import static com.codeborne.selenide.Selenide.actions;

import java.util.Objects;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import com.vmware.admiral.test.ui.pages.common.ModalDialogue;

public class AddMemberModalDialogue
        extends ModalDialogue<AddMemberModalDialogue, AddMemberModalDialogueValidator> {

    private final By OK_BUTTON = By.cssSelector(".modal-content .btn.btn-primary");
    private final By CANCEL_BUTTON = By.cssSelector(".modal-content .btn.btn-outline");
    private final By EMAIL_ID_FIELD = By.cssSelector(".modal-content .tt-input");
    private final By RESULTS = By.cssSelector(".modal-content .tt-menu.tt-open");
    private final By FIRST_RESULT = By.cssSelector(".modal-content .search-item");
    private final By NOT_FOUND_RESULT = By.cssSelector(".modal-content .tt-options-hint");
    private final By ROLE_DROPDOWN = By.id("memberRole");

    private AddMemberModalDialogueValidator validator;

    public static enum ProjectMemberRole {
        ADMIN, MEMBER, VIEWER;
    }

    public AddMemberModalDialogue addMember(String idOrEmail) {
        $(EMAIL_ID_FIELD).sendKeys(idOrEmail);
        $(RESULTS).should(Condition.appear);
        Wait().until(ExpectedConditions.or(
                d -> {
                    if ($(NOT_FOUND_RESULT).is(Condition.visible)) {
                        throw new IllegalArgumentException(
                                "Searching for: " + idOrEmail + " did not return any results.");
                    }
                    return false;
                },
                d -> {
                    if ($(FIRST_RESULT).is(Condition.visible)) {
                        actions().moveToElement($(FIRST_RESULT)).click().build().perform();
                        return true;
                    }
                    return false;
                }));
        return this;
    }

    public AddMemberModalDialogue setRole(ProjectMemberRole role) {
        Select select = new Select($(ROLE_DROPDOWN));
        select.selectByValue(role.toString());
        return this;
    }

    @Override
    public void cancel() {
        $(CANCEL_BUTTON).click();
    }

    @Override
    protected void confirmDialogue() {
        $(OK_BUTTON).click();
    }

    @Override
    protected AddMemberModalDialogueValidator getValidator() {
        if (Objects.isNull(validator)) {
            validator = new AddMemberModalDialogueValidator();
        }
        return validator;
    }

    @Override
    protected AddMemberModalDialogue getThis() {
        return this;
    }

}
