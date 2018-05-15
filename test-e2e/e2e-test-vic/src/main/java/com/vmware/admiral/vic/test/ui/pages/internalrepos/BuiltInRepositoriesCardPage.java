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

package com.vmware.admiral.vic.test.ui.pages.internalrepos;

import org.openqa.selenium.By;

public class BuiltInRepositoriesCardPage extends
        BuiltInRepositoriesCommonPage<BuiltInRepositoriesCardPageValidator, BuiltInRepositoriesCardPageLocators> {

    public BuiltInRepositoriesCardPage(By[] iFrameLocators,
            BuiltInRepositoriesCardPageValidator validator,
            BuiltInRepositoriesCardPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void provisionRepository(String name) {
        LOG.info(String.format("Provisioning container from repository [%s]", name));
        pageActions().click(locators().deployButtonByRepositoryName(name));
    }

    public void provisionRepositoryWithAdditionalInfo(String name) {
        LOG.info(String.format("Provisioning container from repository [%s] with additional info",
                name));
        pageActions().click(locators().cardContextMenuByName(name));
        pageActions().click(locators().cardAdditionalInfoButtonByName(name));
    }

    public void deleteRepository(String name) {
        LOG.info(String.format("Deleting repository [%s]", name));
        pageActions().click(locators().cardContextMenuByName(name));
        pageActions().click(locators().cardDeleteButtonByName(name));
    }

}
