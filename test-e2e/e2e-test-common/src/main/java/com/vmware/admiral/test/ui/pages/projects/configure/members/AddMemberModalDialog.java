/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages.projects.configure.members;

import static com.codeborne.selenide.Selenide.Wait;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.vmware.admiral.test.ui.pages.common.ModalDialog;

public class AddMemberModalDialog
        extends ModalDialog<AddMemberModalDialogValidator, AddMemberModalDialogLocators> {

    public AddMemberModalDialog(By[] iFrameLocators, AddMemberModalDialogValidator validator,
            AddMemberModalDialogLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public static enum ProjectMemberRole {
        ADMIN, MEMBER, VIEWER;
    }

    public void addMember(String idOrEmail) {
        LOG.info(String.format("Adding project member: [%s]", idOrEmail));
        pageActions().sendKeys(idOrEmail, locators().idOrEmailInputField());
        Wait().until(ExpectedConditions.or(
                d -> {
                    if (element(locators().notFoundIndicator()).is(Condition.visible)) {
                        throw new IllegalArgumentException(
                                "Searching for: " + idOrEmail + " did not return any results.");
                    }
                    return false;
                },
                d -> {
                    if (element(locators().firstResult()).is(Condition.visible)) {
                        pageActions().click(locators().firstResult());
                        return true;
                    }
                    return false;
                }));
    }

    public void setRole(ProjectMemberRole role) {
        LOG.info(String.format("Setting member role: [%s]", role.toString()));
        pageActions().selectOptionByValue(role.toString(), locators().roleDropdown());
    }

}
