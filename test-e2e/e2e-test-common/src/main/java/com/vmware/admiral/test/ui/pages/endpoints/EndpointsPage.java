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

package com.vmware.admiral.test.ui.pages.endpoints;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class EndpointsPage extends BasicPage<EndpointsPageValidator, EndpointsPageLocators> {

    public EndpointsPage(By[] iFrameLocators, EndpointsPageValidator validator,
            EndpointsPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        waitForSpinner();
    }

    public void clickCreateEndpointButton() {
        LOG.info("Creating new endpoint");
        pageActions().click(locators().addEndpointButton());
    }

    public void selectEndpointByName(String name) {
        LOG.info(String.format("Selecting endpoint with name: %s", name));
        pageActions().click(locators().rowCheckboxByEndpointName(name));
    }

}
