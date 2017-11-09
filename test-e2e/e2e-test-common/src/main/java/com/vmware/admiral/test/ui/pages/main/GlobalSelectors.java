/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages.main;

import org.openqa.selenium.By;

public class GlobalSelectors {
    public static final By LOGGED_USER_DISPLAY = By
            .cssSelector(".header-actions .dropdown .nav-text.dropdown-toggle");
    public static final By LOGOUT_BUTTON = By
            .cssSelector(".header-actions .dropdown .dropdown-item");
    public static final By ADMINISTRATION_BUTTON = By
            .cssSelector(".nav-link.nav-text[href*=administration]");
    public static final By HOME_BUTTON = By.cssSelector(".nav-link.nav-text[href*=home]");
    public static final By MODAL_CONTENT = By.cssSelector(".modal-content");
    public static final By MODAL_BACKDROP = By.cssSelector(".modal-backdrop");
    public static final By SPINNER = By.cssSelector(".spinner");
}
