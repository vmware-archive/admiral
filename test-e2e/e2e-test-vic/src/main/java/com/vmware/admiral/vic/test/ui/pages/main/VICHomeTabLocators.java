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

package com.vmware.admiral.vic.test.ui.pages.main;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.main.HomeTabLocators;

public class VICHomeTabLocators extends HomeTabLocators {

    private final By PROJECT_REPOSITORIES_BUTTON = By
            .cssSelector(" .nav-link[href='#/home/project-repositories']");

    public By projectRepositoriesButton() {
        return PROJECT_REPOSITORIES_BUTTON;
    }

}
