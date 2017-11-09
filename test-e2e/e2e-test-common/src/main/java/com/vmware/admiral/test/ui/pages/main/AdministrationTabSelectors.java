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

public class AdministrationTabSelectors {
    public static final By IDENTITY_MANAGEMENT_BUTTON = By
            .cssSelector(".sidenav-content .nav-link[routerlink*=identity-management]");
    public static final By PROJECTS_BUTTON = By
            .cssSelector(".sidenav-content .nav-link[routerlink*=projects]");
    public static final By REGISTRIES_BUTTON = By
            .cssSelector(".sidenav-content .nav-link[routerlink*=registries]");
    public static final By LOGS_BUTTON = By
            .cssSelector(".sidenav-content .nav-link[routerlink*=logs]");
}
