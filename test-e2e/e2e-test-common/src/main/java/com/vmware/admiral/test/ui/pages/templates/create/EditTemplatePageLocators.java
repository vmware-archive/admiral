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

import com.vmware.admiral.test.ui.pages.common.CardPageLocators;

public class EditTemplatePageLocators extends CardPageLocators {

    private final By BACK_BUTTON = By.cssSelector(".fa.fa-chevron-circle-left");
    private final By PAGE_TITLE = By
            .cssSelector(".closable-view.slide-and-fade-transition .title.truncateText");
    private final By NEW_ITEM_MENU = By.cssSelector(".template-new-item-menu>canvas");
    private final String RESOURCE_DRAG_AREA_BY_RESOURCE_NAME_XPATH = "//div[./@class='resource-label'][./@title='%s']/ancestor::div[contains(concat(' ', @class, ' '), ' resource ')]//div[contains(concat(' ', @class, ' '), ' resource-anchor ')]";
    private final String CONTAINER_FREE_VOLUME_SLOT = "//div[./@data-resourcetype='volume' and not(contains(concat(' ', @class, ' '), ' jsplumb-connected '))]";
    private final String CONTAINER_FREE_NETWORK_SLOT = "//div[./@data-resourcetype='network' and not(contains(concat(' ', @class, ' '), ' jsplumb-connected '))]";

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By backButton() {
        return BACK_BUTTON;
    }

    public By newItemMenu() {
        return NEW_ITEM_MENU;
    }

    public By resourceDragAreaByName(String name) {
        return By.xpath(String.format(RESOURCE_DRAG_AREA_BY_RESOURCE_NAME_XPATH, name));
    }

    public By containerFreeVolumeSlotByName(String name) {
        return By.xpath(cardByExactTitleXpath(name) + CONTAINER_FREE_VOLUME_SLOT);
    }

    public By containerFreeNetworkSlotByName(String name) {
        return By.xpath(cardByExactTitleXpath(name) + CONTAINER_FREE_NETWORK_SLOT);
    }

}
