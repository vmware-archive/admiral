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

package com.vmware.admiral.test.ui.pages.templates.create;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class EditTemplatePage
        extends BasicPage<EditTemplatePageValidator, EditTemplatePageLocators> {

    public EditTemplatePage(By[] iFrameLocators, EditTemplatePageValidator validator,
            EditTemplatePageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void navigateBack() {
        LOG.info("Navigating back...");
        waitForElementToSettle(locators().backButton());
        pageActions().click(locators().backButton());
    }

    public void connectContainerToVolume(String containerName, String volumeName) {
        LOG.info(String.format("Connecting container [%s] to volume [%s]", containerName,
                volumeName));
        pageActions().hover(locators().resourceDragAreaByName(volumeName));
        // TODO There is a glitch which requires clicking the crag area first, otherwise the drawing
        // is inadequate
        pageActions().click(locators().resourceDragAreaByName(volumeName));
        pageActions().click(locators().resourceDragAreaByName(volumeName));
        pageActions().dragAndDrop(locators().resourceDragAreaByName(volumeName),
                locators().containerFreeVolumeSlotByName(containerName));
    }

    public void connectContainerToNetwork(String containerName, String networkName) {
        LOG.info(String.format("Connecting container [%s] to network [%s]", containerName,
                networkName));
        pageActions().hover(locators().resourceDragAreaByName(networkName));
        // TODO There is a glitch which requires clicking the crag area first, otherwise the drawing
        // is inadequate
        pageActions().click(locators().resourceDragAreaByName(networkName));
        pageActions().click(locators().resourceDragAreaByName(networkName));
        pageActions().dragAndDrop(locators().resourceDragAreaByName(networkName),
                locators().containerFreeNetworkSlotByName(containerName));
    }

    public void clickAddContainerButton() {
        LOG.info("Adding a container");
        int canvasWidth = getCanvasWidth();
        int canvasHeight = getCanvasHeight();
        pageActions().hover(locators().newItemMenu());
        pageActions().click(locators().newItemMenu(), canvasWidth / 2, canvasHeight / 4);
    }

    public void clickAddVolumeButton() {
        LOG.info("Adding a volume");
        int canvasWidth = getCanvasWidth();
        int canvasHeight = getCanvasHeight();
        pageActions().hover(locators().newItemMenu());
        pageActions().click(locators().newItemMenu(), canvasWidth / 4, (canvasHeight / 4) * 3);
    }

    public void clickAddNetworkButton() {
        LOG.info("Adding a network");
        int canvasWidth = getCanvasWidth();
        int canvasHeight = getCanvasHeight();
        pageActions().hover(locators().newItemMenu());
        pageActions().click(locators().newItemMenu(), (canvasWidth / 4) * 3,
                (canvasHeight / 4) * 3);
    }

    private int getCanvasWidth() {
        return Integer
                .parseInt(pageActions().getAttribute("width", locators().newItemMenu())) / 2;
    }

    private int getCanvasHeight() {
        return Integer
                .parseInt(pageActions().getAttribute("height", locators().newItemMenu())) / 2;
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        waitForElementToSettle(locators().childPageSlide());
        waitForSpinner();
    }

}
