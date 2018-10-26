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

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ModalDialogLocators;

public class AddMemberModalDialogLocators extends ModalDialogLocators {

    private final By EMAIL_ID_FIELD = By.cssSelector(".modal-content #searchField");
    private final By ROLE_DROPDOWN = By.id("memberRole");
    private final By FIRST_USER_ID = By
            .cssSelector(".selected-members-container a.label:first-child");
    private final By SECOND_USER_ID = By
            .cssSelector(".selected-members-container a.label:nth-child(2)");

    public By idOrEmailInputField() {
        return EMAIL_ID_FIELD;
    }

    public By firstFoundUserId() {
        return FIRST_USER_ID;
    }

    public By secondFoundUserId() {
        return SECOND_USER_ID;
    }

    public By roleDropdown() {
        return ROLE_DROPDOWN;
    }

}
