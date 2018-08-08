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

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLibrary;

public class EndpointsPageLibrary extends PageLibrary {

    public EndpointsPageLibrary(By[] iframeLocators) {
        super(iframeLocators);
    }

    private EndpointsPage endpointsPage;
    private CreateEndpointPage createEndpointPage;

    public EndpointsPage endpointsPage() {
        if (Objects.isNull(endpointsPage)) {
            EndpointsPageLocators locators = new EndpointsPageLocators();
            EndpointsPageValidator validator = new EndpointsPageValidator(getFrameLocators(),
                    locators);
            endpointsPage = new EndpointsPage(getFrameLocators(), validator, locators);
        }
        return endpointsPage;
    }

    public CreateEndpointPage createEndpointPage() {
        if (Objects.isNull(createEndpointPage)) {
            CreateEndpointPageLocators locators = new CreateEndpointPageLocators();
            CreateEndpointPageValidator validator = new CreateEndpointPageValidator(
                    getFrameLocators(),
                    locators);
            createEndpointPage = new CreateEndpointPage(getFrameLocators(), validator, locators);
        }
        return createEndpointPage;
    }

}
