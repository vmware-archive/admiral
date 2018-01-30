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

public class PageLocators {

    private final By MODAL_CONTENT = By.cssSelector(".modal-content");
    private final By SPINNER = By.cssSelector(".spinner");
    private final By CHILD_PAGE_SLIDE = By.cssSelector(".closable-view.slide-and-fade-transition");
    private final By LOGGED_USER_DISPLAY = By
            .cssSelector(".header-actions .dropdown .nav-text.dropdown-toggle");

    public By loggedUserDiv() {
        return LOGGED_USER_DISPLAY;
    }

    public By modalContent() {
        return MODAL_CONTENT;
    }

    public By spinner() {
        return SPINNER;
    }

    public By childPageSlide() {
        return CHILD_PAGE_SLIDE;
    }

}
