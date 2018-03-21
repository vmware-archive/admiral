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

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class HealthConfigSubTabLocators extends PageLocators {

    private final By IGNORE_HEALTHCHECK_ON_PROVISION_CHECKBOX = By
            .cssSelector(".health-ignore-on-provision-input .checkbox-control");
    private final By AUTOREDEPLOY_CHECKBOX = By
            .cssSelector(".container-autoredeployment-input .checkbox-control");

    public By ignoreHealthcheckOnProvisionCheckbox() {
        return IGNORE_HEALTHCHECK_ON_PROVISION_CHECKBOX;
    }

    public By autoredeployCheckbox() {
        return AUTOREDEPLOY_CHECKBOX;
    }

}
