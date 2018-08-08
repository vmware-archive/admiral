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

public class ResourcePageLocators extends CardPageLocators {

    private final By PAGE_TITLE = By.cssSelector(".list-holder .title>span:first-child");
    private final By ITEMS_COUNT_FIELD = By.cssSelector(".title .total-items");
    private final By CREATE_RESOURCE_BUTTON = By.cssSelector(".btn.btn-link.create-resource-btn");
    private final By REFRESH_BUTTON = By.cssSelector(".refresh-button .fa.fa-refresh");
    private final By CHILD_PAGE_SLIDE = By
            .cssSelector(".closable-view.slide-and-fade-transition");
    private final By REQUESTS_BUTTON = By
            .cssSelector(".toolbar .toolbar-item:nth-child(1) .btn");

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By itemsCount() {
        return ITEMS_COUNT_FIELD;
    }

    public By createResourceButton() {
        return CREATE_RESOURCE_BUTTON;
    }

    public By refreshButton() {
        return REFRESH_BUTTON;
    }

    @Override
    public By childPageSlide() {
        return CHILD_PAGE_SLIDE;
    }

    public By requestsButton() {
        return REQUESTS_BUTTON;
    }

}
