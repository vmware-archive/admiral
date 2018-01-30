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

package com.vmware.admiral.test.ui.pages.volumes;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ResourcePage;

public class VolumesPage extends ResourcePage<VolumesPageValidator, VolumesPageLocators> {

    public VolumesPage(By[] iFrameLocators, VolumesPageValidator validator,
            VolumesPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void clickCreateVolumeButton() {
        LOG.info("Creating volume");
        pageActions().click(locators().createResourceButton());
    }

    public void deleteVolume(String namePrefix) {
        LOG.info(String.format("Deleting volume with name prefix: [%s]", namePrefix));
        deleteItemByTitlePrefix(namePrefix);
    }

    public static enum VolumeState {
        UNKNOWN, PROVISIONING, CONNECTED, RETIRED, ERROR;
    }

}
