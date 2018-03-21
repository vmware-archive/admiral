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

package com.vmware.admiral.test.ui.pages.containers.create;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class LogConfigTab extends BasicPage<LogConfigTabValidator, LogConfigTabLocators> {

    public LogConfigTab(By[] iFrameLocators, LogConfigTabValidator validator,
            LogConfigTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void setDriver(LogDriver driver) {
        String driverStr;
        if (driver == LogDriver.json_file) {
            driverStr = "json-file";
        } else {
            driverStr = driver.toString();
        }
        LOG.info(String.format("Setting log driver [%s]", driverStr));
        pageActions().selectOptionByText(driverStr, locators().driverSelect());
    }

    public void addOption(String name, String value) {
        LOG.info(String.format("Adding an option with name [%s] and value [%s]", name, value));
        if (!pageActions().getAttribute("value", locators().lastOptionNameInput()).trim()
                .isEmpty()) {
            pageActions().click(locators().addOptionsRowButton());
        }
        pageActions().sendKeys(name, locators().lastOptionNameInput());
        pageActions().sendKeys(value, locators().lastOptionValueInput());
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

    public static enum LogDriver {
        none, json_file, syslog, journald, gelf, fluentd, awslogs, splunk, etwlogs, gcplogs
    }

}
