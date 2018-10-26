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

package com.vmware.admiral.test.ui.pages.clusters;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class AddClusterPageLocators extends PageLocators {

    private final By PAGE_TITLE = By.cssSelector("app-cluster-create .title");
    private final By NAME_INPUT = By.cssSelector("#name");
    private final By DESCRIPTION_INPUT = By.cssSelector("#description");
    private final By HOST_TYPE_SELECTOR = By
            .cssSelector(".select[data-name='create-cluster-type'] select");
    private final By URL_INPUT = By.cssSelector("#url");
    private final By SAVE_BUTTON = By.cssSelector(".saveCluster-btn");
    private final By CREDENTIALS_DROPDOWN = By
            .cssSelector("credentials-select select[formcontrolname='credentials']");
    private final String CREDENTIALS_BY_NAME_CSS = "credentials-select option[value$='/%s']";

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By nameInput() {
        return NAME_INPUT;
    }

    public By descriptionInput() {
        return DESCRIPTION_INPUT;
    }

    public By hostTypeSelect() {
        return HOST_TYPE_SELECTOR;
    }

    public By urlInput() {
        return URL_INPUT;
    }

    public By saveButton() {
        return SAVE_BUTTON;
    }

    public By credentialsSelectDropdown() {
        return CREDENTIALS_DROPDOWN;
    }

    public By credentialsByName(String credentialsName) {
        return By.cssSelector(String.format(CREDENTIALS_BY_NAME_CSS, credentialsName));
    }

}
