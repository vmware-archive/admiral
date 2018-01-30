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

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class CreateContainerPageLocators extends PageLocators {

    private final By BASIC_TAB_BUTTON = By.cssSelector(".nav-link[href='#basic']");
    private final By NETWORK_TAB_BUTTON = By.cssSelector(".nav-link[href='#network']");
    private final By STORAGE_TAB_BUTTON = By.cssSelector(".nav-link[href='#storage']");
    private final By POLICY_TAB_BUTTON = By.cssSelector(".nav-link[href='#policy']");
    private final By ENVIRONMENT_TAB_BUTTON = By.cssSelector(".nav-link[href='#environment']");
    private final By HEALTH_TAB_BUTTON = By.cssSelector(".nav-link[href='#health']");
    private final By LOG_CONFIG_TAB_BUTTON = By.cssSelector(".nav-link[href='#logconfig']");
    private final By PAGE_TITLE = By
            .cssSelector(".closable-view.slide-and-fade-transition .title");
    private final By PROVISION_BUTTON = By.cssSelector(".btn.btn-primary");
    private final By BACK_BUTTON = By
            .cssSelector(".closable-view .fa.fa-chevron-circle-left");
    private final By NAME_INPUT_WRAPPER = By.cssSelector(".form-group.container-name-input");

    public By basicTabButton() {
        return BASIC_TAB_BUTTON;
    }

    public By networkTabButton() {
        return NETWORK_TAB_BUTTON;
    }

    public By storageTabButton() {
        return STORAGE_TAB_BUTTON;
    }

    public By policyTabButton() {
        return POLICY_TAB_BUTTON;
    }

    public By environmentTabButton() {
        return ENVIRONMENT_TAB_BUTTON;
    }

    public By healthConfigTabButton() {
        return HEALTH_TAB_BUTTON;
    }

    public By logConfigTabButton() {
        return LOG_CONFIG_TAB_BUTTON;
    }

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By submitButton() {
        return PROVISION_BUTTON;
    }

    public By backButton() {
        return BACK_BUTTON;
    }

    public By nameInputWrapper() {
        return NAME_INPUT_WRAPPER;
    }
}
