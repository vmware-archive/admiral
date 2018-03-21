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

public class ClusterDetailsPageLocators extends PageLocators {

    private final By CHILD_PAGE_SLIDE = By.cssSelector(".full-screen.with-back-button");
    private final By SUMMARY_BUTTON = By.cssSelector("#summaryTab");
    private final By RESOURCES_BUTTON = By.cssSelector("#resourcesTab");
    private final By BACK_BUTTON = By.cssSelector(".close-button");

    @Override
    public By childPageSlide() {
        return CHILD_PAGE_SLIDE;
    }

    public By summaryButton() {
        return SUMMARY_BUTTON;
    }

    public By resourcesButton() {
        return RESOURCES_BUTTON;
    }

    public By backButton() {
        return BACK_BUTTON;
    }

}
