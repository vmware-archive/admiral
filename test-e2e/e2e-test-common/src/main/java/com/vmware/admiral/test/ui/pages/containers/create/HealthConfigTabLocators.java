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

public class HealthConfigTabLocators extends CreateContainerPageLocators {

    private final By NONE_RADIO_BUTTON = By.cssSelector("#health label[for='health-none']");
    private final By HTTP_RADIO_BUTTON = By.cssSelector("#health label[for='http-health']");
    private final By TCP_RADIO_BUTTON = By.cssSelector("#health label[for='tcp-health']");
    private final By COMMAND_RADIO_BUTTON = By.cssSelector("#health label[for='command-health']");

    public By radioButtonNone() {
        return NONE_RADIO_BUTTON;
    }

    public By radioButtonHttp() {
        return HTTP_RADIO_BUTTON;
    }

    public By radioButtonTcp() {
        return TCP_RADIO_BUTTON;
    }

    public By radioButtonCommand() {
        return COMMAND_RADIO_BUTTON;
    }

}
