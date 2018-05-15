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

import com.vmware.admiral.test.ui.pages.projects.configure.ConfigureProjectPageLocators;

public class MembersTabLocators extends ConfigureProjectPageLocators {

    private final By ADD_MEMBERS_BUTTON = By
            .cssSelector("#membersContent .btn-group .btn:first-child");

    public By addMembersButton() {
        return ADD_MEMBERS_BUTTON;
    }

}
