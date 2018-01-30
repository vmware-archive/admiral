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

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class StorageTab extends BasicPage<StorageTabValidator, StorageTabLocators> {

    public StorageTab(By[] iFrameLocators, StorageTabValidator validator,
            StorageTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void addVolume(String volumeNameOrHostPath, String containerPath, boolean readOnly) {
        LOG.info(String.format("Adding volume [%s] with path [%s] and readonly [%b]",
                volumeNameOrHostPath, containerPath, readOnly));
        String value = pageActions().getAttribute("value", locators().lastVolumeHostPathInput());
        if (!value.trim().isEmpty()) {
            pageActions().click(locators().addVolumeInputButton());
        }
        if (Objects.nonNull(volumeNameOrHostPath) && !volumeNameOrHostPath.trim().isEmpty()) {
            pageActions().sendKeys(volumeNameOrHostPath, locators().lastVolumeHostPathInput());
        }
        if (Objects.nonNull(containerPath) && !containerPath.trim().isEmpty()) {
            pageActions().sendKeys(containerPath, locators().lastVolumeContainerPathInput());
        }
        pageActions().setCheckbox(readOnly, locators().lastVolumeReadOnlyCheckbox());
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

}
