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

package com.vmware.admiral.vic.test.ui.pages.main;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.main.HomeTab;

public class VICHomeTab extends HomeTab {

    public VICHomeTab(By[] iFrameLocators, VICHomeTabValidator validator,
            VICHomeTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
        this.validator = validator;
        this.locators = pageLocators;
    }

    private VICHomeTabValidator validator;
    private VICHomeTabLocators locators;

    @Override
    public VICHomeTabValidator validate() {
        return validator;
    }

    public void clickContainerHostsButton() {
        LOG.info("Navigating to Container Hosts page");
        pageActions().click(locators().clustersButton());
    }

    public void clickProjectRepositoriesButton() {
        LOG.info("Navigating to Project Repositories page");
        pageActions().click(locators().projectRepositoriesButton());
    }

    @Override
    protected VICHomeTabLocators locators() {
        return locators;
    }

}
