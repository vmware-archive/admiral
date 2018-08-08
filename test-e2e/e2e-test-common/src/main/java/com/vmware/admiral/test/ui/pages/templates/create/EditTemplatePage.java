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

import static com.codeborne.selenide.Selenide.Wait;

import java.util.concurrent.TimeUnit;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

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
        int retriesCount = 3;
        do {
            try {
                pageActions().click(locators().backButton());
                Wait().withTimeout(5, TimeUnit.SECONDS)
                        .until(d -> element(locators().backButton()).is(Condition.hidden));
                return;
            } catch (TimeoutException e) {
                retriesCount--;
            }
        } while (retriesCount > 0);
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
        validateCanvasClickAction(() -> {
            int canvasWidth = getCanvasWidth();
            int canvasHeight = getCanvasHeight();
            pageActions().hover(locators().pageTitle());
            pageActions().hover(locators().newItemMenu());
            pageActions().click(locators().newItemMenu(), canvasWidth / 2, canvasHeight / 4);
        }, locators().selectImagePageTitle());
    }

    public void clickAddVolumeButton() {
        LOG.info("Adding a volume");
        validateCanvasClickAction(() -> {
            int canvasWidth = getCanvasWidth();
            int canvasHeight = getCanvasHeight();
            pageActions().hover(locators().pageTitle());
            pageActions().hover(locators().newItemMenu());
            pageActions().click(locators().newItemMenu(), canvasWidth / 4, (canvasHeight / 4) * 3);
        }, locators().addVolumePageTitle());
    }

    public void clickAddNetworkButton() {
        LOG.info("Adding a network");
        validateCanvasClickAction(() -> {
            int canvasWidth = getCanvasWidth();
            int canvasHeight = getCanvasHeight();
            pageActions().hover(locators().pageTitle());
            pageActions().hover(locators().newItemMenu());
            pageActions().click(locators().newItemMenu(), (canvasWidth / 4) * 3,
                    (canvasHeight / 4) * 3);
        }, locators().addNetworkPageTitle());
    }

    private void validateCanvasClickAction(Runnable clickAction, By expectedElement) {
        int retries = 3;
        do {
            clickAction.run();
            try {
                Wait().withTimeout(3, TimeUnit.SECONDS)
                        .until(d -> element(expectedElement).is(Condition.visible));
                return;
            } catch (TimeoutException e) {
                LOG.info("Clicking on the canvas failed, retrying...");
                retries--;
            }
        } while (retries > 0);
        throw new RuntimeException(
                "Could not click on the desired add component button from the canvas");
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
