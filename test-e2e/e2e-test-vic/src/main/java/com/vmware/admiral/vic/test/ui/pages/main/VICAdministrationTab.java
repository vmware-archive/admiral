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

import com.vmware.admiral.test.ui.pages.main.AdministrationTab;

public class VICAdministrationTab extends AdministrationTab {

    public VICAdministrationTab(By[] iFrameLocators, VICAdministrationTabValidator validator,
            VICAdministrationTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
        this.validator = validator;
        this.locators = pageLocators;
    }

    private VICAdministrationTabValidator validator;
    private VICAdministrationTabLocators locators;

    public void clickConfigurationButton() {
        LOG.info("Navigating to Configuration page");
        pageActions().click(locators().configurationButton());
    }

    @Override
    public VICAdministrationTabValidator validate() {
        return validator;
    }

    @Override
    protected VICAdministrationTabLocators locators() {
        return locators;
    }

}
