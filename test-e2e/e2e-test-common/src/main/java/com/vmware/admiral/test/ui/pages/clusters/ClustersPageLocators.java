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

public class ClustersPageLocators extends PageLocators {

    private final By ADD_CONTAINER_HOST_BUTTON = By.cssSelector("button.addCluster-btn");
    private final By REFRESH_BUTTON = By.cssSelector(".btn.btn-link[title=\"Refresh\"]");
    private final By PAGE_TITLE = By.cssSelector(".title>div");

    private final By ALL_HOST_CARDS = By.cssSelector(".items .card-item");
    private final String CLUSTER_CARD_SELECTOR_BY_NAME = "//span[contains(concat(' ', @class, ' '), ' card-item ')]//div[contains(concat(' ', @class, ' '), ' titleHolder ')]/div[1][text()='%s']/ancestor::span[contains(concat(' ', @class, ' '), ' card-item ')]";
    private final String CLUSTER_DELETE_BUTTON = "//button[text()='Remove']";
    private final String CLUSTER_RESCAN_BUTTON = "//button[text()='Rescan ']";
    private final String CLUSTER_MORE_ACTIONS_DROPDOWN = "//button[text()='More Actions']";
    private final String CLUSTER_DETAILS_BUTTON = "//button[text()='Open']";
    private final String CLUSTER_STATUS = "//div[contains(concat(' ', @class, ' '), ' status ')]";

    public String clusterCardByNameXpath(String name) {
        return String.format(CLUSTER_CARD_SELECTOR_BY_NAME, name);
    }

    public By clusterCardByName(String name) {
        return By.xpath(clusterCardByNameXpath(name));
    }

    public By clusterDeleteButtonByName(String name) {
        return By.xpath(clusterCardByNameXpath(name) + CLUSTER_DELETE_BUTTON);
    }

    public By clusterRescanButtonByName(String name) {
        return By.xpath(clusterCardByNameXpath(name) + CLUSTER_RESCAN_BUTTON);
    }

    public By clusterMoreactionsDropdownByName(String name) {
        return By.xpath(clusterCardByNameXpath(name) + CLUSTER_MORE_ACTIONS_DROPDOWN);
    }

    public By clusterDetailsButton(String name) {
        return By.xpath(clusterCardByNameXpath(name) + CLUSTER_DETAILS_BUTTON);
    }

    public By clusterStatusByName(String name) {
        return By.xpath(clusterCardByNameXpath(name) + CLUSTER_STATUS);
    }

    public By addClusterButton() {
        return ADD_CONTAINER_HOST_BUTTON;
    }

    public By refreshButton() {
        return REFRESH_BUTTON;
    }

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By allClusterCards() {
        return ALL_HOST_CARDS;
    }

}
