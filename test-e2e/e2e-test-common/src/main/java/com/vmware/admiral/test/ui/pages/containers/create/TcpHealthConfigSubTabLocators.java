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

public class TcpHealthConfigSubTabLocators extends HealthConfigSubTabLocators {

    private final By PORT_INPUT = By
            .cssSelector("#health .container-health-port-input input");
    private final By TIMEOUT_INPUT = By
            .cssSelector("#health .container-health-timeout-input input");
    private final By HEALTHY_THRESHOLD_INPUT = By
            .cssSelector("#health .container-healthy-threshold-input input");
    private final By UNHEALTHY_THRESHOLD_INPUT = By
            .cssSelector("#health .container-unhealthy-threshold-input input");

    public By portInput() {
        return PORT_INPUT;
    }

    public By timeoutInput() {
        return TIMEOUT_INPUT;
    }

    public By healthyThresholdInput() {
        return HEALTHY_THRESHOLD_INPUT;
    }

    public By unhealthyThresholdInput() {
        return UNHEALTHY_THRESHOLD_INPUT;
    }

}
