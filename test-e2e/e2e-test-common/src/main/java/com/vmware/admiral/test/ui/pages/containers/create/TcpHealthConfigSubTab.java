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

public class TcpHealthConfigSubTab extends HealthConfigSubTab<TcpHealthConfigSubTabLocators> {

    public TcpHealthConfigSubTab(By[] iframeLocators,
            TcpHealthConfigSubTabLocators pageLocators) {
        super(iframeLocators, pageLocators);
    }

    public void setPort(String port) {
        LOG.info(String.format("Setting port [%s]", port));
        pageActions().clear(locators().portInput());
        pageActions().sendKeys(port, locators().portInput());
    }

    public void setTimeout(String timeoutMs) {
        LOG.info(String.format("Setting timeout [%s]", timeoutMs));
        pageActions().clear(locators().timeoutInput());
        pageActions().sendKeys(timeoutMs, locators().timeoutInput());
    }

    public void setHealthyThreshold(String healthyThreshold) {
        LOG.info(String.format("Setting healthy threshold [%s]", healthyThreshold));
        pageActions().clear(locators().healthyThresholdInput());
        pageActions().sendKeys(healthyThreshold, locators().healthyThresholdInput());
    }

    public void setUnhealthyThreshold(String unhealthyThreshold) {
        LOG.info(String.format("Setting unhealthy threshold [%s]", unhealthyThreshold));
        pageActions().clear(locators().unhealthyThresholdInput());
        pageActions().sendKeys(unhealthyThreshold, locators().unhealthyThresholdInput());
    }

}
