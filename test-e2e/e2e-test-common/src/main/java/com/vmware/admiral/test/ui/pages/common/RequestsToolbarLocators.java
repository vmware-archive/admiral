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

package com.vmware.admiral.test.ui.pages.common;

import org.openqa.selenium.By;

public class RequestsToolbarLocators extends PageLocators {

    private final By REQUESTS_BUTTON = By
            .cssSelector(".right-context-panel .toolbar-item:first-child .toolbar-item-title");
    private final String LAST_REQUEST_CSS = ".requests-list #all .request-item-holder:first-child";
    private final By LAST_REQUEST_PROGRESS = By
            .cssSelector(LAST_REQUEST_CSS + " .progress-status");
    private final By LAST_REQUEST = By
            .cssSelector(LAST_REQUEST_CSS);
    private final By REFRESH_BUTTON = By.cssSelector(".right-context-panel .fa.fa-refresh");
    private final By SPINNER = By.cssSelector(".right-context-panel .spinner");

    public By lastRequestProgress() {
        return LAST_REQUEST_PROGRESS;
    }

    public By refreshButton() {
        return REFRESH_BUTTON;
    }

    public By lastRequest() {
        return LAST_REQUEST;
    }

    public By requestsButton() {
        return REQUESTS_BUTTON;
    }

    @Override
    public By spinner() {
        return SPINNER;
    }

}
