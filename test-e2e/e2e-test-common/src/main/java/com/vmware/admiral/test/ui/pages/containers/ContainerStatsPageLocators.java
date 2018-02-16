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

package com.vmware.admiral.test.ui.pages.containers;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class ContainerStatsPageLocators extends PageLocators {

    private String CONTAINER_USAGES = ".container-details-usages";
    private By CONTAINER_STATS_SPINNER = By.cssSelector(CONTAINER_USAGES + " .spinner");
    private By LOGS_SPINNER = By.cssSelector(".container-details-logs .spinner");
    private By CPU_USAGE_PERCENTAGE = By.cssSelector(
            CONTAINER_USAGES + " .cpu-stats .radial-progress-labels .radial-progress-label");
    private By MEMORY_USAGE = By.cssSelector(
            CONTAINER_USAGES + " .cpu-stats .radial-progress-labels .radial-progress-label");
    private By PORTS_ROW = By.xpath("//tr/td[./text()='Ports']/../td[2]");
    private By ID_ROW = By.xpath("//tr/td[./text()='id']/../td[2]");
    private By BACK_BUTTON = By.cssSelector(".container-details .fa-chevron-circle-left");

    public By containerStatsSpinner() {
        return CONTAINER_STATS_SPINNER;
    }

    public By usagesDiv() {
        return By.cssSelector(CONTAINER_USAGES);
    }

    public By cpuUsagePercentage() {
        return CPU_USAGE_PERCENTAGE;
    }

    public By memoryUsage() {
        return MEMORY_USAGE;
    }

    public By portsRow() {
        return PORTS_ROW;
    }

    public By idRow() {
        return ID_ROW;
    }

    public By logsSpinner() {
        return LOGS_SPINNER;
    }

    public By backButton() {
        return BACK_BUTTON;
    }

}
