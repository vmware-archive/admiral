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

package com.vmware.admiral.test.ui.pages.containers.create;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class HealthConfigTab extends BasicPage<HealthConfigTabValidator, HealthConfigTabLocators> {

    public HealthConfigTab(By[] iFrameLocators, HealthConfigTabValidator validator,
            HealthConfigTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void selectNone() {
        LOG.info("Selecting None mode");
        pageActions().click(locators().radioButtonNone());
    }

    public void selectHttp() {
        LOG.info("Selecting HTTP mode");
        pageActions().click(locators().radioButtonHttp());
    }

    public void selectTcp() {
        LOG.info("Selecting TCP connection mode");
        pageActions().click(locators().radioButtonTcp());
    }

    public void selectCommand() {
        LOG.info("Selecting Command mode");
        pageActions().click(locators().radioButtonCommand());
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

}
