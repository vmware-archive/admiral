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

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class MembersTab extends BasicPage<MembersTabValidator, MembersTabLocators> {

    public MembersTab(By[] iFrameLocators, MembersTabValidator validator,
            MembersTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void clickAddMemebersButton() {
        LOG.info("Adding project members");
        pageActions().click(locators().addMembersButton());
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

}
