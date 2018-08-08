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

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class CreateEndpointPageLocators extends PageLocators {

    private final By PAGE_TITLE = By.cssSelector("app-endpoint-details .title");
    private final By ENDPOINT_NAME = By.cssSelector("#endpointName");
    private final By ENDPOINT_DESCRIPTION = By.cssSelector("#endpointDescription");
    private final By UAA_ADDRESS = By.cssSelector("#uaaAddress");
    private final By CREDENTIAL_DROPDOWN_BUTTON = By.cssSelector("dropdown .dropdown-toggle");
    private final String CREDENTIALS_ROW_BY_NAME_CSS = "#generalContent .dropdown-menu a[data-name='%s']";
    private final By PKS_ADDRESS = By.cssSelector("#pksAddress");

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By endpointNameInput() {
        return ENDPOINT_NAME;
    }

    public By endpointDescriptionInput() {
        return ENDPOINT_DESCRIPTION;
    }

    public By uaaAddressInput() {
        return UAA_ADDRESS;
    }

    public By credentialsDropdownButton() {
        return CREDENTIAL_DROPDOWN_BUTTON;
    }

    public By credentialsByName(String name) {
        return By.cssSelector(String.format(CREDENTIALS_ROW_BY_NAME_CSS, name));
    }

    public By pksAddressInput() {
        return PKS_ADDRESS;
    }

}
