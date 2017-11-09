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

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicClass;
import com.vmware.admiral.test.ui.pages.main.GlobalSelectors;

public class MembersTab extends BasicClass {

    private final By ADD_MEMBER_BUTTON = By
            .xpath(".//*[@id='membersContent']/section/app-project-members/clr-datagrid/clr-dg-action-bar/div/button");

    private AddMemberModalDialogue addMemberModalDialogue;

    public AddMemberModalDialogue addMemebers() {
        $(ADD_MEMBER_BUTTON).click();
        waitForElementToStopMoving($(GlobalSelectors.MODAL_CONTENT));
        if (Objects.isNull(addMemberModalDialogue)) {
            addMemberModalDialogue = new AddMemberModalDialogue();
        }
        return addMemberModalDialogue;
    }

}
