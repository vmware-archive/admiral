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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class ContainerStatsPage
        extends BasicPage<ContainerStatsPageValidator, ContainerStatsPageLocators> {

    public ContainerStatsPage(By[] iFrameLocators, ContainerStatsPageValidator validator,
            ContainerStatsPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public List<String> getPortsSettings() {
        String ports = pageActions().getText(locators().portsRow());
        if (ports.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(ports.split("\n"));
    }

    public String getContainerId() {
        return pageActions().getText(locators().idRow());
    }

    public void navigateBack() {
        pageActions().click(locators().backButton());
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        element(locators().containerStatsSpinner()).should(Condition.disappear);
        element(locators().logsSpinner()).should(Condition.disappear);
        element(locators().cpuUsagePercentage()).shouldNotHave(Condition.exactText("N/A"));
        element(locators().memoryUsage()).shouldNotHave(Condition.exactText("N/A"));
    }

}
